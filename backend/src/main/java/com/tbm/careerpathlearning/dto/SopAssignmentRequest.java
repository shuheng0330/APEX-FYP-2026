package com.tbm.careerpathlearning.dto;

import com.tbm.careerpathlearning.enums.SopAssignmentMode;
import lombok.Data;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Who gets a course and on what terms (FR-09-02, FR-09-03, FR-09-06, FR-09-09). */
@Data
public class SopAssignmentRequest {
    private Long sopDocumentId;
    /** Everyone in the company; the lists below are then ignored. */
    private boolean everyone;
    private List<Long> departmentIds = new ArrayList<>();
    private List<Long> roleIds = new ArrayList<>();
    private List<UUID> staffIds = new ArrayList<>();
    private SopAssignmentMode mode;
    private boolean mandatory;
    /** Defaults to today. */
    private LocalDate effectiveDate;
    private LocalDate deadline;
    private Integer daysToComplete;
    /** Defaults to 70. */
    private Integer passMark;
    /** Also assign people who join the chosen departments or job roles later; on unless false (FR-09-12). */
    private Boolean includeNewJoiners;
}
