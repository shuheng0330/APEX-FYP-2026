package com.tbm.careerpathlearning.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class ProposeCompetencyAssignmentMap {

    private CompetencyAssignmentCreationRequiredDataId id;
    private CompetencyDto competency;
    private CompetencyProposalDto competencyProposal;
    private int weightage;

    public CompetencyAssignmentCreationRequiredDataId getId() {
        return id;
    }

    public void setId(CompetencyAssignmentCreationRequiredDataId id) {
        this.id = id;
    }

    public CompetencyDto getCompetency() {
        return competency;
    }

    public void setCompetency(CompetencyDto competency) {
        this.competency = competency;
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
}