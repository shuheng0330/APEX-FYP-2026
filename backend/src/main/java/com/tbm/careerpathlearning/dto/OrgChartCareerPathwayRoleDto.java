package com.tbm.careerpathlearning.dto;

import com.tbm.careerpathlearning.model.CareerPathwayRoleId;
import com.tbm.careerpathlearning.model.Role;
import com.tbm.careerpathlearning.model.RoleCompetencyId;

import java.util.List;

public class OrgChartCareerPathwayRoleDto {

    private Long departmentId;
    private String departmentName;
    private Long careerPathwayId;
    private String careerPathwayName;
    private List<Role> roles;
    private String description;
    private String track;
    private boolean isDeleted;

    public OrgChartCareerPathwayRoleDto() {
    }

    public OrgChartCareerPathwayRoleDto(Long departmentId, String departmentName, Long careerPathwayId, String careerPathwayName, List<Role> roles, String description, String track, boolean isDeleted) {
        this.departmentId = departmentId;
        this.departmentName = departmentName;
        this.careerPathwayId = careerPathwayId;
        this.careerPathwayName = careerPathwayName;
        this.roles = roles;
        this.isDeleted = isDeleted;
        this.description = description;
        this.track = track;
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

    public Long getCareerPathwayId() {
        return careerPathwayId;
    }

    public void setCareerPathwayId(Long careerPathwayId) {
        this.careerPathwayId = careerPathwayId;
    }

    public String getCareerPathwayName() {
        return careerPathwayName;
    }

    public void setCareerPathwayName(String careerPathwayName) {
        this.careerPathwayName = careerPathwayName;
    }

    public List<Role> getRoles() {
        return roles;
    }

    public void setRoles(List<Role> roles) {
        this.roles = roles;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getTrack() {
        return track;
    }

    public void setTrack(String track) {
        this.track = track;
    }

    public boolean isDeleted() {
        return isDeleted;
    }

    public void setDeleted(boolean deleted) {
        isDeleted = deleted;
    }
}
