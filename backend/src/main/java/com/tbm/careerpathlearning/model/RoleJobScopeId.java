package com.tbm.careerpathlearning.model;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

import java.io.Serializable;
import java.util.Objects;

@Embeddable
public class RoleJobScopeId implements Serializable {

    @Column(name = "role_id")
    private Long roleId;

    @Column(name = "job_scope_id")
    private Long jobScopeId;

    public RoleJobScopeId() {
    }

    public RoleJobScopeId(Long roleId, Long jobScopeId) {
        this.roleId = roleId;
        this.jobScopeId = jobScopeId;
    }

    public Long getRoleId() {
        return roleId;
    }

    public void setRoleId(Long roleId) {
        this.roleId = roleId;
    }

    public Long getJobScopeId() {
        return jobScopeId;
    }

    public void setJobScopeId(Long jobScopeId) {
        this.jobScopeId = jobScopeId;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof RoleJobScopeId)) return false;
        RoleJobScopeId that = (RoleJobScopeId) o;
        return Objects.equals(roleId, that.roleId) && Objects.equals(jobScopeId, that.jobScopeId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(roleId, jobScopeId);
    }
}

