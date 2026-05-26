package com.tbm.careerpathlearning.dto;

public class CompetencyWithWeightageDto {

    private Long competencyId;
    private String competencyName;
    private String competencyDescription;
    private int weightage;

    public CompetencyWithWeightageDto(){}

    public CompetencyWithWeightageDto(Long competencyId, String competencyName, String competencyDescription, int weightage) {
        this.competencyId = competencyId;
        this.competencyName = competencyName;
        this.competencyDescription = competencyDescription;
        this.weightage = weightage;
    }

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

    public int getWeightage() {
        return weightage;
    }

    public void setWeightage(int weightage) {
        this.weightage = weightage;
    }
}

