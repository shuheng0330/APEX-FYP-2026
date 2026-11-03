package com.tbm.careerpathlearning.dto;

import lombok.Data;

@Data
public class OrgWideSummaryDto {
    private Long totalStaffEvaluated;
    private Long totalStaff;
    private Double orgAverageScore;
    private String topDepartment;
    private Double topDepartmentScore;
    private Long pendingAppraisals;
}
