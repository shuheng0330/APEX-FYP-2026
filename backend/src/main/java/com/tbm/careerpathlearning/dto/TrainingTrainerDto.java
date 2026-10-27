package com.tbm.careerpathlearning.dto;

import lombok.Data;

import java.util.UUID;

/** Someone who can run or present a session: a staff member with Trainer access, or HR (FR-12-05, FR-15-05). */
@Data
public class TrainingTrainerDto {
    private UUID id;
    private String name;
    private String roleName;
    private String departmentName;
}
