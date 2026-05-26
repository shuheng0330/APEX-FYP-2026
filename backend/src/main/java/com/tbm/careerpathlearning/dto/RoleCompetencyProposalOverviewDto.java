package com.tbm.careerpathlearning.dto;

import com.tbm.careerpathlearning.model.RoleCompetencyProposalId;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class RoleCompetencyProposalOverviewDto {

    private RoleCompetencyProposalId id;
    private Long orgChartId;
    private String orgChartName;
    private Long roleId;
    private String roleName;
    private String description;
    private List<JobScopeDto> assignedJobScopes;
    private List<ProposeCompetencyAssignmentMap> assignedCompetencies;
    private String collaboratorName;
    private String collaboratorEmail;
    private Boolean isReviewer;
    private StaffDto lastUpdatedBy;
    private int totalWeightage;

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

    public String getOrgChartName() {
        return orgChartName;
    }

    public void setOrgChartName(String orgChartName) {
        this.orgChartName = orgChartName;
    }

    public Long getRoleId() {
        return roleId;
    }

    public void setRoleId(Long roleId) {
        this.roleId = roleId;
    }

    public String getRoleName() {
        return roleName;
    }

    public void setRoleName(String roleName) {
        this.roleName = roleName;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public List<JobScopeDto> getAssignedJobScopes() {
        return assignedJobScopes;
    }

    public void setAssignedJobScopes(List<JobScopeDto> assignedJobScopes) {
        this.assignedJobScopes = assignedJobScopes;
    }

    public List<ProposeCompetencyAssignmentMap> getAssignedCompetencies() {
        return assignedCompetencies;
    }

    public void setAssignedCompetencies(List<ProposeCompetencyAssignmentMap> assignedCompetencies) {
        this.assignedCompetencies = assignedCompetencies;
    }

    public String getCollaboratorName() {
        return collaboratorName;
    }

    public void setCollaboratorName(String collaboratorName) {
        this.collaboratorName = collaboratorName;
    }

    public String getCollaboratorEmail() {
        return collaboratorEmail;
    }

    public void setCollaboratorEmail(String collaboratorEmail) {
        this.collaboratorEmail = collaboratorEmail;
    }

    public Boolean getReviewer() {
        return isReviewer;
    }

    public void setReviewer(Boolean reviewer) {
        isReviewer = reviewer;
    }

    public StaffDto getLastUpdatedBy() {
        return lastUpdatedBy;
    }

    public void setLastUpdatedBy(StaffDto lastUpdatedBy) {
        this.lastUpdatedBy = lastUpdatedBy;
    }

    public int getTotalWeightage() {
        return totalWeightage;
    }

    public void setTotalWeightage(int totalWeightage) {
        this.totalWeightage = totalWeightage;
    }
}
