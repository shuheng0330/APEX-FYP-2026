package com.tbm.careerpathlearning.exception;

import com.tbm.careerpathlearning.dto.AttitudeAssignmentState;
import lombok.Getter;

@Getter
public class AttitudeAssignmentBlockedException extends RuntimeException {
    private final AttitudeAssignmentState state;
    public AttitudeAssignmentBlockedException(AttitudeAssignmentState state) {
        super("Attitude configuration update is not available");this.state=state;
    }
}
