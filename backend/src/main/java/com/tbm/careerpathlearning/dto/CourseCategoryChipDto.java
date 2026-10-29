package com.tbm.careerpathlearning.dto;

import lombok.Data;

/** One of a course's categories, as a coloured tag; the primary one comes first (FR-19-05). */
@Data
public class CourseCategoryChipDto {
    private Long id;
    private String name;
    private String color;
    private String icon;
    private boolean primary;
    /** Retired since it was added: still shown, no longer offered. */
    private boolean retired;
}
