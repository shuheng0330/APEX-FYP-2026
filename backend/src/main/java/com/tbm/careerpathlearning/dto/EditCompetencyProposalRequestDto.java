package com.tbm.careerpathlearning.dto;

import com.tbm.careerpathlearning.model.ProposalParticipantId;

import java.util.List;

public class EditCompetencyProposalRequestDto {

    private ProposalParticipantId proposalParticipantId;
    private String competencyName;
    private String competencyDescription;
    private List<String> compTagList;

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

    public List<String> getCompTagList() {
        return compTagList;
    }

    public void setCompTagList(List<String> compTagList) {
        this.compTagList = compTagList;
    }
}
