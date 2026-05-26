package com.tbm.careerpathlearning.dto;


import com.tbm.careerpathlearning.enums.ProposalRole;
import com.tbm.careerpathlearning.model.ProposalParticipantId;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;
import java.util.UUID;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class ProposalParticipantDto {

    private ProposalParticipantId id;
    private ProposalDto proposal;
    private StaffDto staff;
    private ProposalRole proposalRole;
    private boolean isInitiator;
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
