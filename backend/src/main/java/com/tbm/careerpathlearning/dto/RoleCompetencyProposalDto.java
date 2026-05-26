package com.tbm.careerpathlearning.dto;

import com.tbm.careerpathlearning.model.RoleCompetencyProposalId;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;
import java.util.UUID;

@AllArgsConstructor
@NoArgsConstructor
public class RoleCompetencyProposalDto {

    private RoleCompetencyProposalId id;
    private ProposalDto proposal;
    private RoleDto role;
    private StaffDto staff;
    private String description;
    private UUID createdBy;
    private OffsetDateTime createdAt;
    private UUID updatedBy;
    private OffsetDateTime updatedAt;

    public RoleCompetencyProposalId getId() {
        return id;
    }

    public void setId(RoleCompetencyProposalId id) {
        this.id = id;
    }

    public ProposalDto getProposal() {
        return proposal;
    }

    public void setProposal(ProposalDto proposal) {
        this.proposal = proposal;
    }

    public RoleDto getRole() {
        return role;
    }

    public void setRole(RoleDto role) {
        this.role = role;
    }

    public StaffDto getStaff() {
        return staff;
    }

    public void setStaff(StaffDto staff) {
        this.staff = staff;
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
}
