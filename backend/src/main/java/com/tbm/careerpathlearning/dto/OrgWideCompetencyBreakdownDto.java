package com.tbm.careerpathlearning.dto;

import lombok.Data;

import java.util.List;

@Data
public class OrgWideCompetencyBreakdownDto {
    private String departmentName;
    private List<OrgWideCompetencyAverageDto> competencies;
}
