package com.tbm.careerpathlearning.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

@Embeddable
@AllArgsConstructor
@NoArgsConstructor
public class RoleCompetencyProposalItemId implements Serializable {

    @Column(name = "proposal_id")
    private Long proposalId;

    @Column(name = "role_id")
    private Long roleId;

    @Column(name = "staff_id")
    private UUID staffId;

    @Embedded
    @AttributeOverrides({
            @AttributeOverride(name = "proposalId", column = @Column(name = "competency_proposal_id")),
            @AttributeOverride(name = "staffId", column = @Column(name = "competency_proposal_staff_id"))
    })
    private ProposalParticipantId competencyProposalId;

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

    public ProposalParticipantId getCompetencyProposalId() {
        return competencyProposalId;
    }

    public void setCompetencyProposalId(ProposalParticipantId competencyProposalId) {
        this.competencyProposalId = competencyProposalId;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof RoleCompetencyProposalItemId)) return false;
        RoleCompetencyProposalItemId that = (RoleCompetencyProposalItemId) o;
        return Objects.equals(proposalId, that.proposalId) && Objects.equals(roleId, that.roleId)
                && Objects.equals(staffId, that.staffId) && Objects.equals(competencyProposalId, that.competencyProposalId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(proposalId, roleId, staffId);
    }

    @Override
    public String toString() {
        return "[ proposalId = " + this.getProposalId() + ", " +
                "roleId = " + this.getRoleId() + ", " +
                "staffId = " + this.getStaffId() + ", " +
                "competencyProposalId = " + this.getCompetencyProposalId().getProposalId() + ", " +
                "competencyProposalStaffId = " + this.getCompetencyProposalId().getStaffId()
                + " ]";
    }
}

