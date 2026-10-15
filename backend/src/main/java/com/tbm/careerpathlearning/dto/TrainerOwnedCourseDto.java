package com.tbm.careerpathlearning.dto;

import lombok.Data;

/** A course a trainer owns, listed before their Trainer access is revoked (FR-20-07). */
@Data
public class TrainerOwnedCourseDto {
    private Long id;
    private String title;
    private String version;
}
