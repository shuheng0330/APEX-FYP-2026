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
    private String assignedByName;
    private OffsetDateTime createdAt;
}
