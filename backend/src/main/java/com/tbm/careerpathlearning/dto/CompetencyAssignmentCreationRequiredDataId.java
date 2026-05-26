package com.tbm.careerpathlearning.dto;

import com.tbm.careerpathlearning.model.ProposalParticipantId;
import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class CompetencyAssignmentCreationRequiredDataId {

    private Long competencyId;
    private ProposalParticipantId competencyProposalId;
    private boolean isProposal;

    public Long getCompetencyId() {
        return competencyId;
    }

    public void setCompetencyId(Long competencyId) {
        this.competencyId = competencyId;
    }

    public ProposalParticipantId getCompetencyProposalId() {
        return competencyProposalId;
    }

    public void setCompetencyProposalId(ProposalParticipantId competencyProposalId) {
        this.competencyProposalId = competencyProposalId;
    }

    public boolean isProposal() {
        return isProposal;
    }

    public void setProposal(boolean proposal) {
        isProposal = proposal;
    }
}
