package com.tbm.careerpathlearning.dto;

import com.tbm.careerpathlearning.model.RoleCompetencyProposalItemId;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;
import java.util.UUID;

@AllArgsConstructor
@NoArgsConstructor
public class RoleCompetencyProposalItemDto {

    private RoleCompetencyProposalItemId id;
    private ProposalDto proposal;
    private RoleDto role;
    private StaffDto staff;
    private CompetencyProposalDto competencyProposal;
    private int weightage;
    private UUID createdBy;
    private OffsetDateTime createdAt;
    private UUID updatedBy;
    private OffsetDateTime updatedAt;

    public RoleCompetencyProposalItemId getId() {
        return id;
    }

    public void setId(RoleCompetencyProposalItemId id) {
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

    public CompetencyProposalDto getCompetencyProposal() {
        return competencyProposal;
    }

    public void setCompetencyProposal(CompetencyProposalDto competencyProposal) {
        this.competencyProposal = competencyProposal;
    }

    public int getWeightage() {
        return weightage;
    }

    public void setWeightage(int weightage) {
        this.weightage = weightage;
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
    public String toString() {
        return "[ id = " + this.id.toString() + ", "
                + "proposal = " + this.proposal.toString() + ", "
                + "role = " + this.role.toString() + ", "
                + "staff = " + this.staff.toString() + ", "
                + "competency = " + this.getCompetencyProposal().toString() + ", "
                + "weightage = " + this.weightage + ", "
                + "createdBy = " + this.createdBy.toString() + ", "
                + "createdAt = " + this.createdAt.toString() + ", "
                + "updatedBy = " + this.updatedBy.toString() + ", "
                + "updatedAt = " + this.updatedAt.toString() + ", "
                + " ]";
    }

}
