package com.tbm.careerpathlearning.controller;

import com.tbm.careerpathlearning.dto.StaffConflictDTO;
import com.tbm.careerpathlearning.dto.StaffDto;
import com.tbm.careerpathlearning.dto.TrainingRegistrationDTO;
import com.tbm.careerpathlearning.service.TrainingRegistrationService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/register-training")
@CrossOrigin
public class TrainingRegistrationController {

    @Autowired
    private TrainingRegistrationService trainingRegistrationService;

    @PostMapping
    public TrainingRegistrationDTO createTrainingRegistration(@RequestBody TrainingRegistrationDTO dto) {
        return trainingRegistrationService.createTrainingRegistration(dto);
    }

    @GetMapping("/staff/{trainingId}")
    public ResponseEntity<List<StaffDto>> getAllRegisteredStaff(@PathVariable Long trainingId) {
        return ResponseEntity.ok(trainingRegistrationService.getRegisteredStaffByTrainingId(trainingId));
    }

    @GetMapping("/conflicting-staff/{trainingId}")
    public ResponseEntity<List<StaffConflictDTO>> getConflictingStaff(@PathVariable Long trainingId) {
        return ResponseEntity.ok(trainingRegistrationService.getConflictingRegistrations(trainingId));
    }
}
