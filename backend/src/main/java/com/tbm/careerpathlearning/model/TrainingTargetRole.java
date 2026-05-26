package com.tbm.careerpathlearning.model;

import jakarta.persistence.*;
import lombok.Data;

@Entity
@Table(name = "training_target_role")
@Data
public class TrainingTargetRole {

    @EmbeddedId
    private TrainingTargetRoleId trainingTargetRoleId = new TrainingTargetRoleId();

    @ManyToOne
    @MapsId("trainingId")
    @JoinColumn(name = "training_id")
    private TrainingProgram training;

    @ManyToOne
    @MapsId("roleId")
    @JoinColumn(name = "role_id")
    private Role role;

    // New field
    @Column(name = "is_mandatory", nullable = false)
    private boolean isMandatory;
}
