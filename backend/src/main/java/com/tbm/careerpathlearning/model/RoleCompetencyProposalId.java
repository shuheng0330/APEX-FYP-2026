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
public class RoleCompetencyProposalId implements Serializable {

    @Column(name = "proposal_id")
    private Long proposalId;

    @Column(name = "role_id")
    private Long roleId;

    @Column(name = "staff_id")
    private UUID staffId;

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

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof RoleCompetencyProposalId)) return false;
        RoleCompetencyProposalId that = (RoleCompetencyProposalId) o;
        return Objects.equals(proposalId, that.proposalId) && Objects.equals(roleId, that.roleId) && Objects.equals(staffId, that.staffId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(proposalId, roleId, staffId);
    }

    @Override
    public String toString() {
        return "proposalId = " + this.getProposalId() + ", " +
                "roleId = " + this.getRoleId() + ", " +
                "staffId = " + this.getStaffId();
    }
}

