package com.tbm.careerpathlearning.dto;

import java.util.List;

public class CreateRoleCompetencyRequestDto {

    private Long roleId;
    private List<CompetencyAssignmentDto> competencyAssignment;

    public Long getRoleId() {
        return roleId;
    }

    public void setRoleId(Long roleId) {
        this.roleId = roleId;
    }

    public List<CompetencyAssignmentDto> getCompetencyAssignment() {
        return competencyAssignment;
    }

    public void setCompetencyAssignment(List<CompetencyAssignmentDto> competencyAssignment) {
        this.competencyAssignment = competencyAssignment;
    }
}
