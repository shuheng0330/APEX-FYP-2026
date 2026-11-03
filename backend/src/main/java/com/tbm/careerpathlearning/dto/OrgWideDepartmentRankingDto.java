package com.tbm.careerpathlearning.dto;

import lombok.Data;

@Data
public class OrgWideDepartmentRankingDto {
    private String departmentName;
    private Double averageScore;
    private Double previousAverageScore;
    private Double scoreChange;
    private Long staffCount;
    private String status;
}
