package com.tbm.careerpathlearning.dto;

import com.tbm.careerpathlearning.enums.StaffAccountStatus;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;
import java.util.UUID;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class StaffDto {

    private UUID id;
    private String password;
    private String name;
    private String email;
    private RoleDto role;
    private CareerPathwayDto careerPathway;
    private StaffDto manager;
    private boolean isFirstLogin;
    private StaffAccountStatus accountStatus;
    private boolean isDeleted;
    private UUID createdBy;
    private OffsetDateTime createdAt;
    private UUID updatedBy;
    private OffsetDateTime updatedAt;

    public StaffDto(UUID id, String name, String email) {
        this.id = id;
        this.name = name;
        this.email = email;
    }

    public StaffDto(UUID id) {
        this.id = id;
    }

    public StaffDto(UUID id, String name, String email, RoleDto role, CareerPathwayDto careerPathway, StaffDto manager, boolean isFirstLogin, StaffAccountStatus accountStatus, boolean isDeleted, UUID createdBy, OffsetDateTime createdAt, UUID updatedBy, OffsetDateTime updatedAt) {
        this.id = id;
        this.name = name;
        this.email = email;
        this.role = role;
        this.careerPathway = careerPathway;
        this.manager = manager;
        this.isFirstLogin = isFirstLogin;
        this.accountStatus = accountStatus;
        this.isDeleted = isDeleted;
        this.createdBy = createdBy;
        this.createdAt = createdAt;
        this.updatedBy = updatedBy;
        this.updatedAt = updatedAt;
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public RoleDto getRole() {
        return role;
    }

    public void setRole(RoleDto role) {
        this.role = role;
    }

    public CareerPathwayDto getCareerPathway() {
        return careerPathway;
    }

    public void setCareerPathway(CareerPathwayDto careerPathway) {
        this.careerPathway = careerPathway;
    }

    public StaffDto getManager() {
        return manager;
    }

    public void setManager(StaffDto manager) {
        this.manager = manager;
    }

    public boolean isFirstLogin() {
        return isFirstLogin;
    }

    public void setFirstLogin(boolean firstLogin) {
        isFirstLogin = firstLogin;
    }

    public StaffAccountStatus getAccountStatus() {
        return accountStatus;
    }

    public void setAccountStatus(StaffAccountStatus accountStatus) {
        this.accountStatus = accountStatus;
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

    @Override
    public String toString() {
        return "[ id = " + this.id.toString() + ", "
                + "name = " + this.name + ", "
                + "email = " + this.email + ", "
                + "role = " + this.role + ", "
                + "careerPathway = " + this.careerPathway + ", "
                + "manager = " + this.manager + ", "
                + "isFirstLogin = " + this.isFirstLogin + ", "
                + "createdBy = " + this.createdBy + ", "
                + "createdAt = " + this.createdAt + ", "
                + "updatedBy = " + this.updatedBy + ", "
                + "updatedAt = " + this.updatedAt
                + " ]";
    }
}
