package com.tbm.careerpathlearning.model;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

import java.io.Serializable;
import java.util.Objects;

@Embeddable
public class RoleCompetencyId implements Serializable {

    @Column(name = "role_id")
    private Long roleId;

    @Column(name = "comp_id")
    private Long competencyId;

    public RoleCompetencyId() {
    }

    public RoleCompetencyId(Long roleId, Long competencyId) {
        this.roleId = roleId;
        this.competencyId = competencyId;
    }

    public Long getRoleId() {
        return roleId;
    }

    public void setRoleId(Long roleId) {
        this.roleId = roleId;
    }

    public Long getCompetencyId() {
        return competencyId;
    }

    public void setCompetencyId(Long competencyId) {
        this.competencyId = competencyId;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof RoleCompetencyId)) return false;
        RoleCompetencyId that = (RoleCompetencyId) o;
        return Objects.equals(roleId, that.roleId) && Objects.equals(competencyId, that.competencyId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(roleId, competencyId);
    }
}

