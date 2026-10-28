package com.tbm.careerpathlearning.controller;

import com.tbm.careerpathlearning.dto.TrainingCancelRequest;
import com.tbm.careerpathlearning.dto.TrainingSessionRequest;
import com.tbm.careerpathlearning.service.TrainingService;
import com.tbm.careerpathlearning.service.TrainingViewer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;

/**
 * Training sessions on the Training Calendar (FR-12). Everyone signed in sees the sessions meant for them. Staff with
 * Trainer access (CAN_MANAGE_TRAINING) and HR (CAN_ASSIGN_TRAINING) schedule sessions (FR-12-05) and see them all, as
 * does the Super Admin. Who is acting always comes from the sign-in, never from the request.
 */
@RestController
@RequestMapping("/api/trainings")
public class TrainingProgramController {

    @Autowired
    private TrainingService trainingService;

    @GetMapping
    public ResponseEntity<?> calendar(@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                      @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                                      @RequestParam(defaultValue = "false") boolean archived,
                                      Authentication authentication) {
        return ResponseEntity.ok(trainingService.calendar(from, to, archived, viewer(authentication)));
    }

    /** The viewer's upcoming sessions, for the My Learning home (FR-13-05). */
    @GetMapping("/mine")
    public ResponseEntity<?> mine(Authentication authentication) {
        return ResponseEntity.ok(trainingService.mine(viewer(authentication)));
    }

    @GetMapping("/{id}")
    public ResponseEntity<?> get(@PathVariable Long id, Authentication authentication) {
        return ResponseEntity.ok(trainingService.get(id, viewer(authentication)));
    }

    @PreAuthorize(TrainingViewers.SCHEDULERS)
    @GetMapping("/trainers")
    public ResponseEntity<?> trainers() {
        return ResponseEntity.ok(trainingService.trainers());
    }

    @PreAuthorize(TrainingViewers.SCHEDULERS)
    @PostMapping("/audience-check")
    public ResponseEntity<?> checkAudience(@RequestBody TrainingSessionRequest request) {
        return ResponseEntity.ok(trainingService.checkAudience(request));
    }

    @PreAuthorize(TrainingViewers.SCHEDULERS)
    @PostMapping
    public ResponseEntity<?> schedule(@RequestBody TrainingSessionRequest request, Authentication authentication) {
        return ResponseEntity.ok(trainingService.schedule(request, viewer(authentication)));
    }

    @PreAuthorize(TrainingViewers.SCHEDULERS)
    @PutMapping("/{id}")
    public ResponseEntity<?> update(@PathVariable Long id, @RequestBody TrainingSessionRequest request,
                                    Authentication authentication) {
        return ResponseEntity.ok(trainingService.update(id, request, viewer(authentication)));
    }

    @PreAuthorize(TrainingViewers.SCHEDULERS)
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id, Authentication authentication) {
        trainingService.delete(id, viewer(authentication));
        return ResponseEntity.noContent().build();
    }

    @PreAuthorize(TrainingViewers.SCHEDULERS)
    @PostMapping("/{id}/cancel")
    public ResponseEntity<?> cancel(@PathVariable Long id, @RequestBody TrainingCancelRequest request,
                                    Authentication authentication) {
        return ResponseEntity.ok(trainingService.cancel(id, request.getReason(), viewer(authentication)));
    }

    @PreAuthorize(TrainingViewers.SCHEDULERS)
    @PostMapping("/{id}/archive")
    public ResponseEntity<?> archive(@PathVariable Long id, Authentication authentication) {
        return ResponseEntity.ok(trainingService.archive(id, viewer(authentication)));
    }

    @PreAuthorize(TrainingViewers.SCHEDULERS)
    @PostMapping("/{id}/restore")
    public ResponseEntity<?> restore(@PathVariable Long id, Authentication authentication) {
        return ResponseEntity.ok(trainingService.restore(id, viewer(authentication)));
    }

    private TrainingViewer viewer(Authentication authentication) {
        return TrainingViewers.of(authentication);
    }
}
