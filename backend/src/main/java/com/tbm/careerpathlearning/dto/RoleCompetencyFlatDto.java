package com.tbm.careerpathlearning.dto;

public class RoleCompetencyFlatDto {

    private String roleName;
    private Long departmentId;
    private String departmentName;
    private Long competencyId;
    private String competencyName;
    private int weightage;
    private Long roleId;

    public RoleCompetencyFlatDto() {}

    public RoleCompetencyFlatDto(Long roleId, String roleName, Long departmentId, String departmentName,
                                 Long competencyId, String competencyName, int weightage) {
        this.roleId = roleId;
        this.roleName = roleName;
        this.departmentId = departmentId;
        this.departmentName = departmentName;
        this.competencyId = competencyId;
        this.competencyName = competencyName;
        this.weightage = weightage;
    }

    public String getRoleName() {
        return roleName;
    }

    public void setRoleName(String roleName) {
        this.roleName = roleName;
    }

    public Long getRoleId() {
        return roleId;
    }

    public void setRoleId(Long roleId) {
        this.roleId = roleId;
    }

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

    public int getWeightage() {
        return weightage;
    }

    public void setWeightage(int weightage) {
        this.weightage = weightage;
    }

}

