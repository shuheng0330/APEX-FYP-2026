package com.tbm.careerpathlearning.controller;

import com.tbm.careerpathlearning.service.TrainingTemplateService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

/**
 * Saved session set-ups (FR-12-08 to FR-12-10). A template is saved from the scheduling form, with the session, and
 * counted as used when a session is scheduled from it.
 */
@RestController
@RequestMapping("/api/trainings/templates")
@PreAuthorize(TrainingViewers.SCHEDULERS)
public class TrainingTemplateController {

    @Autowired
    private TrainingTemplateService templates;

    @GetMapping
    public ResponseEntity<?> list(Authentication authentication) {
        return ResponseEntity.ok(templates.list(TrainingViewers.of(authentication)));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> delete(@PathVariable Long id, Authentication authentication) {
        templates.delete(id, TrainingViewers.of(authentication));
        return ResponseEntity.noContent().build();
    }
}
