package com.tbm.careerpathlearning.dto;


import com.tbm.careerpathlearning.model.RoleAuthorityId;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;
import java.util.UUID;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class RoleAuthorityDto {

    private RoleAuthorityId id;
    private RoleDto role;
    private AuthorityDto authority;
    private UUID createdBy;
    private OffsetDateTime createdAt;
    private UUID updatedBy;
    private OffsetDateTime updatedAt;

    public RoleAuthorityId getId() {
        return id;
    }

    public void setId(RoleAuthorityId id) {
        this.id = id;
    }

    public RoleDto getRole() {
        return role;
    }

    public void setRole(RoleDto role) {
        this.role = role;
    }

    public AuthorityDto getAuthority() {
        return authority;
    }

    public void setAuthority(AuthorityDto authority) {
        this.authority = authority;
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
