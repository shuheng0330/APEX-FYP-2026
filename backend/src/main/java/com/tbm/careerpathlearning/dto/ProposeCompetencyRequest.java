package com.tbm.careerpathlearning.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.UUID;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class ProposeCompetencyRequest {

    private String competencyName;
    private String competencyDescription;
    private List<String> competencyTagList;
    private List<UUID> reviewerList;
    private List<UUID> proposerList;

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

    public List<String> getCompetencyTagList() {
        return competencyTagList;
    }

    public void setCompetencyTagList(List<String> competencyTagList) {
        this.competencyTagList = competencyTagList;
    }

    public List<UUID> getReviewerList() {
        return reviewerList;
    }

    public void setReviewerList(List<UUID> reviewerList) {
        this.reviewerList = reviewerList;
    }

    public List<UUID> getProposerList() {
        return proposerList;
    }

    public void setProposerList(List<UUID> proposerList) {
        this.proposerList = proposerList;
    }
}