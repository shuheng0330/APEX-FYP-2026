package com.tbm.careerpathlearning.model;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

@Embeddable
public class ProposalParticipantId implements Serializable {

    @Column(name = "proposal_id")
    private Long proposalId;

    @Column(name = "staff_id")
    private UUID staffId;

    public ProposalParticipantId() {
    }

    public ProposalParticipantId(Long proposalId, UUID staffId) {
        this.proposalId = proposalId;
        this.staffId = staffId;
    }

    public Long getProposalId() {
        return proposalId;
    }

    public void setProposalId(Long proposalId) {
        this.proposalId = proposalId;
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
        if (!(o instanceof ProposalParticipantId)) return false;
        ProposalParticipantId that = (ProposalParticipantId) o;
        return Objects.equals(proposalId, that.proposalId) && Objects.equals(staffId, that.staffId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(proposalId, staffId);
    }

    @Override
    public String toString() {
        return "[ proposalId = " + proposalId + ", " +
                "staffId = " + staffId
                + " ]";
    }
}

