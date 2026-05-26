package com.tbm.careerpathlearning.dto;

import com.tbm.careerpathlearning.model.ProposalParticipantId;
import lombok.Data;

import java.util.List;

@Data
public class CompetencyProposalOverviewDto {

    private ProposalParticipantId proposalParticipantId;
    private String competencyName;
    private String competencyDescription;
    private List<CompTagDto> assignedCompTags;
    private String collaboratorName;
    private String collaboratorEmail;
    private boolean isReviewer;
    private StaffDto lastUpdatedBy;

    public ProposalParticipantId getProposalParticipantId() {
        return proposalParticipantId;
    }

    public void setProposalParticipantId(ProposalParticipantId proposalParticipantId) {
        this.proposalParticipantId = proposalParticipantId;
    }

    public String getCompetencyName() {
        return competencyName;
    }

    public void setCompetencyName(String competencyName) {
        this.competencyName = competencyName;
    }

    public String getCompetencyDescription() {
        return competencyDescription;
    }

    public void setCompetencyDescription(String competencyDescription) {
        this.competencyDescription = competencyDescription;
    }

    public List<CompTagDto> getAssignedCompTags() {
        return assignedCompTags;
    }

    public void setAssignedCompTags(List<CompTagDto> assignedCompTags) {
        this.assignedCompTags = assignedCompTags;
    }

    public String getCollaboratorName() {
        return collaboratorName;
    }

    public void setCollaboratorName(String collaboratorName) {
        this.collaboratorName = collaboratorName;
    }

    public String getCollaboratorEmail() {
        return collaboratorEmail;
    }

    public void setCollaboratorEmail(String collaboratorEmail) {
        this.collaboratorEmail = collaboratorEmail;
    }

    public boolean isReviewer() {
        return isReviewer;
    }

    public void setReviewer(boolean reviewer) {
        isReviewer = reviewer;
    }

    public StaffDto getLastUpdatedBy() {
        return lastUpdatedBy;
    }

    public void setLastUpdatedBy(StaffDto lastUpdatedBy) {
        this.lastUpdatedBy = lastUpdatedBy;
    }
}
