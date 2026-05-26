package com.tbm.careerpathlearning.model;

import com.tbm.careerpathlearning.enums.ProposalRole;
import jakarta.persistence.*;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "proposal_participant")
public class ProposalParticipant {

    @EmbeddedId
    private ProposalParticipantId id;

    @ManyToOne(fetch = FetchType.LAZY)
    @MapsId("proposalId")
    @JoinColumn(name = "proposal_id", referencedColumnName = "id")
    private Proposal proposal;

    @ManyToOne(fetch = FetchType.LAZY)
    @MapsId("staffId")
    @JoinColumn(name = "staff_id", referencedColumnName = "id")
    private Staff staff;

    @Enumerated(EnumType.STRING)
    @Column(name = "proposal_role", nullable = false)
    private ProposalRole proposalRole;

    @Column(name = "is_initiator", nullable = false)
    private boolean isInitiator;

    @Column(name = "created_by")
    private UUID createdBy;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_by")
    private UUID updatedBy;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    public ProposalParticipant() {
    }

    public ProposalParticipantId getId() {
        return id;
    }

    public void setId(ProposalParticipantId id) {
        this.id = id;
    }

    public Proposal getProposal() {
        return proposal;
    }

    public void setProposal(Proposal proposal) {
        this.proposal = proposal;
    }

    public Staff getStaff() {
        return staff;
    }

    public void setStaff(Staff staff) {
        this.staff = staff;
    }

    public ProposalRole getProposalRole() {
        return proposalRole;
    }

    public void setProposalRole(ProposalRole proposalRole) {
        this.proposalRole = proposalRole;
    }

    public boolean isInitiator() {
        return isInitiator;
    }

    public void setInitiator(boolean initiator) {
        isInitiator = initiator;
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