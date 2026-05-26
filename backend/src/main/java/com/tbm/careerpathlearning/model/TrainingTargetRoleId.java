package com.tbm.careerpathlearning.model;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

@Embeddable
@Data
@NoArgsConstructor
@AllArgsConstructor
public class TrainingTargetRoleId implements Serializable {

    @Column(name = "training_id")
    private Long trainingId;

    @Column(name = "role_id")
    private Long roleId;
}
