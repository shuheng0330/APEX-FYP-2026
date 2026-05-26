package com.tbm.careerpathlearning.dto;

import com.tbm.careerpathlearning.model.*;

import java.time.OffsetDateTime;
import java.util.UUID;

public class CompetencyCompTagProposalDto {

    private CompetencyCompTagProposalId id;
    private ProposalDto proposal;
    private StaffDto staff;
    private CompTagDto compTag;
    private UUID createdBy;
    private OffsetDateTime createdAt;
    private UUID updatedBy;
    private OffsetDateTime updatedAt;

    public CompetencyCompTagProposalId getId() {
        return id;
    }

    public void setId(CompetencyCompTagProposalId id) {
        this.id = id;
    }

    public ProposalDto getProposal() {
        return proposal;
    }

    public void setProposal(ProposalDto proposal) {
        this.proposal = proposal;
    }

    public StaffDto getStaff() {
        return staff;
    }

    public void setStaff(StaffDto staff) {
        this.staff = staff;
    }

    public CompTagDto getCompTag() {
        return compTag;
    }

    public void setCompTag(CompTagDto compTag) {
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
