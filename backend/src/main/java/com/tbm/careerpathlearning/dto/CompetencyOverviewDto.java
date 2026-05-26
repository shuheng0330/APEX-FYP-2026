package com.tbm.careerpathlearning.dto;

import lombok.Data;

import java.util.List;

@Data
public class CompetencyOverviewDto {

    private Long competencyId;
    private String competencyName;
    private String competencyDescription;
    private List<CompTagDto> assignedCompTags;

    public Long getCompetencyId() {
        return competencyId;
    }

    public void setCompetencyId(Long competencyId) {
        this.competencyId = competencyId;
    }

    public String getCompetencyName() {
        return competencyName;
    }

    public void setCompetencyName(String competencyName) {
        this.competencyName = competencyName;
    }

    public String getCompetencyDescription() {
        return competencyDescription;
    }

    public void setCompetencyDescription(String competencyDescription) {
        this.competencyDescription = competencyDescription;
    }

    public List<CompTagDto> getAssignedCompTags() {
        return assignedCompTags;
    }

    public void setAssignedCompTags(List<CompTagDto> assignedCompTags) {
        this.assignedCompTags = assignedCompTags;
    }
}
