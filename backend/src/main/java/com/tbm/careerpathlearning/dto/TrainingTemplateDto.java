package com.tbm.careerpathlearning.dto;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/** A saved session set-up, as listed for reuse (FR-12-08 to FR-12-10). */
@Data
public class TrainingTemplateDto {
    private Long id;
    private String name;
    private String createdByName;
    private LocalDateTime createdAt;
    private int timesUsed;
    private LocalDateTime lastUsedAt;
    /** The signed-in user saved it. */
    private boolean mine;
    /** Its creator or HR (FR-12-10). */
    private boolean canDelete;
    /** Who runs it, for the list: the staff member's name or the external trainer's. */
    private String trainerName;
    /** The form, filled in as saved; the date and cut-off are left for the trainer to choose. */
    private TrainingSessionRequest setup;
    /** What was left out because it no longer exists or is no longer allowed. */
    private List<String> leftOut = new ArrayList<>();
}
