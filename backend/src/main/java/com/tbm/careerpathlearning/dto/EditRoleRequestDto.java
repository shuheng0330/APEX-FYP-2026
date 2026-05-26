package com.tbm.careerpathlearning.dto;

import java.util.List;

public class EditRoleRequestDto {

    private Long orgChartId;
    private Long roleId;
    private String roleName;
    private String description;
    private boolean visibility;
    private List<String> jobScopeList;

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

    public boolean getVisibility() {
        return visibility;
    }

    public void setVisibility(boolean visibility) {
        this.visibility = visibility;
    }

    public List<String> getJobScopeList() {
        return jobScopeList;
    }

    public void setJobScopeList(List<String> jobScopeList) {
        this.jobScopeList = jobScopeList;
    }
}
