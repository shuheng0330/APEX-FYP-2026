package com.tbm.careerpathlearning.service;

import com.tbm.careerpathlearning.model.SopDocument;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Course versions (FR-06-06, FR-07-16 to FR-07-18). Each version is its own course record, and every version of a
 * course shares its course id. Staff take the live version. Once anyone has been assigned or joined a course,
 * changes other than material text, pass mark and duration go into a draft of the next version, which goes live
 * only when it is published.
 */
public interface SopVersionService {

    /** Every version of the course, oldest first. */
    List<SopDocument> versionsOf(SopDocument version);

    /** The draft of the course's next version, if one is in progress (at most one, P2-18). */
    Optional<SopDocument> draftOf(SopDocument version);

    /** Whether anyone has been assigned or joined this version (P2-15). */
    boolean hasLearners(SopDocument version);

    /**
     * An unsaved draft of the live course's next version, with the course's own settings: owner, title,
     * department, who can join it, pass mark and duration. Refused when the course already has a draft (P2-18).
     */
    SopDocument newDraftOf(SopDocument live);

    /** A live course copied into the draft of its next version, and where each module and question went. */
    record DraftCopy(SopDocument draft, Map<Long, Long> moduleIds, Map<Long, Long> questionIds) {
    }

    /**
     * Copies a live course into the draft of its next version: its details, every module and every quiz question,
     * as they are (FR-07-16). Staff keep taking the live version. Logged with the reason for the new version.
     */
    DraftCopy createDraft(SopDocument live, UUID actorId, String reason);

    /**
     * Puts an approved draft live (FR-07-17, FR-07-18). The version it replaces is kept as superseded. Staff still
     * taking it restart on the new version with a fresh window from today, keeping their earlier progress in their
     * history; staff who completed it keep their certificate and, when {@code redoCompleted}, must also complete
     * the new version. Everyone enrolled is told.
     */
    SopDocument publish(SopDocument draft, boolean redoCompleted, UUID actorId);

    /** Deletes a draft with everything copied or generated for it; the live version is untouched. */
    void discard(SopDocument draft, UUID actorId);
}
