package com.tbm.careerpathlearning.dto;

import lombok.Data;

/** A course category on the Course Categories page (FR-19-01 to FR-19-04), with what the signed-in user may do. */
@Data
public class CourseCategoryDto {
    private Long id;
    private String name;
    private String color;
    private String icon;
    /** Comes with the system; General is the one courses fall back to. */
    private boolean defaultCategory;
    private boolean general;
    private boolean retired;
    /** Courses carrying it; for General, also those with no other category. */
    private int courseCount;
    private String createdByName;
    /** Recolour, and rename unless it is General. */
    private boolean canEdit;
    private boolean canRename;
    /** Retire or restore: HR and the Super Admin, defaults only, never General (FR-19-03). */
    private boolean canRetire;
    /** Custom categories only: their creator, HR and the Super Admin (FR-19-04). */
    private boolean canDelete;
}
