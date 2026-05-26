package com.tbm.careerpathlearning.controller;


import com.tbm.careerpathlearning.dto.BulkInvitationDTO;
import com.tbm.careerpathlearning.dto.TrainingInvitationDTO;
import com.tbm.careerpathlearning.dto.UpdateInvitationDto;
import com.tbm.careerpathlearning.service.TrainingInvitationService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/training/invitation")
@CrossOrigin
public class TrainingInvitationController {

    @Autowired
    private TrainingInvitationService trainingInvitationService;

    @GetMapping
    public ResponseEntity<List<TrainingInvitationDTO>> getAllInvitations() {
        List<TrainingInvitationDTO> invitation = trainingInvitationService.getTrainingInvitations();
        return ResponseEntity.ok(invitation);
    }

    @PreAuthorize("""
            hasAnyAuthority(
                T(com.tbm.careerpathlearning.enums.AuthorityName).CAN_ASSIGN_TRAINING.getAuthorityName()
            )
            """)
    @PostMapping("/create")
    public ResponseEntity<List<TrainingInvitationDTO>> inviteStaff(@RequestBody BulkInvitationDTO dto, Authentication authentication) {
        String userId = authentication.getPrincipal().toString();
        UUID userUUID = UUID.fromString(userId);
        List<TrainingInvitationDTO> invitations = trainingInvitationService.inviteStaff(dto, userUUID);
        return ResponseEntity.ok(invitations);
    }

    @GetMapping("/get/{trainingId}")
    public ResponseEntity<List<TrainingInvitationDTO>> getAllInvitationsByTraining(@PathVariable Long trainingId) {
        List<TrainingInvitationDTO> invitations = trainingInvitationService.getAllInvitationsByTraining(trainingId);
        return ResponseEntity.ok(invitations);
    }

    @GetMapping("/staff/{staffId}")
    public ResponseEntity<List<TrainingInvitationDTO>> getTrainingInvitationsByStaffId(@PathVariable UUID staffId) {
        List<TrainingInvitationDTO> invitations = trainingInvitationService.getTrainingInvitationsByStaffId(staffId);
        return ResponseEntity.ok(invitations);
    }

    @PatchMapping("/{invitationId}/status")
    public ResponseEntity<TrainingInvitationDTO> updateInvitationStatus(@PathVariable Long invitationId, @RequestBody UpdateInvitationDto dto) {
        TrainingInvitationDTO updated = trainingInvitationService.updateInvitationStatus(invitationId, dto);
        return ResponseEntity.ok(updated);
    }
}
