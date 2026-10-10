package com.tbm.careerpathlearning.controller;

import com.tbm.careerpathlearning.dto.*;
import com.tbm.careerpathlearning.service.AttitudeAssessmentService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import java.util.*;

@RestController @RequestMapping("/api/attitude-assessments")
@PreAuthorize("hasAuthority('ROLE_USER')")
public class AttitudeAssessmentController {
    private final AttitudeAssessmentService service;
    public AttitudeAssessmentController(AttitudeAssessmentService service){this.service=service;}
    @GetMapping("/periods") public List<KpiPeriodContextDto> periods(Authentication auth){return service.periods(actor(auth));}
    @GetMapping("/mine") public AttitudeAssessmentDto mine(@RequestParam Long reviewPeriodId,Authentication auth){return service.mine(reviewPeriodId,actor(auth));}
    @GetMapping("/{id}") public AttitudeAssessmentDto get(@PathVariable Long id,Authentication auth){return service.get(id,actor(auth));}
    @PostMapping public ResponseEntity<AttitudeAssessmentDto> create(@RequestBody AttitudeAssessmentRequest request,Authentication auth){return ResponseEntity.status(201).body(service.create(request,actor(auth)));}
    @PutMapping("/{id}") public AttitudeAssessmentDto update(@PathVariable Long id,@RequestBody AttitudeAssessmentRequest request,Authentication auth){return service.update(id,request,actor(auth));}
    @PostMapping("/{id}/submit") public AttitudeAssessmentDto submit(@PathVariable Long id,Authentication auth){return service.submit(id,actor(auth));}
    private UUID actor(Authentication auth){return UUID.fromString(auth.getName());}
}
