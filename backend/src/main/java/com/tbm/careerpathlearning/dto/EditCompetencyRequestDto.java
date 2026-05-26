package com.tbm.careerpathlearning.dto;

import java.util.List;

public class EditCompetencyRequestDto {

    private Long competencyId;
    private String competencyName;
    private String competencyDescription;
    private List<String> compTagList;

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

    public List<String> getCompTagList() {
        return compTagList;
    }

    public void setCompTagList(List<String> compTagList) {
        this.compTagList = compTagList;
    }
}
