package com.tbm.careerpathlearning.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class CompetencyAssignmentCreationRequiredDataDto {

    private CompetencyAssignmentCreationRequiredDataId id;
    private String name;
    private String proposerEmail;

    public CompetencyAssignmentCreationRequiredDataId getId() {
        return id;
    }

    public void setId(CompetencyAssignmentCreationRequiredDataId id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getProposerEmail() {
        return proposerEmail;
    }

    public void setProposerEmail(String proposerEmail) {
        this.proposerEmail = proposerEmail;
    }
}
