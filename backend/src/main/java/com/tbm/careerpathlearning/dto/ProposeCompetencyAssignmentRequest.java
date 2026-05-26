package com.tbm.careerpathlearning.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.UUID;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class ProposeCompetencyAssignmentRequest {

    private Long orgChartId;
    private Long roleId;
    private String description;
    private List<String> jobScopeList;
    private List<ProposeCompetencyAssignmentMap> competencyList;
    private List<UUID> reviewerList;
    private List<UUID> proposerList;

    public Long getOrgChartId() {
        return orgChartId;
    }

    public void setOrgChartId(Long orgChartId) {
        this.orgChartId = orgChartId;
    }

    public Long getRoleId() {
        return roleId;
    }

    public void setRoleId(Long roleId) {
        this.roleId = roleId;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public List<String> getJobScopeList() {
        return jobScopeList;
    }

    public void setJobScopeList(List<String> jobScopeList) {
        this.jobScopeList = jobScopeList;
    }

    public List<ProposeCompetencyAssignmentMap> getCompetencyList() {
        return competencyList;
    }

    public void setCompetencyList(List<ProposeCompetencyAssignmentMap> competencyList) {
        this.competencyList = competencyList;
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