package com.tbm.careerpathlearning.dto;

import com.tbm.careerpathlearning.model.RoleCompetencyProposalId;

import java.util.List;
import java.util.Objects;

public class EditCompetencyAssignmentProposalRequestDto {

    private RoleCompetencyProposalId id;
    private Long orgChartId;
    private Long roleId;
    private String description;
    private List<String> jobScopeList;
    private List<ProposeCompetencyAssignmentMap> competencyList;

    public RoleCompetencyProposalId getId() {
        return id;
    }

    public void setId(RoleCompetencyProposalId id) {
        this.id = id;
    }

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

    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass()) return false;
        EditCompetencyAssignmentProposalRequestDto that = (EditCompetencyAssignmentProposalRequestDto) o;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(id);
    }
}
