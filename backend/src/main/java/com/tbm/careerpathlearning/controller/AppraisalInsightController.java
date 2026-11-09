package com.tbm.careerpathlearning.controller;

import com.tbm.careerpathlearning.aspect.MetadataOnlyLogging;
import com.tbm.careerpathlearning.dto.AppraisalInsightDto;
import com.tbm.careerpathlearning.exception.BadRequestException;
import com.tbm.careerpathlearning.service.appraisalinsight.SyntheticAppraisalInsightService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.UUID;

@RestController @RequestMapping("/api/annual-appraisal-insights/synthetic")
@PreAuthorize("hasAnyAuthority('CAN_SUBMIT_ANNUAL_APPRAISAL','CAN_REVIEW_ANNUAL_APPRAISAL')")
@MetadataOnlyLogging
public class AppraisalInsightController {
    private final SyntheticAppraisalInsightService service;
    public AppraisalInsightController(SyntheticAppraisalInsightService service) { this.service = service; }
    @GetMapping("/scenarios")
    public List<AppraisalInsightDto.Scenario> scenarios(Authentication authentication) { return service.scenarios(actor(authentication)); }
    @PostMapping("/scenarios/{scenario}/generations")
    public ResponseEntity<AppraisalInsightDto> generate(@PathVariable String scenario,
            @RequestHeader("Idempotency-Key") UUID requestKey, @RequestBody(required = false) String body, Authentication authentication) {
        if (body != null && !body.isBlank()) throw new BadRequestException("AI preview accepts a fictional scenario only, not staff data");
        return ResponseEntity.accepted().body(service.generate(scenario, requestKey, actor(authentication)));
    }
    @GetMapping("/generations/{id}")
    public AppraisalInsightDto get(@PathVariable UUID id, Authentication authentication) { return service.get(id, actor(authentication)); }
    private UUID actor(Authentication authentication) { return UUID.fromString(authentication.getName()); }
}
