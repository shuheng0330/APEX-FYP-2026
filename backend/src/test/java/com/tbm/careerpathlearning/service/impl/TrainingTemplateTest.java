package com.tbm.careerpathlearning.service.impl;

import com.tbm.careerpathlearning.dto.TrainingSessionRequest;
import com.tbm.careerpathlearning.dto.TrainingTemplateDto;
import com.tbm.careerpathlearning.enums.AuthorityName;
import com.tbm.careerpathlearning.enums.StaffAccountStatus;
import com.tbm.careerpathlearning.enums.TrainingDelivery;
import com.tbm.careerpathlearning.enums.TrainingSessionKind;
import com.tbm.careerpathlearning.enums.TrainingType;
import com.tbm.careerpathlearning.exception.BadRequestException;
import com.tbm.careerpathlearning.exception.ForbiddenRequestException;
import com.tbm.careerpathlearning.model.OrgChart;
import com.tbm.careerpathlearning.model.Role;
import com.tbm.careerpathlearning.model.Staff;
import com.tbm.careerpathlearning.model.TrainingSessionTemplate;
import com.tbm.careerpathlearning.service.TrainingTemplateService;
import com.tbm.careerpathlearning.service.TrainingViewer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.*;

/** Saving a session's set-up as a template and scheduling from it again (FR-12-08 to FR-12-10, decision A of 28 Oct). */
class TrainingTemplateTest {

    private SopTestStore store;
    private TrainingServiceImpl sessions;
    private TrainingTemplateService templates;
    private Staff weiMing, karim, aisyah, ravi;
    private OrgChart sales;
    private TrainingViewer asHr, asWeiMing, asKarim, asAisyah;
    private LocalDate today;

    @BeforeEach
    void setUp() {
        store = new SopTestStore();
        sessions = store.wire(new TrainingServiceImpl());
        templates = store.sessionTemplates();
        sales = store.department(10L, "Sales Department");
        OrgChart people = store.department(12L, "Human Resources");
        Role salesExecutive = store.role(20L, "Sales Executive", sales);
        Role trainer = store.role(22L, "Trainer", people);
        Role hrExecutive = store.role(23L, "HR Executive", people);
        store.permit(trainer, AuthorityName.CAN_MANAGE_TRAINING);
        store.permit(hrExecutive, AuthorityName.CAN_ASSIGN_TRAINING);
        Staff hr = store.person("Hana Yusof", hrExecutive);
        weiMing = store.person("Tan Wei Ming", trainer);
        karim = store.person("Karim Abdullah", trainer);
        aisyah = store.person("Nur Aisyah", salesExecutive);
        ravi = store.person("Ravi Kumar", salesExecutive);
        asHr = new TrainingViewer(hr.getId(), false, true, false);
        asWeiMing = new TrainingViewer(weiMing.getId(), true, false, false);
        asKarim = new TrainingViewer(karim.getId(), true, false, false);
        asAisyah = new TrainingViewer(aisyah.getId(), false, false, false);
        today = store.today(); // Monday 12 Oct 2026
    }

    /** A Physical product training for Sales, Tuesday 20 Oct 2026, 09:00 to 12:00, run by Tan Wei Ming. */
    private TrainingSessionRequest request(Consumer<TrainingSessionRequest> change) {
        TrainingSessionRequest r = new TrainingSessionRequest();
        r.setTitle("Inverter Series 2026 Launch");
        r.setDescription("Bring your product handbook.");
        r.setKind(TrainingSessionKind.TRAINING);
        r.setDelivery(TrainingDelivery.PHYSICAL);
        r.setTrainingType(TrainingType.PRODUCT);
        r.setDate(today.plusDays(8));
        r.setStartTime(LocalTime.of(9, 0));
        r.setEndTime(LocalTime.of(12, 0));
        r.setVenue("Conference Hall B, HQ");
        r.setCapacity(20);
        r.setMandatory(true);
        r.setTrainerId(weiMing.getId());
        r.setDepartmentIds(List.of(sales.getId()));
        r.setStaffIds(List.of(ravi.getId()));
        r.setRegistrationForm(true);
        r.setRegistrationCutoff(today.plusDays(6));
        change.accept(r);
        return r;
    }

    private TrainingTemplateDto only(TrainingViewer viewer) {
        List<TrainingTemplateDto> list = templates.list(viewer);
        assertEquals(1, list.size());
        return list.get(0);
    }

    @Test
    @DisplayName("Scheduling can save the set-up as a template, without its date or cut-off (FR-12-08)")
    void savedFromTheForm() {
        sessions.schedule(request(r -> r.setTemplateName("  Inverter launch  ")), asWeiMing);

        TrainingTemplateDto t = only(asWeiMing);
        assertEquals("Inverter launch", t.getName());
        assertEquals("Tan Wei Ming", t.getCreatedByName());
        assertEquals("Tan Wei Ming", t.getTrainerName());
        assertEquals(0, t.getTimesUsed());
        assertNull(t.getLastUsedAt());
        assertTrue(t.isMine());
        assertTrue(t.isCanDelete());
        assertTrue(t.getLeftOut().isEmpty());
        TrainingSessionRequest setup = t.getSetup();
        assertEquals("Inverter Series 2026 Launch", setup.getTitle());
        assertEquals("Bring your product handbook.", setup.getDescription());
        assertEquals(TrainingType.PRODUCT, setup.getTrainingType());
        assertEquals(LocalTime.of(9, 0), setup.getStartTime());
        assertEquals(LocalTime.of(12, 0), setup.getEndTime());
        assertEquals("Conference Hall B, HQ", setup.getVenue());
        assertEquals(20, setup.getCapacity());
        assertTrue(setup.isMandatory());
        assertTrue(setup.isRegistrationForm());
        assertEquals(List.of(sales.getId()), setup.getDepartmentIds());
        assertEquals(List.of(ravi.getId()), setup.getStaffIds());
        assertNull(setup.getDate());
        assertNull(setup.getRegistrationCutoff());
        assertNull(setup.getTemplateName());

        // Changing a session can save its set-up too.
        Long id = sessions.schedule(request(r -> r.setTitle("Safety refresher")), asWeiMing).getId();
        sessions.update(id, request(r -> {
            r.setTitle("Safety refresher");
            r.setTemplateName("Safety refresher");
        }), asWeiMing);
        assertEquals(List.of("Inverter launch", "Safety refresher"), templates.list(asWeiMing).stream()
                .map(TrainingTemplateDto::getName).toList());
    }

    @Test
    @DisplayName("Trainers see only their own templates; HR sees everyone's (decision A)")
    void ownTemplatesOnly() {
        sessions.schedule(request(r -> r.setTemplateName("Wei Ming's launch")), asWeiMing);
        sessions.schedule(request(r -> {
            r.setTrainerId(karim.getId());
            r.setStartTime(LocalTime.of(14, 0));
            r.setEndTime(LocalTime.of(16, 0));
            r.setTemplateName("Karim's launch");
        }), asKarim);

        assertEquals("Wei Ming's launch", only(asWeiMing).getName());
        assertEquals("Karim's launch", only(asKarim).getName());
        List<TrainingTemplateDto> all = templates.list(asHr);
        assertEquals(List.of("Karim's launch", "Wei Ming's launch"), all.stream().map(TrainingTemplateDto::getName).toList());
        assertTrue(all.stream().noneMatch(TrainingTemplateDto::isMine));
        assertTrue(all.stream().allMatch(TrainingTemplateDto::isCanDelete));
        assertThrows(ForbiddenRequestException.class, () -> templates.list(asAisyah));
    }

    @Test
    @DisplayName("A template's use is counted when a session is scheduled from it, not when it is opened (FR-12-09)")
    void usedWhenScheduled() {
        sessions.schedule(request(r -> r.setTemplateName("Inverter launch")), asWeiMing);
        Long templateId = only(asWeiMing).getId();
        templates.list(asWeiMing); // opening the list counts nothing

        assertThrows(BadRequestException.class,
                () -> sessions.schedule(request(r -> { r.setTemplateId(templateId); r.setDate(today.minusDays(1)); }), asWeiMing));
        assertEquals(0, only(asWeiMing).getTimesUsed());

        store.clock.advance(Duration.ofHours(2));
        sessions.schedule(request(r -> { r.setTemplateId(templateId); r.setDate(today.plusDays(15)); }), asWeiMing);
        sessions.schedule(request(r -> { r.setTemplateId(templateId); r.setDate(today.plusDays(22)); }), asWeiMing);

        TrainingTemplateDto t = only(asWeiMing);
        assertEquals(2, t.getTimesUsed());
        assertEquals(LocalDateTime.of(today, LocalTime.of(12, 0)), t.getLastUsedAt());

        // Someone else's template isn't counted from a request naming it.
        sessions.schedule(request(r -> {
            r.setTrainerId(karim.getId());
            r.setDate(today.plusDays(9));
            r.setTemplateId(templateId);
        }), asKarim);
        assertEquals(2, only(asWeiMing).getTimesUsed());
    }

    @Test
    @DisplayName("Each person's templates have different names; the whole save is refused otherwise")
    void namesUniquePerPerson() {
        sessions.schedule(request(r -> r.setTemplateName("Inverter launch")), asWeiMing);
        int before = store.sessions.size();

        BadRequestException e = assertThrows(BadRequestException.class,
                () -> sessions.schedule(request(r -> {
                    r.setDate(today.plusDays(15));
                    r.setTemplateName("INVERTER LAUNCH");
                }), asWeiMing));
        assertEquals("You already have a template called \"INVERTER LAUNCH\". Choose another name, or delete the old "
                + "template first.", e.getMessage());
        assertEquals(before, store.sessions.size());
        assertThrows(BadRequestException.class, () -> sessions.schedule(request(r -> r.setTemplateName("x".repeat(101))), asWeiMing));

        sessions.schedule(request(r -> {
            r.setTrainerId(karim.getId());
            r.setDate(today.plusDays(9));
            r.setTemplateName("Inverter launch");
        }), asKarim);
        assertEquals("Inverter launch", only(asKarim).getName());
    }

    @Test
    @DisplayName("Only a template's creator, or HR, deletes it (FR-12-10)")
    void deleting() {
        sessions.schedule(request(r -> r.setTemplateName("Inverter launch")), asWeiMing);
        sessions.schedule(request(r -> { r.setDate(today.plusDays(15)); r.setTemplateName("Second run"); }), asWeiMing);
        Long first = templates.list(asWeiMing).get(0).getId();
        Long second = templates.list(asWeiMing).get(1).getId();

        assertThrows(ForbiddenRequestException.class, () -> templates.delete(first, asKarim));
        assertThrows(ForbiddenRequestException.class, () -> templates.delete(first, asAisyah));
        templates.delete(first, asHr);
        templates.delete(second, asWeiMing);

        assertTrue(templates.list(asWeiMing).isEmpty());
        assertEquals("This template no longer exists.",
                assertThrows(BadRequestException.class, () -> templates.delete(first, asWeiMing)).getMessage());
    }

    @Test
    @DisplayName("People who left, removed departments and a trainer without Trainer access are left out, with a note")
    void leftOut() {
        Staff farid = store.person("Ahmad Farid", aisyah.getRole());
        sessions.schedule(request(r -> {
            r.setStaffIds(List.of(ravi.getId(), farid.getId()));
            r.setTemplateName("Inverter launch");
        }), asWeiMing);
        farid.setAccountStatus(StaffAccountStatus.INACTIVE);
        sales.setDeleted(true);
        weiMing.setRole(aisyah.getRole()); // no longer a Trainer

        TrainingTemplateDto t = only(asHr);

        assertEquals(List.of(ravi.getId()), t.getSetup().getStaffIds());
        assertTrue(t.getSetup().getDepartmentIds().isEmpty());
        assertNull(t.getSetup().getTrainerId());
        assertEquals(List.of(
                "Left out of the audience, as they no longer exist or have left: 1 department, 1 person.",
                "Tan Wei Ming no longer has Trainer access, so choose who runs the session."), t.getLeftOut());
    }

    @Test
    @DisplayName("A template whose saved set-up can't be read still lists, so it can be deleted")
    void unreadable() {
        TrainingSessionTemplate broken = new TrainingSessionTemplate();
        broken.setName("Old template");
        broken.setSetup("{not json");
        broken.setCreatedBy(weiMing.getId());
        broken.setCreatedAt(LocalDateTime.of(today, LocalTime.NOON));
        store.templates.put(1L, broken);
        broken.setTemplateId(1L);

        TrainingTemplateDto t = only(asWeiMing);
        assertEquals("Old template", t.getSetup().getTitle());
        assertEquals(List.of("This template could not be read. Delete it, then save the set-up again."), t.getLeftOut());
        templates.delete(1L, asWeiMing);
        assertTrue(store.templates.isEmpty());
    }
}
