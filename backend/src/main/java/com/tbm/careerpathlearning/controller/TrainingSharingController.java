package com.tbm.careerpathlearning.controller;

import com.tbm.careerpathlearning.service.TrainingSharingService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

/** Staff sharing sessions (FR-15), for all staff: the Staff Sharing page, and asking to present one. */
@RestController
@RequestMapping("/api/trainings")
public class TrainingSharingController {

    @Autowired
    private TrainingSharingService sharing;

    @GetMapping("/sharing")
    public ResponseEntity<?> board(@RequestParam(required = false) Integer year, Authentication authentication) {
        return ResponseEntity.ok(sharing.board(year, TrainingViewers.of(authentication)));
    }

    /** FR-15-06: always as the signed-in user, after attending the Product Training session. */
    @PostMapping("/{id}/request-trainer-access")
    public ResponseEntity<?> requestTrainerAccess(@PathVariable Long id, Authentication authentication) {
        return ResponseEntity.ok(sharing.requestTrainerAccess(id, TrainingViewers.of(authentication)));
    }
}
