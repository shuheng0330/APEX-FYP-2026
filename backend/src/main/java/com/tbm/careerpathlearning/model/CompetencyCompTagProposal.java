package com.tbm.careerpathlearning.model;

import jakarta.persistence.*;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "competency_comp_tag_proposal")
public class CompetencyCompTagProposal {

    @EmbeddedId
    private CompetencyCompTagProposalId id;

    @ManyToOne(fetch = FetchType.LAZY)
    @MapsId("proposalId")
    @JoinColumn(name = "proposal_id")
    private Proposal proposal;

    @ManyToOne(fetch = FetchType.LAZY)
    @MapsId("staffId")
    @JoinColumn(name = "staff_id")
    private Staff staff;

    @ManyToOne(fetch = FetchType.LAZY)
    @MapsId("compTagId")
    @JoinColumn(name = "comp_tag_id")
    private CompTag compTag;

    @Column(name = "created_by")
    private UUID createdBy;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_by")
    private UUID updatedBy;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    public CompetencyCompTagProposal() {
    }

    public CompetencyCompTagProposal(Proposal proposal, Staff staff, CompTag compTag) {
        this.proposal = proposal;
        this.staff = staff;
        this.compTag = compTag;
        this.id = new CompetencyCompTagProposalId(proposal.getId(), staff.getId(), compTag.getId());
    }

    public CompetencyCompTagProposalId getId() {
        return id;
    }

    public void setId(CompetencyCompTagProposalId id) {
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

    public CompTag getCompTag() {
        return compTag;
    }

    public void setCompTag(CompTag compTag) {
        this.compTag = compTag;
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
