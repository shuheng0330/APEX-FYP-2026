package com.tbm.careerpathlearning.controller;

import com.tbm.careerpathlearning.aspect.MetadataOnlyLogging;
import com.tbm.careerpathlearning.dto.*;
import com.tbm.careerpathlearning.exception.AttitudeAssignmentBlockedException;
import com.tbm.careerpathlearning.service.AttitudeAssignmentService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import java.util.UUID;

@RestController @MetadataOnlyLogging
@RequestMapping("/api/attitude-configurations/periods/{periodId}/assignment")
@PreAuthorize("hasAuthority('CAN_MANAGE_ATTITUDE_CONFIGURATION')")
public class AttitudeAssignmentController {
    private final AttitudeAssignmentService service;
    public AttitudeAssignmentController(AttitudeAssignmentService service) {this.service=service;}
    @GetMapping public AttitudeAssignmentState state(@PathVariable Long periodId,Authentication auth) {
        return service.state(periodId,UUID.fromString(auth.getName()));
    }
    @PostMapping public AttitudeAssignmentState update(@PathVariable Long periodId,
            @Valid @RequestBody AttitudeAssignmentRequest request,Authentication auth) {
        return service.update(periodId,request,UUID.fromString(auth.getName()));
    }
    @ExceptionHandler(AttitudeAssignmentBlockedException.class)
    public ResponseEntity<AttitudeAssignmentState> blocked(AttitudeAssignmentBlockedException error) {
        return ResponseEntity.status(409).body(error.getState());
    }
}
