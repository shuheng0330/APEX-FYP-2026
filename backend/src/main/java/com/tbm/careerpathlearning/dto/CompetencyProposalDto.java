package com.tbm.careerpathlearning.dto;

import com.tbm.careerpathlearning.model.ProposalParticipantId;

import java.time.OffsetDateTime;
import java.util.Objects;
import java.util.UUID;

public class CompetencyProposalDto {

    private ProposalParticipantId id;
    private ProposalDto proposal;
    private StaffDto staff;
    private String name;
    private String description;
    private UUID createdBy;
    private OffsetDateTime createdAt;
    private UUID updatedBy;
    private OffsetDateTime updatedAt;

    public ProposalParticipantId getId() {
        return id;
    }

    public void setId(ProposalParticipantId id) {
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

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
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

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof CompetencyProposalDto)) return false;
        CompetencyProposalDto that = (CompetencyProposalDto) o;
        return Objects.equals(id.getProposalId(), that.id.getProposalId())
                && Objects.equals(id.getStaffId(), that.id.getStaffId());
    }

    @Override
    public int hashCode() {
        return Objects.hash(id.getProposalId(), id.getStaffId());
    }

    @Override
    public String toString() {
        return "[ ProposalParticipantId = " + this.id.toString() + ", " +
                "proposal = " + this.proposal.toString() + ", " +
                "staff = " + this.staff.toString() + ", " +
                "name = " + this.getName() + ", " +
                "description = " + this.getDescription() + ", " +
                "createdBy = " + this.createdBy + ", " +
                "createdAt = " + this.createdAt + ", " +
                "updatedBy = " + this.updatedBy + ", " +
                "updatedAt = " + this.updatedAt +
                " ]";
    }
}
