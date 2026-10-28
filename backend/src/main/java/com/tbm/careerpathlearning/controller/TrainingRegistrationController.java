package com.tbm.careerpathlearning.controller;

import com.tbm.careerpathlearning.dto.TrainingAddPersonRequest;
import com.tbm.careerpathlearning.dto.TrainingDeclineRequest;
import com.tbm.careerpathlearning.dto.TrainingRegistrationRequest;
import com.tbm.careerpathlearning.service.TrainingRegistrationService;
import com.tbm.careerpathlearning.service.TrainingService;
import com.tbm.careerpathlearning.service.TrainingViewer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

/**
 * Registering for training sessions (FR-13) and answering invitations (FR-12-14). Staff act only for themselves:
 * nobody is named in the request. The trainer and HR change who is on a session through /people. Each action answers
 * with the session as the viewer now sees it, or with the updated list of people.
 */
@RestController
@RequestMapping("/api/trainings")
public class TrainingRegistrationController {

    @Autowired
    private TrainingRegistrationService registrations;

    @Autowired
    private TrainingService trainingService;

    @GetMapping("/{id}/registration-form")
    public ResponseEntity<?> form(@PathVariable Long id, Authentication authentication) {
        return ResponseEntity.ok(registrations.form(id, TrainingViewers.of(authentication)));
    }

    @PostMapping("/{id}/register")
    public ResponseEntity<?> register(@PathVariable Long id, @RequestBody(required = false) TrainingRegistrationRequest request,
                                      Authentication authentication) {
        TrainingViewer viewer = TrainingViewers.of(authentication);
        registrations.register(id, request == null ? new TrainingRegistrationRequest() : request, viewer);
        return ResponseEntity.ok(trainingService.get(id, viewer));
    }

    @PostMapping("/{id}/cancel-registration")
    public ResponseEntity<?> cancelRegistration(@PathVariable Long id, Authentication authentication) {
        TrainingViewer viewer = TrainingViewers.of(authentication);
        registrations.cancelMine(id, viewer);
        return ResponseEntity.ok(trainingService.get(id, viewer));
    }

    @PostMapping("/{id}/accept-offer")
    public ResponseEntity<?> acceptOffer(@PathVariable Long id, Authentication authentication) {
        TrainingViewer viewer = TrainingViewers.of(authentication);
        registrations.acceptOffer(id, viewer);
        return ResponseEntity.ok(trainingService.get(id, viewer));
    }

    @PostMapping("/{id}/decline-offer")
    public ResponseEntity<?> declineOffer(@PathVariable Long id, Authentication authentication) {
        TrainingViewer viewer = TrainingViewers.of(authentication);
        registrations.declineOffer(id, viewer);
        return ResponseEntity.ok(trainingService.get(id, viewer));
    }

    @PostMapping("/{id}/decline-invitation")
    public ResponseEntity<?> declineInvitation(@PathVariable Long id, @RequestBody TrainingDeclineRequest request,
                                               Authentication authentication) {
        TrainingViewer viewer = TrainingViewers.of(authentication);
        registrations.declineInvitation(id, request.getReason(), viewer);
        return ResponseEntity.ok(trainingService.get(id, viewer));
    }

    @GetMapping("/{id}/people")
    public ResponseEntity<?> people(@PathVariable Long id, Authentication authentication) {
        return ResponseEntity.ok(registrations.people(id, TrainingViewers.of(authentication)));
    }

    @PreAuthorize(TrainingViewers.SCHEDULERS)
    @PostMapping("/{id}/people")
    public ResponseEntity<?> add(@PathVariable Long id, @RequestBody TrainingAddPersonRequest request,
                                 Authentication authentication) {
        TrainingViewer viewer = TrainingViewers.of(authentication);
        registrations.add(id, request.getStaffId(), viewer);
        return ResponseEntity.ok(registrations.people(id, viewer));
    }

    @PreAuthorize(TrainingViewers.SCHEDULERS)
    @PostMapping("/{id}/people/{registrationId}/remove")
    public ResponseEntity<?> remove(@PathVariable Long id, @PathVariable Long registrationId, Authentication authentication) {
        TrainingViewer viewer = TrainingViewers.of(authentication);
        registrations.remove(id, registrationId, viewer);
        return ResponseEntity.ok(registrations.people(id, viewer));
    }

    @PreAuthorize(TrainingViewers.SCHEDULERS)
    @PostMapping("/{id}/people/{registrationId}/offer")
    public ResponseEntity<?> offer(@PathVariable Long id, @PathVariable Long registrationId, Authentication authentication) {
        TrainingViewer viewer = TrainingViewers.of(authentication);
        registrations.offerSeat(id, registrationId, viewer);
        return ResponseEntity.ok(registrations.people(id, viewer));
    }
}
