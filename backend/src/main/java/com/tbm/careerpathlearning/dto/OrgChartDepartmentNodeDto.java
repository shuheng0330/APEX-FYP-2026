package com.tbm.careerpathlearning.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class OrgChartDepartmentNodeDto {

    private Long departmentId;
    private String departmentName;
    private Map<Long, RoleDto> roles;
    private Map<Long, List<StaffDto>> staffs;
    private Map<Long, Integer> roleStaffNumberMap;


    public Long getDepartmentId() {
        return departmentId;
    }

    public void setDepartmentId(Long departmentId) {
        this.departmentId = departmentId;
    }

    public String getDepartmentName() {
        return departmentName;
    }

    public void setDepartmentName(String departmentName) {
        this.departmentName = departmentName;
    }

    public Map<Long, RoleDto> getRoles() {
        return roles;
    }

    public void setRoles(Map<Long, RoleDto> roles) {
        this.roles = roles;
    }

    public Map<Long, List<StaffDto>> getStaffs() {
        return staffs;
    }

    public void setStaffs(Map<Long, List<StaffDto>> staffs) {
        this.staffs = staffs;
    }

    public Map<Long, Integer> getRoleStaffNumberMap() {
        return roleStaffNumberMap;
    }

    public void setRoleStaffNumberMap(Map<Long, Integer> roleStaffNumberMap) {
        this.roleStaffNumberMap = roleStaffNumberMap;
    }
}
