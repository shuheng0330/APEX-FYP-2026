package com.tbm.careerpathlearning.dto;

import com.tbm.careerpathlearning.model.RoleJobScopeProposalId;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;
import java.util.UUID;

@AllArgsConstructor
@NoArgsConstructor
public class RoleJobScopeProposalDto {

    private RoleJobScopeProposalId id;
    private ProposalDto proposal;
    private RoleDto role;
    private StaffDto staff;
    private JobScopeDto jobScope;
    private UUID createdBy;
    private OffsetDateTime createdAt;
    private UUID updatedBy;
    private OffsetDateTime updatedAt;

    public RoleJobScopeProposalId getId() {
        return id;
    }

    public void setId(RoleJobScopeProposalId id) {
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

    public JobScopeDto getJobScope() {
        return jobScope;
    }

    public void setJobScope(JobScopeDto jobScope) {
        this.jobScope = jobScope;
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
