package com.tbm.careerpathlearning.dto;

import org.apache.catalina.LifecycleState;

import java.util.List;

public class GrantAccessDto {
    private Long roleId;
    private Long orgChartId;
    private List<Long> selectedAuthorities;

    public Long getRoleId() {
        return roleId;
    }

    public void setRoleId(Long roleId) {
        this.roleId = roleId;
    }

    public Long getOrgChartId() {
        return orgChartId;
    }

    public void setOrgChartId(Long orgChartId) {
        this.orgChartId = orgChartId;
    }

    public List<Long> getSelectedAuthorities() {
        return selectedAuthorities;
    }

    public void setSelectedAuthorities(List<Long> selectedAuthorities) {
        this.selectedAuthorities = selectedAuthorities;
    }
}
