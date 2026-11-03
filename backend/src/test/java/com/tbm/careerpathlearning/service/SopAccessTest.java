package com.tbm.careerpathlearning.service;

import com.tbm.careerpathlearning.exception.ForbiddenRequestException;
import com.tbm.careerpathlearning.model.SopDocument;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/** The ownership rule for SOP courses (FR-07-08, FR-07-19). */
class SopAccessTest {

    private final UUID ownerId = UUID.randomUUID();
    private final SopViewer owner = new SopViewer(ownerId, false);
    private final SopViewer otherTrainer = new SopViewer(UUID.randomUUID(), false);
    private final SopViewer hr = new SopViewer(UUID.randomUUID(), true);
    private final SopViewer superAdminWhoOwnsIt = new SopViewer(ownerId, true);

    @Test
    @DisplayName("The owner sees and changes the course; another trainer can do neither")
    void ownerAndOtherTrainer() {
        SopDocument course = course(ownerId);

        assertTrue(SopAccess.canView(course, owner));
        assertTrue(SopAccess.canEdit(course, owner));
        assertFalse(SopAccess.canView(course, otherTrainer));
        assertFalse(SopAccess.canEdit(course, otherTrainer));
        ForbiddenRequestException e = assertThrows(ForbiddenRequestException.class, () -> SopAccess.requireView(course, otherTrainer));
        assertEquals(SopAccess.ANOTHER_TRAINERS, e.getMessage());
    }

    @Test
    @DisplayName("HR and the Super Admin see every owned course read-only, and change only what they own")
    void readOnlyForEveryCourseViewers() {
        SopDocument course = course(ownerId);

        assertTrue(SopAccess.canView(course, hr));
        assertFalse(SopAccess.canEdit(course, hr));
        assertEquals(SopAccess.READ_ONLY,
                assertThrows(ForbiddenRequestException.class, () -> SopAccess.requireEdit(course, hr)).getMessage());
        assertTrue(SopAccess.canEdit(course, superAdminWhoOwnsIt));
    }

    @Test
    @DisplayName("A course without an owner is hidden from everyone")
    void ownerlessHidden() {
        SopDocument ownerless = course(null);

        assertFalse(SopAccess.canView(ownerless, hr));
        assertFalse(SopAccess.canView(ownerless, owner));
        assertEquals(SopAccess.NOT_FOUND,
                assertThrows(ForbiddenRequestException.class, () -> SopAccess.requireView(ownerless, hr)).getMessage());
    }

    @Test
    @DisplayName("A viewer always names who is signed in")
    void viewerNeedsAUser() {
        assertThrows(NullPointerException.class, () -> new SopViewer(null, true));
    }

    private static SopDocument course(UUID uploadedBy) {
        SopDocument d = new SopDocument();
        d.setId(1L);
        d.setUploadedBy(uploadedBy);
        return d;
    }
}
