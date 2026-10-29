package com.tbm.careerpathlearning.service.impl;

import com.tbm.careerpathlearning.config.ClockConfig;
import com.tbm.careerpathlearning.dto.TrainingPersonDto;
import com.tbm.careerpathlearning.dto.TrainingRegistrationFormDto;
import com.tbm.careerpathlearning.dto.TrainingRegistrationRequest;
import com.tbm.careerpathlearning.dto.TrainingSessionDto;
import com.tbm.careerpathlearning.dto.TrainingSessionRequest;
import com.tbm.careerpathlearning.enums.AuthorityName;
import com.tbm.careerpathlearning.enums.NotificationType;
import com.tbm.careerpathlearning.enums.TrainingDelivery;
import com.tbm.careerpathlearning.enums.TrainingInvitationStatus;
import com.tbm.careerpathlearning.enums.TrainingRegistrationStatus;
import com.tbm.careerpathlearning.enums.TrainingSessionKind;
import com.tbm.careerpathlearning.enums.TrainingType;
import com.tbm.careerpathlearning.exception.BadRequestException;
import com.tbm.careerpathlearning.exception.ForbiddenRequestException;
import com.tbm.careerpathlearning.model.OrgChart;
import com.tbm.careerpathlearning.model.Role;
import com.tbm.careerpathlearning.model.Staff;
import com.tbm.careerpathlearning.model.TrainingInvitation;
import com.tbm.careerpathlearning.model.TrainingRegistration;
import com.tbm.careerpathlearning.service.TrainingViewer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Registering for sessions, the waitlist and its 24-hour seat offers, the HRDC form, and invitations to mandatory
 * sessions (FR-13, FR-12-04, FR-12-12 to FR-12-14, FR-15-07, decisions A to E of 28 Oct 2026).
 */
class TrainingRegistrationTest {

    private SopTestStore store;
    private TrainingServiceImpl sessions;
    private TrainingRegistrationServiceImpl registrations;
    private Staff hr, weiMing, karim, aisyah, ravi, siti, farid;
    private TrainingViewer asHr, asWeiMing, asKarim, asAisyah, asRavi, asSiti, asFarid;
    private LocalDate today;

    @BeforeEach
    void setUp() {
        store = new SopTestStore();
        sessions = store.wire(new TrainingServiceImpl());
        registrations = (TrainingRegistrationServiceImpl) store.sessionRegistrations();
        OrgChart sales = store.department(10L, "Sales Department");
        OrgChart service = store.department(11L, "Service Department");
        OrgChart people = store.department(12L, "Human Resources");
        Role salesExecutive = store.role(20L, "Sales Executive", sales);
        Role technician = store.role(21L, "Technician", service);
        Role trainer = store.role(22L, "Trainer", people);
        Role hrExecutive = store.role(23L, "HR Executive", people);
        store.permit(trainer, AuthorityName.CAN_MANAGE_TRAINING);
        store.permit(hrExecutive, AuthorityName.CAN_ASSIGN_TRAINING);
        hr = store.person("Hana Yusof", hrExecutive);
        weiMing = store.person("Tan Wei Ming", trainer);
        karim = store.person("Karim Abdullah", trainer);
        aisyah = store.person("Nur Aisyah", salesExecutive);
        ravi = store.person("Ravi Kumar", salesExecutive);
        siti = store.person("Siti Aminah", salesExecutive);
        farid = store.person("Ahmad Farid", technician);
        asHr = new TrainingViewer(hr.getId(), false, true, false);
        asWeiMing = new TrainingViewer(weiMing.getId(), true, false, false);
        asKarim = new TrainingViewer(karim.getId(), true, false, false);
        asAisyah = staff(aisyah);
        asRavi = staff(ravi);
        asSiti = staff(siti);
        asFarid = staff(farid);
        today = store.today(); // Monday 12 Oct 2026, 10:00 in Malaysia
    }

    private static TrainingViewer staff(Staff s) {
        return new TrainingViewer(s.getId(), false, false, false);
    }

    /** A Physical product training for 20, Tuesday 20 Oct 2026, 09:00 to 12:00, run by Tan Wei Ming, for everyone. */
    private Long session(Consumer<TrainingSessionRequest> change) {
        TrainingSessionRequest r = new TrainingSessionRequest();
        r.setTitle("Inverter Series 2026 Launch");
        r.setKind(TrainingSessionKind.TRAINING);
        r.setDelivery(TrainingDelivery.PHYSICAL);
        r.setTrainingType(TrainingType.PRODUCT);
        r.setDate(today.plusDays(8));
        r.setStartTime(LocalTime.of(9, 0));
        r.setEndTime(LocalTime.of(12, 0));
        r.setVenue("Conference Hall B, HQ");
        r.setCapacity(20);
        r.setTrainerId(weiMing.getId());
        change.accept(r);
        return sessions.schedule(r, asWeiMing).getId();
    }

    private TrainingSessionRequest requestOf(Long id) {
        TrainingSessionDto s = sessions.get(id, asWeiMing);
        TrainingSessionRequest r = new TrainingSessionRequest();
        r.setTitle(s.getTitle());
        r.setKind(s.getKind());
        r.setDelivery(s.getDelivery());
        r.setTrainingType(s.getTrainingType());
        r.setDate(s.getDate());
        r.setStartTime(s.getStartTime());
        r.setEndTime(s.getEndTime());
        r.setVenue(s.getVenue());
        r.setCapacity(s.getCapacity());
        r.setMandatory(s.isMandatory());
        r.setTrainerId(s.getTrainerId());
        r.setDepartmentIds(s.getDepartmentIds());
        r.setRoleIds(s.getRoleIds());
        r.setStaffIds(s.getStaffIds());
        r.setSelfRegistration(s.isSelfRegistration());
        r.setRegistrationForm(s.isRegistrationForm());
        r.setRegistrationCutoff(s.getRegistrationCutoff());
        r.setWaitlistEnabled(s.isWaitlistEnabled());
        return r;
    }

    private void register(Long id, TrainingViewer viewer) {
        registrations.register(id, new TrainingRegistrationRequest(), viewer);
    }

    private static TrainingRegistrationRequest form(String id, String phone, String branch, boolean consent) {
        TrainingRegistrationRequest r = new TrainingRegistrationRequest();
        r.setIdNumber(id);
        r.setPhone(phone);
        r.setBranch(branch);
        r.setPdpaConsent(consent);
        return r;
    }

    private TrainingRegistration registrationOf(Long id, Staff s) {
        return store.registrations.values().stream()
                .filter(r -> r.getTrainingId().equals(id) && r.getStaffId().equals(s.getId())).findFirst().orElseThrow();
    }

    private TrainingInvitation invitationOf(Long id, Staff s) {
        return store.invitations.values().stream()
                .filter(i -> i.getTrainingId().equals(id) && i.getStaffId().equals(s.getId())).findFirst().orElse(null);
    }

    private SopTestStore.Sent last() {
        return store.notifications.get(store.notifications.size() - 1);
    }

    private static void refused(String message, Executable action) {
        assertEquals(message, assertThrows(BadRequestException.class, action).getMessage());
    }

    private void at(LocalDate date, int hour, int minute) {
        store.clock.set(LocalDateTime.of(date, LocalTime.of(hour, minute)).atZone(ClockConfig.ZONE).toInstant());
    }

    // --- registering ----------------------------------------------------------------------

    @Test
    @DisplayName("Staff register in one step while seats remain, and are told the details (FR-13-01, FR-13-04)")
    void register() {
        Long id = session(r -> { });

        register(id, asAisyah);

        TrainingSessionDto dto = sessions.get(id, asAisyah);
        assertEquals(TrainingRegistrationStatus.REGISTERED, dto.getMyStatus());
        assertEquals(1, dto.getBooked());
        assertEquals(19, dto.getSeatsLeft());
        assertFalse(dto.isCanRegister());
        assertEquals(NotificationType.SESSION_REGISTERED, last().type());
        assertEquals(aisyah.getId(), last().recipient());
        assertEquals("You're registered for \"Inverter Series 2026 Launch\" on Tue 20 Oct 2026, 09:00 to 12:00, "
                + "at Conference Hall B, HQ.", last().body());
        refused("You're registered for this session.", () -> register(id, asAisyah));
    }

    @Test
    @DisplayName("A full session puts staff on the waitlist in order; with the waitlist off it is full (FR-13-02, FR-12-13)")
    void waitlist() {
        Long id = session(r -> r.setCapacity(1));
        Long closed = session(r -> {
            r.setCapacity(1);
            r.setWaitlistEnabled(false);
            r.setStartTime(LocalTime.of(14, 0));
            r.setEndTime(LocalTime.of(16, 0));
        });
        register(id, asAisyah);
        assertTrue(sessions.get(id, asRavi).isWouldWaitlist());

        register(id, asRavi);
        register(id, asSiti);

        assertEquals(TrainingRegistrationStatus.WAITLISTED, sessions.get(id, asRavi).getMyStatus());
        assertEquals(1, sessions.get(id, asRavi).getMyWaitlistPosition());
        assertEquals(2, sessions.get(id, asSiti).getMyWaitlistPosition());
        assertEquals("\"Inverter Series 2026 Launch\" on Tue 20 Oct 2026, 09:00 to 12:00 is full, so you're number 2 on "
                + "the waitlist. If a seat frees up, it is offered to you in turn.", last().body());
        register(closed, asAisyah);
        assertEquals("This session is full.", sessions.get(closed, asRavi).getRegisterBlocker());
        refused("This session is full.", () -> register(closed, asRavi));
    }

    @Test
    @DisplayName("Registration closes after the cut-off, and is for the trainer alone when self-registration is off (FR-12-12, FR-12-04)")
    void closedRegistration() {
        Long cutoff = session(r -> r.setRegistrationCutoff(today.plusDays(7)));
        Long byTrainer = session(r -> {
            r.setSelfRegistration(false);
            r.setStartTime(LocalTime.of(14, 0));
            r.setEndTime(LocalTime.of(16, 0));
        });

        refused("The trainer or HR registers people for this session.", () -> register(byTrainer, asAisyah));
        at(today.plusDays(8), 8, 0);
        refused("Registration closed at the end of Mon 19 Oct 2026.", () -> register(cutoff, asAisyah));
        assertFalse(sessions.get(cutoff, asAisyah).isCanRegister());
        at(today.plusDays(8), 14, 0);
        refused("This session has started, so registration is closed.", () -> register(byTrainer, asAisyah));
    }

    @Test
    @DisplayName("Only the audience registers, and a presenter can't register for their own session (FR-12-03, FR-15-07)")
    void whoMayRegister() {
        Long technicians = session(r -> r.setRoleIds(List.of(21L)));
        Long sharing = session(r -> {
            r.setKind(TrainingSessionKind.STAFF_SHARING);
            r.setTrainerId(karim.getId());
            r.setStartTime(LocalTime.of(14, 0));
            r.setEndTime(LocalTime.of(15, 0));
        });

        refused("This session isn't open to you.", () -> register(technicians, asAisyah));
        assertThrows(ForbiddenRequestException.class, () -> sessions.get(technicians, asAisyah));
        register(technicians, asFarid);
        refused("You present this session, so you can't register for it.", () -> register(sharing, asKarim));
    }

    @Test
    @DisplayName("A registration that clashes in time with another one is refused, naming it (decision B)")
    void clash() {
        Long morning = session(r -> { });
        Long overlapping = session(r -> {
            r.setTitle("Customer Service Excellence");
            r.setStartTime(LocalTime.of(11, 0));
            r.setEndTime(LocalTime.of(13, 0));
        });
        Long afternoon = session(r -> {
            r.setStartTime(LocalTime.of(13, 0));
            r.setEndTime(LocalTime.of(14, 0));
        });
        register(morning, asAisyah);

        String clash = "It clashes with \"Inverter Series 2026 Launch\", 09:00 to 12:00, which you're registered for.";
        assertEquals(clash, sessions.get(overlapping, asAisyah).getRegisterBlocker());
        refused(clash, () -> register(overlapping, asAisyah));
        register(afternoon, asAisyah);
    }

    // --- the HRDC form --------------------------------------------------------------------

    @Test
    @DisplayName("The HRDC form is filled from the staff record and asks for IC, phone, branch and PDPA consent (FR-13-03)")
    void hrdcForm() {
        Long id = session(r -> r.setRegistrationForm(true));

        TrainingRegistrationFormDto filled = registrations.form(id, asAisyah);
        assertEquals("Nur Aisyah", filled.getName());
        assertEquals("nur.aisyah@tbm.net", filled.getEmail());
        assertEquals("Sales Department", filled.getDepartmentName());
        assertEquals("Sales Executive", filled.getRoleName());
        assertEquals(List.of("Jalan Klang Lama (HQ)", "Bangsar Village", "Taman Connaught Cheras", "Great Eastern Mall",
                "Setapak Central"), filled.getBranches().get("Kuala Lumpur"));
        refused("Enter your IC number (12 digits) or passport number.", () -> register(id, asAisyah));
        refused("Enter a phone number, for example 012-345 6789.",
                () -> registrations.register(id, form("900101-14-5678", "12", "Bangsar Village", true), asAisyah));
        refused("Choose your branch.",
                () -> registrations.register(id, form("900101-14-5678", "012-345 6789", "Penang", true), asAisyah));
        refused("Tick the box to agree to the PDPA notice before registering.",
                () -> registrations.register(id, form("900101-14-5678", "012-345 6789", "Bangsar Village", false), asAisyah));

        registrations.register(id, form("900101-14-5678", "012-345 6789", "Bangsar Village", true), asAisyah);

        TrainingRegistration saved = registrationOf(id, aisyah);
        assertEquals("900101145678", saved.getIdNumber());
        assertNotNull(saved.getPdpaConsentAt());
        assertFalse(last().body().contains("5678"), "HRDC details never go into a notification");
    }

    @Test
    @DisplayName("The trainer and HR see the HRDC details, IC in full; the Super Admin sees the list without them")
    void peopleList() {
        Long id = session(r -> r.setRegistrationForm(true));
        registrations.register(id, form("900101-14-5678", "012-345 6789", "Bangsar Village", true), asAisyah);
        TrainingViewer asSuperAdmin = new TrainingViewer(store.person("Super Admin", null).getId(), false, false, true);

        TrainingPersonDto row = registrations.people(id, asWeiMing).get(0);
        assertEquals("REGISTERED", row.getStatus());
        assertEquals("900101145678", row.getIdNumber());
        assertEquals("Bangsar Village", row.getBranch());
        assertEquals("900101145678", registrations.people(id, asHr).get(0).getIdNumber());
        assertNull(registrations.people(id, asSuperAdmin).get(0).getIdNumber());
        assertThrows(ForbiddenRequestException.class, () -> registrations.people(id, asKarim));
        assertThrows(ForbiddenRequestException.class, () -> registrations.people(id, asRavi));
    }

    // --- cancelling and seat offers -----------------------------------------------------------

    @Test
    @DisplayName("Cancelling erases the HRDC details and offers the seat to the first on the waitlist for 24 hours (FR-13-06, FR-13-07)")
    void cancelOffersSeat() {
        Long id = session(r -> {
            r.setCapacity(1);
            r.setRegistrationForm(true);
        });
        registrations.register(id, form("900101-14-5678", "012-345 6789", "Bangsar Village", true), asAisyah);
        registrations.register(id, form("A1234567", "+60 12 345 6789", "IOI Mall Damansara", true), asRavi);
        registrations.register(id, form("910202-10-1111", "019-876 5432", "Setapak Central", true), asSiti);

        registrations.cancelMine(id, asAisyah);

        TrainingRegistration mine = registrationOf(id, aisyah);
        assertEquals(TrainingRegistrationStatus.CANCELLED, mine.getStatus());
        assertEquals("Cancelled by the staff member", mine.getCancelReason());
        assertNull(mine.getIdNumber(), "erased with the registration (decision C)");
        TrainingRegistration offered = registrationOf(id, ravi);
        assertEquals(TrainingRegistrationStatus.OFFERED, offered.getStatus());
        assertEquals(LocalDateTime.of(today.plusDays(1), LocalTime.of(10, 0)), offered.getOfferExpiresAt());
        assertEquals(TrainingRegistrationStatus.WAITLISTED, registrationOf(id, siti).getStatus());
        assertEquals(NotificationType.SESSION_SEAT_OFFERED, last().type());
        assertEquals(ravi.getId(), last().recipient());
        assertEquals("A seat on \"Inverter Series 2026 Launch\" on Tue 20 Oct 2026, 09:00 to 12:00 is free for you. "
                + "Accept it in the Training Calendar by Tue 13 Oct, 10:00, or it passes to the next person.", last().body());
        assertEquals(1, sessions.get(id, asSiti).getBooked(), "the offered seat is held");
    }

    @Test
    @DisplayName("Accepting an offer gives the seat; declining it passes it to the next person")
    void acceptOrDecline() {
        Long id = session(r -> r.setCapacity(1));
        register(id, asAisyah);
        register(id, asRavi);
        register(id, asSiti);
        registrations.cancelMine(id, asAisyah);

        registrations.declineOffer(id, asRavi);
        assertEquals("Declined the offered seat", registrationOf(id, ravi).getCancelReason());
        assertEquals(TrainingRegistrationStatus.OFFERED, registrationOf(id, siti).getStatus());

        registrations.acceptOffer(id, asSiti);
        assertEquals(TrainingRegistrationStatus.REGISTERED, sessions.get(id, asSiti).getMyStatus());
        assertEquals("You took up the seat on \"Inverter Series 2026 Launch\" on Tue 20 Oct 2026, 09:00 to 12:00, "
                + "at Conference Hall B, HQ.", last().body());
        refused("You have no seat offer for this session.", () -> registrations.acceptOffer(id, asRavi));
    }

    @Test
    @DisplayName("An offer not taken up within 24 hours ends: the person leaves the waitlist and the seat goes on (decision A)")
    void offerRunsOut() {
        Long id = session(r -> r.setCapacity(1));
        register(id, asAisyah);
        register(id, asRavi);
        register(id, asSiti);
        registrations.cancelMine(id, asAisyah);
        at(today.plusDays(1), 10, 0);

        refused("This offer ran out at Tue 13 Oct, 10:00.", () -> registrations.acceptOffer(id, asRavi));
        assertEquals(1, registrations.expireOffers());
        assertEquals(0, registrations.expireOffers(), "safe to run again");

        TrainingRegistration expired = registrationOf(id, ravi);
        assertEquals(TrainingRegistrationStatus.CANCELLED, expired.getStatus());
        assertEquals("Did not take up the offered seat in time", expired.getCancelReason());
        assertTrue(store.notifications.stream().anyMatch(n -> n.type() == NotificationType.SESSION_OFFER_EXPIRED
                && n.recipient().equals(ravi.getId())));
        assertEquals(TrainingRegistrationStatus.OFFERED, registrationOf(id, siti).getStatus());
        assertEquals(LocalDateTime.of(today.plusDays(2), LocalTime.of(10, 0)), registrationOf(id, siti).getOfferExpiresAt());
    }

    @Test
    @DisplayName("Within 24 hours of the start nobody is offered a seat automatically, nobody jumps the queue, and the trainer offers it")
    void lastDayTrainerFills() {
        Long id = session(r -> r.setCapacity(1));
        register(id, asAisyah);
        register(id, asRavi);
        at(today.plusDays(7), 12, 0); // 21 hours before the start

        registrations.cancelMine(id, asAisyah);
        assertEquals(TrainingRegistrationStatus.WAITLISTED, registrationOf(id, ravi).getStatus());
        register(id, asSiti);
        assertEquals(TrainingRegistrationStatus.WAITLISTED, registrationOf(id, siti).getStatus(), "behind Ravi, though a seat is free");

        registrations.offerSeat(id, registrationOf(id, ravi).getRegistrationId(), asWeiMing);

        assertEquals(TrainingRegistrationStatus.OFFERED, registrationOf(id, ravi).getStatus());
        assertEquals(LocalDateTime.of(today.plusDays(8), LocalTime.of(9, 0)), registrationOf(id, ravi).getOfferExpiresAt(),
                "held until the start at the latest");
        refused("No seat is free. Add seats, or wait for someone to cancel.",
                () -> registrations.offerSeat(id, registrationOf(id, siti).getRegistrationId(), asWeiMing));
    }

    @Test
    @DisplayName("Seats added by an edit go to the waitlist first")
    void moreSeats() {
        Long id = session(r -> r.setCapacity(1));
        register(id, asAisyah);
        register(id, asRavi);
        register(id, asSiti);
        TrainingSessionRequest bigger = requestOf(id);
        bigger.setCapacity(3);

        sessions.update(id, bigger, asWeiMing);

        assertEquals(TrainingRegistrationStatus.OFFERED, registrationOf(id, ravi).getStatus());
        assertEquals(TrainingRegistrationStatus.OFFERED, registrationOf(id, siti).getStatus());
    }

    // --- the trainer and HR ----------------------------------------------------------------

    @Test
    @DisplayName("The trainer or HR adds and removes people; removing offers the freed seat on (FR-12-04)")
    void trainerAddsAndRemoves() {
        Long id = session(r -> {
            r.setSelfRegistration(false);
            r.setCapacity(1);
        });

        registrations.add(id, aisyah.getId(), asHr);
        assertEquals(hr.getId(), registrationOf(id, aisyah).getAddedBy());
        assertEquals("Hana Yusof registered you for \"Inverter Series 2026 Launch\" on Tue 20 Oct 2026, 09:00 to 12:00, "
                + "at Conference Hall B, HQ.", last().body());
        refused("Nur Aisyah is already on this session.", () -> registrations.add(id, aisyah.getId(), asWeiMing));
        refused("Tan Wei Ming runs this session, so they can't be registered for it.",
                () -> registrations.add(id, weiMing.getId(), asWeiMing));
        assertThrows(ForbiddenRequestException.class, () -> registrations.add(id, ravi.getId(), asKarim));
        registrations.add(id, ravi.getId(), asWeiMing);
        assertEquals(TrainingRegistrationStatus.WAITLISTED, registrationOf(id, ravi).getStatus(), "full, so the waitlist");

        registrations.remove(id, registrationOf(id, aisyah).getRegistrationId(), asWeiMing);

        assertEquals("Removed by Tan Wei Ming", registrationOf(id, aisyah).getCancelReason());
        assertTrue(store.notifications.stream().anyMatch(n -> n.type() == NotificationType.SESSION_REMOVED
                && n.body().equals("Tan Wei Ming took you off \"Inverter Series 2026 Launch\" on Tue 20 Oct 2026, 09:00 to 12:00.")));
        assertEquals(TrainingRegistrationStatus.OFFERED, registrationOf(id, ravi).getStatus());
    }

    // --- invitations to mandatory sessions -------------------------------------------------------

    @Test
    @DisplayName("A mandatory session invites its audience, and people added to it later; accepting registers (FR-12-14, decision E)")
    void invitations() {
        Long id = session(r -> {
            r.setMandatory(true);
            r.setDepartmentIds(List.of(10L));
        });
        assertEquals(TrainingInvitationStatus.PENDING, invitationOf(id, aisyah).getStatus());
        assertNotNull(invitationOf(id, ravi));
        assertNotNull(invitationOf(id, siti));
        assertNull(invitationOf(id, farid), "not in the Sales Department");
        assertNull(invitationOf(id, weiMing), "whoever runs it isn't invited");
        SopTestStore.Sent invited = store.notifications.stream().filter(n -> n.recipient().equals(aisyah.getId())).findFirst().orElseThrow();
        assertEquals(NotificationType.SESSION_INVITATION, invited.type());
        assertEquals("You're invited to the mandatory session \"Inverter Series 2026 Launch\" on Tue 20 Oct 2026, "
                + "09:00 to 12:00, at Conference Hall B, HQ. Register before it starts.", invited.body());

        register(id, asAisyah);
        assertEquals(TrainingInvitationStatus.ACCEPTED, invitationOf(id, aisyah).getStatus());
        refused("Say why you can't attend. Your trainer sees the reason.", () -> registrations.declineInvitation(id, " ", asRavi));
        registrations.declineInvitation(id, "On leave that week", asRavi);
        assertEquals(TrainingInvitationStatus.DECLINED, invitationOf(id, ravi).getStatus());

        TrainingSessionRequest wider = requestOf(id);
        wider.setRoleIds(List.of(21L));
        int before = store.invitations.size();
        sessions.update(id, wider, asWeiMing);
        assertEquals(before + 1, store.invitations.size(), "only Ahmad Farid is new to the audience");
        assertNotNull(invitationOf(id, farid));

        assertEquals(List.of("REGISTERED:Nur Aisyah", "INVITED:Ahmad Farid", "INVITED:Siti Aminah", "DECLINED:Ravi Kumar"),
                registrations.people(id, asWeiMing).stream().map(p -> p.getStatus() + ":" + p.getName()).toList());
        assertEquals("On leave that week", registrations.people(id, asWeiMing).get(3).getNote());
    }

    @Test
    @DisplayName("Someone invited can register even when staff can't register themselves")
    void invitedWhenSelfRegistrationIsOff() {
        Long id = session(r -> {
            r.setMandatory(true);
            r.setSelfRegistration(false);
            r.setStaffIds(List.of(aisyah.getId()));
        });

        assertTrue(sessions.get(id, asAisyah).isCanRegister());
        register(id, asAisyah);
        assertEquals(TrainingRegistrationStatus.REGISTERED, registrationOf(id, aisyah).getStatus());
    }

    // --- My Learning ---------------------------------------------------------------------------

    @Test
    @DisplayName("My Learning lists the sessions the person is on or invited to, soonest first (FR-13-05)")
    void mine() {
        Long later = session(r -> r.setDate(today.plusDays(20)));
        Long soon = session(r -> {
            r.setTitle("R32 Safety Refresher");
            r.setDate(today.plusDays(3));
        });
        Long invited = session(r -> {
            r.setMandatory(true);
            r.setStaffIds(List.of(aisyah.getId()));
            r.setDate(today.plusDays(5));
        });
        Long cancelled = session(r -> r.setDate(today.plusDays(6)));
        register(later, asAisyah);
        register(soon, asAisyah);
        register(cancelled, asAisyah);
        registrations.cancelMine(cancelled, asAisyah);

        assertEquals(List.of(soon, invited, later), sessions.mine(asAisyah).stream().map(TrainingSessionDto::getId).toList());
        assertEquals(TrainingInvitationStatus.PENDING, sessions.mine(asAisyah).get(1).getMyInvitation());
    }
}
