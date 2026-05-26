package com.tbm.careerpathlearning.model;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

import java.io.Serializable;
import java.util.Objects;

@Embeddable
public class RoleAuthorityId implements Serializable {

    @Column(name = "role_id")
    private Long roleId;

    @Column(name = "authority_id")
    private Long authorityId;

    public RoleAuthorityId() {
    }

    public RoleAuthorityId(Long roleId, Long authorityId) {
        this.roleId = roleId;
        this.authorityId = authorityId;
    }

    public Long getAuthorityId() {
        return authorityId;
    }

    public void setAuthorityId(Long authorityId) {
        this.authorityId = authorityId;
    }

    public Long getRoleId() {
        return roleId;
    }

    public void setRoleId(Long roleId) {
        this.roleId = roleId;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof RoleAuthorityId)) return false;
        RoleAuthorityId that = (RoleAuthorityId) o;
        return Objects.equals(roleId, that.roleId) && Objects.equals(authorityId, that.authorityId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(roleId, authorityId);
    }

    @Override
    public String toString() {
        return "[roleId=" + roleId + ", authorityId=" + authorityId + "]";
    }
}

