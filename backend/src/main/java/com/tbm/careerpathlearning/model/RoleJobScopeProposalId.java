package com.tbm.careerpathlearning.model;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

@Embeddable
@AllArgsConstructor
@NoArgsConstructor
public class RoleJobScopeProposalId implements Serializable {

    @Column(name = "proposal_id")
    private Long proposalId;

    @Column(name = "role_id")
    private Long roleId;

    @Column(name = "staff_id")
    private UUID staffId;

    @Column(name = "job_scope_id")
    private Long jobScopeId;

    public Long getProposalId() {
        return proposalId;
    }

    public void setProposalId(Long proposalId) {
        this.proposalId = proposalId;
    }

    public Long getRoleId() {
        return roleId;
    }

    public void setRoleId(Long roleId) {
        this.roleId = roleId;
    }

    public UUID getStaffId() {
        return staffId;
    }

    public void setStaffId(UUID staffId) {
        this.staffId = staffId;
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
        if (!(o instanceof RoleJobScopeProposalId)) return false;
        RoleJobScopeProposalId that = (RoleJobScopeProposalId) o;
        return Objects.equals(proposalId, that.proposalId) && Objects.equals(roleId, that.roleId)
                && Objects.equals(staffId, that.staffId) && Objects.equals(jobScopeId, that.jobScopeId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(proposalId, roleId, staffId);
    }

    @Override
    public String toString() {
        return "proposalId = " + this.getProposalId() + ", " +
                "roleId = " + this.getRoleId() + ", " +
                "staffId = " + this.getStaffId() + ", " +
                "jobScopeId = " + this.getJobScopeId();
    }
}

