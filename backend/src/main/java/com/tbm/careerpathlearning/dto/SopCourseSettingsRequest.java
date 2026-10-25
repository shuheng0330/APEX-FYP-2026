package com.tbm.careerpathlearning.dto;

import lombok.Data;

/** A course's own settings, set by its owner on the material review page (FR-07-15). */
@Data
public class SopCourseSettingsRequest {
    /** Percentage, 1 to 100. */
    private Integer passMark;
    /** Estimated duration in minutes, 30 to 1,440 (24 hours). */
    private Integer durationMinutes;
}
