package com.tbm.careerpathlearning.service;

import com.tbm.careerpathlearning.dto.TrainingSessionRequest;
import com.tbm.careerpathlearning.dto.TrainingTemplateDto;

import java.util.List;

/**
 * Saved session set-ups (FR-12-08 to FR-12-10). Staff with Trainer access see and use their own templates; HR sees and
 * may delete everyone's (decision A, 28 Oct 2026).
 */
public interface TrainingTemplateService {

    /** The viewer's templates, or every template for HR, by name. */
    List<TrainingTemplateDto> list(TrainingViewer viewer);

    /** Saves a session's set-up under a name, without its date or cut-off (FR-12-08). */
    void save(String name, TrainingSessionRequest setup, TrainingViewer viewer);

    /** A session was scheduled from the template (FR-12-09). */
    void markUsed(Long templateId, TrainingViewer viewer);

    /** By its creator or HR only (FR-12-10). */
    void delete(Long templateId, TrainingViewer viewer);
}
