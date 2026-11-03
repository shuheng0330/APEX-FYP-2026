package com.tbm.careerpathlearning.dto;

import com.tbm.careerpathlearning.enums.SopAssignmentMode;
import lombok.Data;

import java.time.LocalDate;
import java.time.OffsetDateTime;

/** One entry in the assignment history (FR-09-08). */
@Data
public class SopAssignmentDto {
    private Long id;
    private Long sopDocumentId;
    private String courseTitle;
    private String audience;
    private int staffCount;
    private SopAssignmentMode mode;
    private boolean mandatory;
    private LocalDate effectiveDate;
    private LocalDate deadline;
    private int daysToComplete;
    private int passMark;
    /** People who join the audience later get the course too (FR-09-12). */
    private boolean includeNewJoiners;
    private String assignedByName;
    private OffsetDateTime createdAt;
}
