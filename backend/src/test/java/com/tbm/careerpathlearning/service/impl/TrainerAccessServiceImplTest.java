package com.tbm.careerpathlearning.service.impl;

import com.tbm.careerpathlearning.dto.TrainerAccessRequest;
import com.tbm.careerpathlearning.dto.TrainerAccessStaffDto;
import com.tbm.careerpathlearning.dto.TrainingAuditEntryDto;
import com.tbm.careerpathlearning.enums.AuthorityName;
import com.tbm.careerpathlearning.enums.NotificationType;
import com.tbm.careerpathlearning.enums.StaffAccountStatus;
import com.tbm.careerpathlearning.exception.BadRequestException;
import com.tbm.careerpathlearning.model.OrgChart;
import com.tbm.careerpathlearning.model.Role;
import com.tbm.careerpathlearning.model.Staff;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class TrainerAccessServiceImplTest {

    private SopTestStore store;
    private TrainerAccessServiceImpl service;
    private Staff admin, weiMing, aisyah, ravi;
    private LocalDate today;

    @BeforeEach
    void setUp() {
        store = new SopTestStore();
        service = store.wire(new TrainerAccessServiceImpl());
        today = store.today();
        OrgChart hr = store.department(12L, "HR Department");
        OrgChart sales = store.department(10L, "Sales Department");
        Role superAdminRole = store.role(1L, "superadmin", hr);
        Role trainerRole = store.role(22L, "Trainer", hr);
        Role salesExecutive = store.role(20L, "Sales Executive", sales);
        store.permit(superAdminRole, AuthorityName.CAN_MANAGE_ACCESS_CONTROL);
        store.permit(trainerRole, AuthorityName.CAN_MANAGE_TRAINING);
        admin = store.person("Super Admin", superAdminRole);
        weiMing = store.person("Tan Wei Ming", trainerRole);
        aisyah = store.person("Nur Aisyah", salesExecutive);
        ravi = store.person("Ravi Kumar", salesExecutive);
    }

    @Test
    @DisplayName("A grant gives Trainer access straight away and records who gave it, why, and until when")
    void grant() {
        TrainerAccessStaffDto dto = service.grant(aisyah.getId(), request(today.plusDays(30), "Presents the product sharing session"),
                admin.getId());

        assertTrue(service.hasActiveGrant(aisyah.getId()));
        assertTrue(dto.isViaGrant());
        assertFalse(dto.isViaRole());
        assertEquals(today.plusDays(30), dto.getGrantEndDate());
        assertEquals("Super Admin", dto.getGrantedByName());
        List<TrainingAuditEntryDto> history = service.history(aisyah.getId());
        assertEquals(1, history.size());
        assertEquals("TRAINER_ACCESS_GRANTED", history.get(0).getAction());
        assertEquals("Super Admin", history.get(0).getActorName());
        assertEquals("Presents the product sharing session", history.get(0).getReason());
        assertEquals("Last day: 11 Nov 2026", history.get(0).getDetail());
    }

    @Test
    @DisplayName("The list shows everyone, sorted by name, with access through their role kept apart from grants")
    void list() {
        service.grant(aisyah.getId(), request(null, "Sharing session"), admin.getId());

        List<TrainerAccessStaffDto> list = service.list();

        assertEquals(List.of("Nur Aisyah", "Ravi Kumar", "Super Admin", "Tan Wei Ming"),
                list.stream().map(TrainerAccessStaffDto::getName).toList());
        TrainerAccessStaffDto granted = list.get(0);
        assertTrue(granted.isViaGrant());
        assertNull(granted.getGrantEndDate(), "no end date");
        assertEquals("Sales Department", granted.getDepartment());
        TrainerAccessStaffDto none = list.get(1);
        assertFalse(none.isViaGrant() || none.isViaRole());
        TrainerAccessStaffDto throughRole = list.get(3);
        assertTrue(throughRole.isViaRole());
        assertFalse(throughRole.isViaGrant());
    }

    @Test
    @DisplayName("Access works through the whole last day in Malaysia and stops at midnight")
    void endDateIsTheLastDay() {
        service.grant(aisyah.getId(), request(today, "One-day session"), admin.getId());

        store.clock.set(Instant.parse("2026-10-12T15:59:59Z")); // 23:59:59 on 12 Oct in Malaysia
        assertTrue(service.hasActiveGrant(aisyah.getId()));
        store.clock.set(Instant.parse("2026-10-12T16:00:00Z")); // midnight
        assertFalse(service.hasActiveGrant(aisyah.getId()));
    }

    @Test
    @DisplayName("Granting again changes the end date of the grant in force, and a repeat with nothing to change is refused")
    void grantAgainChangesTheEndDate() {
        service.grant(aisyah.getId(), request(null, "Sharing session"), admin.getId());
        service.grant(aisyah.getId(), request(LocalDate.of(2026, 12, 31), "Only needed this year"), admin.getId());

        assertEquals(1, store.grants.size());
        assertEquals(LocalDate.of(2026, 12, 31), store.grants.get(aisyah.getId()).getEndDate());
        TrainingAuditEntryDto change = service.history(aisyah.getId()).get(0);
        assertEquals("TRAINER_ACCESS_END_DATE_CHANGED", change.getAction());
        assertEquals("Last day changed from no end date to 31 Dec 2026", change.getDetail());
        assertEquals("Only needed this year", change.getReason());
        BadRequestException same = assertThrows(BadRequestException.class,
                () -> service.grant(aisyah.getId(), request(LocalDate.of(2026, 12, 31), "Again"), admin.getId()));
        assertTrue(same.getMessage().startsWith("Nothing changed"), same.getMessage());
    }

    @Test
    @DisplayName("A grant needs a reason of at most 500 characters, an end date that is not past, and an active staff member")
    void grantIsValidated() {
        assertThrows(BadRequestException.class, () -> service.grant(aisyah.getId(), request(null, "  "), admin.getId()));
        assertThrows(BadRequestException.class,
                () -> service.grant(aisyah.getId(), request(null, "x".repeat(501)), admin.getId()));
        assertThrows(BadRequestException.class,
                () -> service.grant(aisyah.getId(), request(today.minusDays(1), "Sharing session"), admin.getId()));
        ravi.setAccountStatus(StaffAccountStatus.INACTIVE);
        assertThrows(BadRequestException.class, () -> service.grant(ravi.getId(), request(null, "Sharing session"), admin.getId()));
        assertThrows(BadRequestException.class,
                () -> service.grant(UUID.randomUUID(), request(null, "Sharing session"), admin.getId()));
        assertTrue(store.grants.isEmpty());
        assertTrue(store.auditLog.isEmpty());
    }

    @Test
    @DisplayName("Revoking ends the grant and is recorded")
    void revoke() {
        service.grant(aisyah.getId(), request(null, "Sharing session"), admin.getId());
        store.clock.advance(Duration.ofHours(2));

        TrainerAccessStaffDto dto = service.revoke(aisyah.getId(), request(null, "Session cancelled"), admin.getId());

        assertFalse(dto.isViaGrant());
        assertFalse(service.hasActiveGrant(aisyah.getId()));
        List<String> actions = service.history(aisyah.getId()).stream().map(TrainingAuditEntryDto::getAction).toList();
        assertEquals(List.of("TRAINER_ACCESS_REVOKED", "TRAINER_ACCESS_GRANTED"), actions);
    }

    @Test
    @DisplayName("Revoking someone who owns courses asks who takes them over, naming the courses")
    void revokeBlockedByOwnedCourses() {
        service.grant(aisyah.getId(), request(null, "Sharing session"), admin.getId());
        store.doc(1L, "Cash Handling", aisyah.getId());

        BadRequestException e = assertThrows(BadRequestException.class,
                () -> service.revoke(aisyah.getId(), request(null, "Moved to another outlet"), admin.getId()));

        assertTrue(e.getMessage().contains("Cash Handling"), e.getMessage());
        assertTrue(e.getMessage().contains("Choose who takes them over"), e.getMessage());
        assertTrue(service.hasActiveGrant(aisyah.getId()));
        assertEquals(1, service.history(aisyah.getId()).size(), "the refused revocation is not recorded");
    }

    @Test
    @DisplayName("Someone who keeps Trainer access through their role can lose a grant even while owning courses")
    void revokeWhenTheRoleKeepsAccess() {
        service.grant(weiMing.getId(), request(null, "Cover while the role is reviewed"), admin.getId());
        store.doc(1L, "Cash Handling", weiMing.getId());

        assertDoesNotThrow(() -> service.revoke(weiMing.getId(), request(null, "Role confirmed"), admin.getId()));
    }

    @Test
    @DisplayName("Revoking someone without a grant is refused, pointing to the Roles page when the access comes from the role")
    void revokeWithoutGrant() {
        assertThrows(BadRequestException.class, () -> service.revoke(aisyah.getId(), request(null, "x"), admin.getId()));
        BadRequestException role = assertThrows(BadRequestException.class,
                () -> service.revoke(weiMing.getId(), request(null, "x"), admin.getId()));
        assertTrue(role.getMessage().contains("job role"), role.getMessage());
    }

    @Test
    @DisplayName("Granting again after the end date has passed records the lapse before the new grant")
    void regrantAfterLapse() {
        service.grant(aisyah.getId(), request(today, "One-day session"), admin.getId());
        store.clock.advance(Duration.ofDays(2));

        service.grant(aisyah.getId(), request(null, "Presents again"), admin.getId());

        List<TrainingAuditEntryDto> history = service.history(aisyah.getId());
        assertEquals(List.of("TRAINER_ACCESS_GRANTED", "TRAINER_ACCESS_LAPSED", "TRAINER_ACCESS_GRANTED"),
                history.stream().map(TrainingAuditEntryDto::getAction).toList());
        assertNull(history.get(1).getActorName(), "the lapse is recorded by the system");
        assertEquals("Ended after 12 Oct 2026", history.get(1).getDetail());
        assertTrue(service.hasActiveGrant(aisyah.getId()));
    }

    @Test
    @DisplayName("The daily job records a lapse once the last day has passed, and alerts the Super Admin about courses left behind")
    void lapseAlertsAboutOwnedCourses() {
        service.grant(aisyah.getId(), request(today, "One-day session"), admin.getId());
        store.doc(1L, "Cash Handling", aisyah.getId());
        assertEquals(0, service.lapseExpiredGrants(), "the last day itself still counts");

        store.clock.advance(Duration.ofDays(1));
        assertEquals(1, service.lapseExpiredGrants());

        assertNotNull(store.grants.get(aisyah.getId()).getLapsedAt());
        TrainingAuditEntryDto lapse = service.history(aisyah.getId()).get(0);
        assertEquals("TRAINER_ACCESS_LAPSED", lapse.getAction());
        assertNull(lapse.getActorName(), "recorded by the system");
        assertEquals("Ended after 12 Oct 2026", lapse.getDetail());
        assertEquals(1, store.notifications.size());
        SopTestStore.Sent alert = store.notifications.get(0);
        assertEquals(admin.getId(), alert.recipient());
        assertEquals(NotificationType.TRAINER_ACCESS_LAPSED, alert.type());
        assertEquals("Trainer access ended: Nur Aisyah", alert.title());
        assertEquals("Nur Aisyah's Trainer access ended after 12 Oct 2026, but they still own 1 course: Cash Handling. "
                + "Transfer them to another trainer.", alert.body());
        assertEquals("/sop/trainer-access?tab=owners", alert.link(), "opens the Course owners tab");

        assertEquals(0, service.lapseExpiredGrants(), "a second run finds nothing to do");
        assertEquals(1, store.notifications.size());
        assertEquals(2, service.history(aisyah.getId()).size());
    }

    @Test
    @DisplayName("No alert when the person keeps access through their role or owns no courses")
    void lapseWithoutAlert() {
        service.grant(weiMing.getId(), request(today, "Cover"), admin.getId());
        store.doc(1L, "Cash Handling", weiMing.getId());
        service.grant(ravi.getId(), request(today, "Sharing session"), admin.getId());
        store.clock.advance(Duration.ofDays(1));

        assertEquals(2, service.lapseExpiredGrants());

        assertTrue(store.notifications.isEmpty());
    }

    @Test
    @DisplayName("Owned courses are the person's uploads that are not deleted")
    void ownedCourses() {
        store.doc(1L, "Cash Handling", aisyah.getId()).setVersion("2.0");
        store.doc(2L, "Forklift Safety", aisyah.getId()).setDeleted(true);
        store.doc(3L, "Data Privacy", weiMing.getId());

        assertEquals(List.of("Cash Handling"), service.ownedCourses(aisyah.getId()).stream().map(c -> c.getTitle()).toList());
        assertEquals("2.0", service.ownedCourses(aisyah.getId()).get(0).getVersion());
    }

    private static TrainerAccessRequest request(LocalDate endDate, String reason) {
        TrainerAccessRequest r = new TrainerAccessRequest();
        r.setEndDate(endDate);
        r.setReason(reason);
        return r;
    }
}
