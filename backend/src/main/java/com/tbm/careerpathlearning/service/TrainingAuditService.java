package com.tbm.careerpathlearning.service;

import com.tbm.careerpathlearning.dto.TrainingAuditEntryDto;
import com.tbm.careerpathlearning.enums.TrainingAuditAction;
import com.tbm.careerpathlearning.enums.TrainingAuditTarget;

import java.util.List;
import java.util.UUID;

/** The training audit log (NFR-007). */
public interface TrainingAuditService {

    /**
     * Records an action as part of the caller's transaction, so the entry is kept only if the action is.
     *
     * @param actorId who acted, or null for a scheduled job
     */
    void record(UUID actorId, TrainingAuditAction action, TrainingAuditTarget target, String targetId,
                String reason, String detail);

    /** The record's audit log, newest first. */
    List<TrainingAuditEntryDto> history(TrainingAuditTarget target, String targetId);
}
