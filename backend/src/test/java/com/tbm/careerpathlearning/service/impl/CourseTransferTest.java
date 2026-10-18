package com.tbm.careerpathlearning.service.impl;

import com.tbm.careerpathlearning.dto.CourseOwnerDto;
import com.tbm.careerpathlearning.dto.CourseTransferRequest;
import com.tbm.careerpathlearning.dto.TrainerAccessRequest;
import com.tbm.careerpathlearning.dto.TrainingAuditEntryDto;
import com.tbm.careerpathlearning.enums.AuthorityName;
import com.tbm.careerpathlearning.enums.CourseOwnerStatus;
import com.tbm.careerpathlearning.enums.SopCourseStatus;
import com.tbm.careerpathlearning.enums.NotificationType;
import com.tbm.careerpathlearning.enums.SopAssignmentMode;
import com.tbm.careerpathlearning.enums.StaffAccountStatus;
import com.tbm.careerpathlearning.exception.BadRequestException;
import com.tbm.careerpathlearning.model.OrgChart;
import com.tbm.careerpathlearning.model.Role;
import com.tbm.careerpathlearning.model.SopDocument;
import com.tbm.careerpathlearning.model.Staff;
import com.tbm.careerpathlearning.service.SopAccess;
import com.tbm.careerpathlearning.service.SopViewer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/** The Super Admin gives a course whose owner account is abandoned to another trainer (FR-20-06, FR-20-07). */
class CourseTransferTest {

    private SopTestStore store;
    private TrainerAccessServiceImpl service;
    private Role superAdminRole, trainerRole;
    private Staff admin, weiMing, aisyah, ravi;
    private LocalDate today;

    @BeforeEach
    void setUp() {
        store = new SopTestStore();
        service = store.wire(new TrainerAccessServiceImpl());
        today = store.today();
        OrgChart hr = store.department(12L, "HR Department");
        OrgChart sales = store.department(10L, "Sales Department");
        superAdminRole = store.role(1L, "superadmin", hr);
        trainerRole = store.role(22L, "Trainer", hr);
        Role salesExecutive = store.role(20L, "Sales Executive", sales);
        store.permit(superAdminRole, AuthorityName.CAN_MANAGE_ACCESS_CONTROL);
        store.permit(trainerRole, AuthorityName.CAN_MANAGE_TRAINING);
        admin = store.person("Super Admin", superAdminRole);
        weiMing = store.person("Tan Wei Ming", trainerRole);
        aisyah = store.person("Nur Aisyah", salesExecutive);
        ravi = store.person("Ravi Kumar", salesExecutive);
    }

    @Test
    @DisplayName("The Course owners list flags every course whose owner can no longer look after it, newest first")
    void ownerStatuses() {
        service.grant(aisyah.getId(), request(null, "Sharing session"), admin.getId());
        Staff left = store.person("Lim Mei Ling", trainerRole);
        left.setAccountStatus(StaffAccountStatus.INACTIVE);
        Staff removed = store.person("Ahmad Faiz", trainerRole);
        removed.setDeleted(true);
        store.doc(1L, "Data Privacy", weiMing.getId());
        store.doc(2L, "Cash Handling", aisyah.getId());
        store.doc(3L, "Forklift Safety", ravi.getId());
        store.doc(4L, "Fire Drill", left.getId());
        store.doc(5L, "First Aid", removed.getId());
        store.doc(6L, "[EVAL] Sample", null);
        store.doc(7L, "Old Course", ravi.getId()).setDeleted(true);
        store.enrollment(3L, aisyah.getId(), SopAssignmentMode.PORTAL, today, today.plusDays(14), 14, 70);
        store.enrollment(3L, weiMing.getId(), SopAssignmentMode.PORTAL, today, today.plusDays(14), 14, 70);

        List<CourseOwnerDto> list = service.courseOwners();

        assertEquals(List.of("Data Privacy", "Cash Handling", "Forklift Safety", "Fire Drill", "First Aid", "[EVAL] Sample"),
                list.stream().map(CourseOwnerDto::getTitle).toList(), "deleted courses are left out");
        assertEquals(List.of(CourseOwnerStatus.OK, CourseOwnerStatus.OK, CourseOwnerStatus.NO_TRAINER_ACCESS,
                        CourseOwnerStatus.OWNER_LEFT, CourseOwnerStatus.OWNER_LEFT, CourseOwnerStatus.NO_OWNER),
                list.stream().map(CourseOwnerDto::getStatus).toList());
        assertEquals(List.of(false, false, true, true, true, true),
                list.stream().map(CourseOwnerDto::isNeedsNewOwner).toList());
        assertEquals("Ahmad Faiz", list.get(4).getOwnerName(), "an owner whose account was removed is still named");
        assertNull(list.get(5).getOwnerId());
        assertNull(list.get(5).getOwnerName());
        assertEquals(2, list.get(2).getEnrolledCount());
        assertEquals(0, list.get(0).getEnrolledCount());
    }

    @Test
    @DisplayName("Transferring an abandoned course moves it to the new owner, records why, and tells only the new owner")
    void transferAbandonedCourse() {
        SopDocument course = store.doc(3L, "Forklift Safety", ravi.getId());

        CourseOwnerDto dto = service.transferCourse(3L, transfer(weiMing.getId(), "Ravi moved to the Sales team"),
                admin.getId());

        assertEquals(weiMing.getId(), course.getUploadedBy());
        assertEquals(CourseOwnerStatus.OK, dto.getStatus());
        assertEquals("Tan Wei Ming", dto.getOwnerName());
        assertTrue(SopAccess.canEdit(course, new SopViewer(weiMing.getId(), false)), "the new owner can change it");
        assertFalse(SopAccess.canView(course, new SopViewer(ravi.getId(), false)), "the old owner no longer sees it");

        List<TrainingAuditEntryDto> history = service.courseHistory(3L);
        assertEquals(1, history.size());
        assertEquals("COURSE_TRANSFERRED", history.get(0).getAction());
        assertEquals("Super Admin", history.get(0).getActorName());
        assertEquals("Ravi moved to the Sales team", history.get(0).getReason());
        assertEquals("From Ravi Kumar to Tan Wei Ming", history.get(0).getDetail());

        assertEquals(1, store.notifications.size(), "only the new owner is told");
        SopTestStore.Sent sent = store.notifications.get(0);
        assertEquals(weiMing.getId(), sent.recipient());
        assertEquals(NotificationType.COURSE_TRANSFERRED, sent.type());
        assertEquals("Course transferred to you: Forklift Safety", sent.title());
        assertEquals("Super Admin made you the owner of \"Forklift Safety\". You can now edit, review and assign it.",
                sent.body());
        assertEquals("/sop/review/3", sent.link());
    }

    @Test
    @DisplayName("A course without an owner can be given one, and its history says it had none")
    void transferOwnerlessCourse() {
        service.grant(aisyah.getId(), request(null, "Sharing session"), admin.getId());
        store.doc(6L, "[EVAL] Sample", null);

        service.transferCourse(6L, transfer(aisyah.getId(), "Keep the sample for the next intake"), admin.getId());

        assertEquals(aisyah.getId(), store.docs.get(6L).getUploadedBy());
        assertEquals("From no owner to Nur Aisyah", service.courseHistory(6L).get(0).getDetail());
    }

    @Test
    @DisplayName("A course whose owner still has Trainer access, through their role or a grant, cannot be transferred")
    void ownerWithAccessKeepsTheCourse() {
        service.grant(aisyah.getId(), request(null, "Sharing session"), admin.getId());
        store.doc(1L, "Data Privacy", weiMing.getId());
        store.doc(2L, "Cash Handling", aisyah.getId());
        int auditBefore = store.auditLog.size();

        assertRefused("still has Trainer access",
                () -> service.transferCourse(1L, transfer(aisyah.getId(), "Rebalance the courses"), admin.getId()));
        assertRefused("still has Trainer access",
                () -> service.transferCourse(2L, transfer(weiMing.getId(), "Rebalance the courses"), admin.getId()));

        assertEquals(weiMing.getId(), store.docs.get(1L).getUploadedBy());
        assertEquals(aisyah.getId(), store.docs.get(2L).getUploadedBy());
        assertEquals(auditBefore, store.auditLog.size());
        assertTrue(store.notifications.isEmpty());
    }

    @Test
    @DisplayName("A transfer needs a reason and a new owner who is someone else, active, and has Trainer access")
    void transferIsValidated() {
        store.doc(3L, "Forklift Safety", ravi.getId());
        store.doc(8L, "Old Course", null).setDeleted(true);
        Staff left = store.person("Lim Mei Ling", trainerRole);
        left.setAccountStatus(StaffAccountStatus.INACTIVE);

        assertRefused("Give a reason", () -> service.transferCourse(3L, transfer(weiMing.getId(), "  "), admin.getId()));
        assertRefused("Give a reason", () -> service.transferCourse(3L, null, admin.getId()));
        assertRefused("Choose who takes over", () -> service.transferCourse(3L, transfer(null, "Ravi left"), admin.getId()));
        assertRefused("someone other than the current owner",
                () -> service.transferCourse(3L, transfer(ravi.getId(), "Ravi left"), admin.getId()));
        assertRefused("active staff member",
                () -> service.transferCourse(3L, transfer(left.getId(), "Ravi left"), admin.getId()));
        assertRefused("Nur Aisyah has no Trainer access",
                () -> service.transferCourse(3L, transfer(aisyah.getId(), "Ravi left"), admin.getId()));
        assertRefused("Course not found",
                () -> service.transferCourse(99L, transfer(weiMing.getId(), "Ravi left"), admin.getId()));
        assertRefused("Course not found",
                () -> service.transferCourse(8L, transfer(weiMing.getId(), "Ravi left"), admin.getId()));

        assertEquals(ravi.getId(), store.docs.get(3L).getUploadedBy());
        assertTrue(store.auditLog.isEmpty());
        assertTrue(store.notifications.isEmpty());
    }

    @Test
    @DisplayName("Revoking someone who owns courses hands every course to the chosen trainer in the same step")
    void revokeTransfersTheirCourses() {
        service.grant(aisyah.getId(), request(null, "Sharing session"), admin.getId());
        store.doc(1L, "Cash Handling", aisyah.getId());
        store.doc(2L, "Customer Service", aisyah.getId());
        store.clock.advance(Duration.ofHours(1));

        service.revoke(aisyah.getId(), revoke("Moved to another outlet", weiMing.getId()), admin.getId());

        assertFalse(service.hasActiveGrant(aisyah.getId()));
        assertEquals(weiMing.getId(), store.docs.get(1L).getUploadedBy());
        assertEquals(weiMing.getId(), store.docs.get(2L).getUploadedBy());
        assertEquals(List.of("TRAINER_ACCESS_REVOKED", "TRAINER_ACCESS_GRANTED"),
                service.history(aisyah.getId()).stream().map(TrainingAuditEntryDto::getAction).toList());
        for (long id : new long[]{1L, 2L}) {
            TrainingAuditEntryDto moved = service.courseHistory(id).get(0);
            assertEquals("From Nur Aisyah to Tan Wei Ming", moved.getDetail());
            assertEquals("Moved to another outlet", moved.getReason());
        }
        assertEquals(1, store.notifications.size(), "one message covers all the courses");
        SopTestStore.Sent sent = store.notifications.get(0);
        assertEquals(weiMing.getId(), sent.recipient());
        assertEquals("2 courses transferred to you", sent.title());
        assertEquals("Super Admin made you the owner of Cash Handling, Customer Service. "
                + "You can now edit, review and assign them.", sent.body());
        assertEquals("/sop/upload", sent.link());
        assertTrue(service.courseOwners().stream().noneMatch(CourseOwnerDto::isNeedsNewOwner), "nothing is left behind");
    }

    @Test
    @DisplayName("A revoke whose new owner is refused changes nothing: the access, the courses and the history stay as they were")
    void refusedRevokeChangesNothing() {
        service.grant(aisyah.getId(), request(null, "Sharing session"), admin.getId());
        store.doc(1L, "Cash Handling", aisyah.getId());

        assertRefused("Choose who takes them over",
                () -> service.revoke(aisyah.getId(), revoke("Moved to another outlet", null), admin.getId()));
        assertRefused("Ravi Kumar has no Trainer access",
                () -> service.revoke(aisyah.getId(), revoke("Moved to another outlet", ravi.getId()), admin.getId()));
        assertRefused("someone other than the current owner",
                () -> service.revoke(aisyah.getId(), revoke("Moved to another outlet", aisyah.getId()), admin.getId()));
        assertRefused("active staff member",
                () -> service.revoke(aisyah.getId(), revoke("Moved to another outlet", UUID.randomUUID()), admin.getId()));

        assertTrue(service.hasActiveGrant(aisyah.getId()));
        assertEquals(aisyah.getId(), store.docs.get(1L).getUploadedBy());
        assertEquals(1, service.history(aisyah.getId()).size(), "only the grant is recorded");
        assertTrue(service.courseHistory(1L).isEmpty());
        assertTrue(store.notifications.isEmpty());
    }

    @Test
    @DisplayName("When the Super Admin takes a course over themselves, the move is recorded but no message is sent")
    void transferToTheSuperAdmin() {
        store.permit(superAdminRole, AuthorityName.CAN_MANAGE_TRAINING);
        store.doc(3L, "Forklift Safety", ravi.getId());

        service.transferCourse(3L, transfer(admin.getId(), "I will look after it"), admin.getId());

        assertEquals(admin.getId(), store.docs.get(3L).getUploadedBy());
        assertEquals("From Ravi Kumar to Super Admin", service.courseHistory(3L).get(0).getDetail());
        assertTrue(store.notifications.isEmpty());
    }

    @Test
    @DisplayName("A course needs a new owner from the day after its owner's last day, even before the daily job runs")
    void statusFollowsTheEndDate() {
        service.grant(aisyah.getId(), request(today, "One-day session"), admin.getId());
        store.doc(2L, "Cash Handling", aisyah.getId());
        assertEquals(CourseOwnerStatus.OK, service.courseOwners().get(0).getStatus(), "the last day still counts");

        store.clock.advance(Duration.ofDays(1));

        assertEquals(CourseOwnerStatus.NO_TRAINER_ACCESS, service.courseOwners().get(0).getStatus());
        assertDoesNotThrow(() -> service.transferCourse(2L, transfer(weiMing.getId(), "Session over"), admin.getId()));
    }

    @Test
    @DisplayName("A course with several versions is one row, and a transfer moves every version, its draft included")
    void transferMovesEveryVersion() {
        SopDocument v1 = store.doc(3L, "Forklift Safety", ravi.getId());
        v1.setCourseStatus(SopCourseStatus.SUPERSEDED);
        SopDocument v2 = store.doc(4L, "Forklift Safety", ravi.getId());
        v2.setCourseId(3L);
        v2.setVersionNo(2);
        SopDocument v3 = store.doc(5L, "Forklift Safety", ravi.getId());
        v3.setCourseId(3L);
        v3.setVersionNo(3);
        v3.setCourseStatus(SopCourseStatus.DRAFT);

        List<CourseOwnerDto> rows = service.courseOwners();
        assertEquals(List.of(4L), rows.stream().map(CourseOwnerDto::getCourseId).toList(), "one row, for the live version");
        assertEquals(2, rows.get(0).getVersionNo());

        service.transferCourse(4L, transfer(weiMing.getId(), "Ravi moved to the Sales team"), admin.getId());

        assertEquals(List.of(weiMing.getId(), weiMing.getId(), weiMing.getId()),
                List.of(v1.getUploadedBy(), v2.getUploadedBy(), v3.getUploadedBy()));
        assertEquals("From Ravi Kumar to Tan Wei Ming", service.courseHistory(4L).get(0).getDetail());
        assertEquals(service.courseHistory(4L), service.courseHistory(3L), "every version shares one history");
        assertTrue(service.ownedCourses(ravi.getId()).isEmpty());
        assertEquals(1, service.ownedCourses(weiMing.getId()).size(), "counted once, not once per version");
    }

    private static void assertRefused(String expected, Executable action) {
        BadRequestException e = assertThrows(BadRequestException.class, action);
        assertTrue(e.getMessage().contains(expected), e.getMessage());
    }

    private static TrainerAccessRequest request(LocalDate endDate, String reason) {
        TrainerAccessRequest r = new TrainerAccessRequest();
        r.setEndDate(endDate);
        r.setReason(reason);
        return r;
    }

    private static TrainerAccessRequest revoke(String reason, UUID transferToId) {
        TrainerAccessRequest r = request(null, reason);
        r.setTransferToId(transferToId);
        return r;
    }

    private static CourseTransferRequest transfer(UUID newOwnerId, String reason) {
        CourseTransferRequest r = new CourseTransferRequest();
        r.setNewOwnerId(newOwnerId);
        r.setReason(reason);
        return r;
    }
}
