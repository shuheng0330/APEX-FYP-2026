package com.tbm.careerpathlearning.controller;

import com.tbm.careerpathlearning.service.TrainingRecordService;
import com.tbm.careerpathlearning.service.TrainingViewer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/** Training records (FR-18): one's own, or another person's for their manager, HR and the Super Admin. */
@RestController
@RequestMapping("/api/training-records")
public class TrainingRecordController {

    @Autowired
    private TrainingRecordService records;

    @GetMapping("/me")
    public ResponseEntity<?> mine(Authentication authentication) {
        TrainingViewer viewer = TrainingViewers.of(authentication);
        return ResponseEntity.ok(records.record(viewer.userId(), viewer));
    }

    @GetMapping("/{staffId}")
    public ResponseEntity<?> of(@PathVariable UUID staffId, Authentication authentication) {
        return ResponseEntity.ok(records.record(staffId, TrainingViewers.of(authentication)));
    }
}
