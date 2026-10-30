package com.tbm.careerpathlearning.service;

import com.tbm.careerpathlearning.exception.ForbiddenRequestException;
import com.tbm.careerpathlearning.model.SopDocument;

import java.util.Objects;

/**
 * Who may see and change an SOP course (FR-07-08, FR-07-19). The course's owner is the person who uploaded it.
 * <ul>
 *   <li>The owner sees and changes it.</li>
 *   <li>HR and the Super Admin see every course that has an owner, read-only.</li>
 *   <li>Other trainers cannot see it at all.</li>
 *   <li>A course without an owner is hidden from everyone until the Super Admin gives it one.</li>
 * </ul>
 * Every SOP page and endpoint uses these rules, so they live in one place.
 */
public final class SopAccess {

    static final String NOT_FOUND = "SOP document not found.";
    static final String ANOTHER_TRAINERS = "This course belongs to another trainer.";
    static final String READ_ONLY = "You can view this course but not change it. Only its owner can.";

    private SopAccess() {
    }

    public static boolean isOwner(SopDocument doc, SopViewer viewer) {
        Objects.requireNonNull(doc, "doc");
        Objects.requireNonNull(viewer, "viewer");
        return doc.getUploadedBy() != null && doc.getUploadedBy().equals(viewer.userId());
    }

    public static boolean canView(SopDocument doc, SopViewer viewer) {
        return isOwner(doc, viewer) || (viewer.seesAllCourses() && doc.getUploadedBy() != null);
    }

    public static boolean canEdit(SopDocument doc, SopViewer viewer) {
        return isOwner(doc, viewer);
    }

    /** @throws ForbiddenRequestException when the viewer may not see the course */
    public static void requireView(SopDocument doc, SopViewer viewer) {
        if (!canView(doc, viewer)) {
            throw new ForbiddenRequestException(doc.getUploadedBy() == null ? NOT_FOUND : ANOTHER_TRAINERS);
        }
    }

    /** @throws ForbiddenRequestException when the viewer may not change the course */
    public static void requireEdit(SopDocument doc, SopViewer viewer) {
        requireView(doc, viewer);
        if (!canEdit(doc, viewer)) {
            throw new ForbiddenRequestException(READ_ONLY);
        }
    }
}
