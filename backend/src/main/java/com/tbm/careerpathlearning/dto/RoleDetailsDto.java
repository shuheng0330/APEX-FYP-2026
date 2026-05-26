package com.tbm.careerpathlearning.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class RoleDetailsDto {

    private Long orgChartId;
    private String orgChartName;
    private Long roleId;
    private String roleName;
    private String roleDescription;
    private boolean isVisible;
    private List<JobScopeDto> jobScopes;
    private List<RoleCompetencyDto> competencies;
    private List<StaffDto> staffs;
    private int totalWeightage;
    private Map<Long, List<String>> compTags;

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

    public String getRoleDescription() {
        return roleDescription;
    }

    public void setRoleDescription(String roleDescription) {
        this.roleDescription = roleDescription;
    }

    public boolean isVisible() {
        return isVisible;
    }

    public void setVisible(boolean visible) {
        isVisible = visible;
    }

    public List<JobScopeDto> getJobScopes() {
        return jobScopes;
    }

    public void setJobScopes(List<JobScopeDto> jobScopes) {
        this.jobScopes = jobScopes;
    }

    public List<RoleCompetencyDto> getCompetencies() {
        return competencies;
    }

    public void setCompetencies(List<RoleCompetencyDto> competencies) {
        this.competencies = competencies;
    }

    public List<StaffDto> getStaffs() {
        return staffs;
    }

    public void setStaffs(List<StaffDto> staffs) {
        this.staffs = staffs;
    }

    public int getTotalWeightage() {
        return totalWeightage;
    }

    public void setTotalWeightage(int totalWeightage) {
        this.totalWeightage = totalWeightage;
    }

    public Map<Long, List<String>> getCompTags() {
        return compTags;
    }

    public void setCompTags(Map<Long, List<String>> compTags) {
        this.compTags = compTags;
    }
}
