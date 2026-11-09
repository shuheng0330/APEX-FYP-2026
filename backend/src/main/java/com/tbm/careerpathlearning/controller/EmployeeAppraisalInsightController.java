package com.tbm.careerpathlearning.controller;

import com.tbm.careerpathlearning.aspect.MetadataOnlyLogging;
import com.tbm.careerpathlearning.dto.EmployeeInsightDto.*;
import com.tbm.careerpathlearning.exception.*;
import com.tbm.careerpathlearning.service.appraisalinsight.*;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import java.time.Duration;
import java.util.UUID;

@RestController @RequestMapping("/api/annual-appraisals") @MetadataOnlyLogging
public class EmployeeAppraisalInsightController {
    private final EmployeeAppraisalInsightService service;
    private final EmployeeInsightSubmissionService submissions;
    public EmployeeAppraisalInsightController(EmployeeAppraisalInsightService service,EmployeeInsightSubmissionService submissions) {
        this.service=service;this.submissions=submissions;
    }
    @GetMapping("/participants/{participantId}/ai-insights")
    @PreAuthorize("hasAuthority('CAN_SUBMIT_ANNUAL_APPRAISAL')")
    public State state(@PathVariable Long participantId,Authentication auth) { return service.state(participantId,actor(auth)); }
    @PostMapping("/participants/{participantId}/ai-insights")
    @PreAuthorize("hasAuthority('CAN_SUBMIT_ANNUAL_APPRAISAL')")
    public ResponseEntity<Result> generate(@PathVariable Long participantId,@RequestHeader("Idempotency-Key") UUID key,
            @RequestBody(required=false) String body,Authentication auth) {
        emptyBody(body);return response(service.generate(participantId,key,actor(auth),false));
    }
    @PostMapping("/participants/{participantId}/ai-insights/regenerate")
    @PreAuthorize("hasAuthority('CAN_SUBMIT_ANNUAL_APPRAISAL')")
    public ResponseEntity<Result> regenerate(@PathVariable Long participantId,@RequestHeader("Idempotency-Key") UUID key,
            @RequestBody(required=false) String body,Authentication auth) {
        emptyBody(body);return response(service.generate(participantId,key,actor(auth),true));
    }
    @GetMapping("/participants/{participantId}/ai-insights/generations/{generationId}")
    @PreAuthorize("hasAuthority('CAN_SUBMIT_ANNUAL_APPRAISAL')")
    public Result get(@PathVariable Long participantId,@PathVariable UUID generationId,Authentication auth) {
        return service.read(participantId,generationId,actor(auth));
    }
    @GetMapping("/{appraisalId}/ai-insight")
    @PreAuthorize("hasAnyAuthority('CAN_SUBMIT_ANNUAL_APPRAISAL','CAN_REVIEW_ANNUAL_APPRAISAL')")
    public Submitted submitted(@PathVariable Long appraisalId,@RequestParam(required=false) Long submissionActivityId,Authentication auth) {
        return submissions.get(appraisalId,submissionActivityId,actor(auth));
    }
    @ExceptionHandler(EmployeeInsightLimitException.class)
    public ResponseEntity<LimitError> limited(EmployeeInsightLimitException failure) {
        var detail=failure.detail();
        long seconds=detail.retryAt()==null?60:Math.max(1,Duration.between(detail.limits().serverTime(),detail.retryAt()).toSeconds());
        return ResponseEntity.status(429).header("Retry-After",Long.toString(seconds)).body(detail);
    }
    private ResponseEntity<Result> response(Result result) {
        return ResponseEntity.status("REUSED".equals(result.disposition())?200:202).body(result);
    }
    private void emptyBody(String body) {
        if (body!=null && !body.isBlank()) throw new BadRequestException("AI insight requests accept no performance data or prompt body");
    }
    private UUID actor(Authentication auth) { return UUID.fromString(auth.getName()); }
}
