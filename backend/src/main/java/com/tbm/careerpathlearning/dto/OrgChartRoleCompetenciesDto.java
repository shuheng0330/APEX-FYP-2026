package com.tbm.careerpathlearning.dto;

import com.tbm.careerpathlearning.model.RoleCompetencyId;

import java.time.OffsetDateTime;
import java.util.List;

public class OrgChartRoleCompetenciesDto {

    private Long roleId;
    private String roleName;
    private Long departmentId;
    private String departmentName;
    private boolean isDeleted;
    private List<CompetencyWithWeightageDto> competencies;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;

    public OrgChartRoleCompetenciesDto() {
    }

    public OrgChartRoleCompetenciesDto(Long roleId, String roleName, Long departmentId, String departmentName, boolean isDeleted,
                                       List<CompetencyWithWeightageDto> competencies) {
        this.roleId = roleId;
        this.roleName = roleName;
        this.departmentId = departmentId;
        this.departmentName = departmentName;
        this.isDeleted = isDeleted;
        this.competencies = competencies;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
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

    public boolean isDeleted() {
        return isDeleted;
    }

    public void setDeleted(boolean deleted) {
        isDeleted = deleted;
    }

    public List<CompetencyWithWeightageDto> getCompetencies() {
        return competencies;
    }

    public void setCompetencies(List<CompetencyWithWeightageDto> competencies) {
        this.competencies = competencies;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(OffsetDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public OffsetDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(OffsetDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }


}
