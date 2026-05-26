package com.tbm.careerpathlearning.dto;

import lombok.Data;

import java.util.List;

@Data
public class RoleAssignmentOverviewDto {

    private Long orgChartId;
    private String orgChartName;
    private boolean isOrgChartDeleted;
    private Long roleId;
    private String roleName;
    private boolean isRoleDeleted;
    private List<StaffDto> staffList;

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

    public boolean isOrgChartDeleted() {
        return isOrgChartDeleted;
    }

    public void setOrgChartDeleted(boolean orgChartDeleted) {
        isOrgChartDeleted = orgChartDeleted;
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

    public boolean isRoleDeleted() {
        return isRoleDeleted;
    }

    public void setRoleDeleted(boolean roleDeleted) {
        isRoleDeleted = roleDeleted;
    }

    public List<StaffDto> getStaffList() {
        return staffList;
    }

    public void setStaffList(List<StaffDto> staffList) {
        this.staffList = staffList;
    }
}
