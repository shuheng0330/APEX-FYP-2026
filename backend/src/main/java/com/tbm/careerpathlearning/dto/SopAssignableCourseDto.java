package com.tbm.careerpathlearning.dto;

import com.tbm.careerpathlearning.enums.SopVisibility;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/** An SOP course as shown on the Assign Training page. */
@Data
public class SopAssignableCourseDto {
    private Long id;
    private String title;
    private String version;
    private String departmentTag;
    private String ownerName;
    /** Uploaded by the signed-in user. */
    private boolean mine;
    private int moduleCount;
    private int approvedModules;
    private int approvedQuizzes;
    /** Every module and its quiz approved, so it can be assigned (FR-07-03, FR-08-08). */
    private boolean ready;
    private SopVisibility visibility;
    private int enrolledCount;
    /** The course's own pass mark; an assignment may raise it but not lower it (FR-09-03). */
    private int passMark;
    /** The estimated duration that counts, in minutes (FR-07-15). */
    private int durationMinutes;
    /** False while the course still counts the default duration. */
    private boolean durationSet;
    /** Departments and job roles matching the SOP's department, suggested as the audience (FR-09-01). */
    private List<Long> recommendedDepartmentIds = new ArrayList<>();
    private List<Long> recommendedRoleIds = new ArrayList<>();
}
