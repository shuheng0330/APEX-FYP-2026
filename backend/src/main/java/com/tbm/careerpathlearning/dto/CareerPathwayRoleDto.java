package com.tbm.careerpathlearning.dto;

import com.tbm.careerpathlearning.model.CareerPathwayRoleId;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CareerPathwayRoleDto {

    private CareerPathwayRoleId id;
    private CareerPathwayDto careerPathway;
    private RoleDto parentRole;
    private RoleDto childRole;
    private boolean isDeleted;
    private UUID createdBy;
    private OffsetDateTime createdAt;
    private UUID updatedBy;
    private OffsetDateTime updatedAt;

    public CareerPathwayRoleId getId() {
        return id;
    }

    public void setId(CareerPathwayRoleId id) {
        this.id = id;
    }

    public CareerPathwayDto getCareerPathway() {
        return careerPathway;
    }

    public void setCareerPathway(CareerPathwayDto careerPathway) {
        this.careerPathway = careerPathway;
    }

    public RoleDto getParentRole() {
        return parentRole;
    }

    public void setParentRole(RoleDto parentRole) {
        this.parentRole = parentRole;
    }

    public RoleDto getChildRole() {
        return childRole;
    }

    public void setChildRole(RoleDto childRole) {
        this.childRole = childRole;
    }

    public boolean isDeleted() {
        return isDeleted;
    }

    public void setDeleted(boolean deleted) {
        isDeleted = deleted;
    }

    public UUID getCreatedBy() {
        return createdBy;
    }

    public void setCreatedBy(UUID createdBy) {
        this.createdBy = createdBy;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(OffsetDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public UUID getUpdatedBy() {
        return updatedBy;
    }

    public void setUpdatedBy(UUID updatedBy) {
        this.updatedBy = updatedBy;
    }

    public OffsetDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(OffsetDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
}
