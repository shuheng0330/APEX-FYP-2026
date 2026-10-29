package com.tbm.careerpathlearning.dto;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/** A category's name, colour and icon (FR-19-01, FR-19-02); or, for a course, which categories it carries (FR-19-05). */
@Data
public class CourseCategoryRequest {
    private String name;
    private String color;
    private String icon;
    /** For a course: its categories, at least one... */
    private List<Long> categoryIds = new ArrayList<>();
    /** ...and which is primary; the first when none is given. */
    private Long primaryId;
}
