package com.tbm.careerpathlearning.dto;

import lombok.Data;

@Data
public class TrainingTargetRoleDTO {

    private Long trainingTargetRoleId;
    private TrainingProgramDTO trainingProgram;
    private RoleDto role;
    private boolean isMandatory;

}
