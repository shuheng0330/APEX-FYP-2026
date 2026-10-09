package com.tbm.careerpathlearning.controller;

import com.tbm.careerpathlearning.dto.*;
import com.tbm.careerpathlearning.service.KpiAssessmentService;
import org.springframework.core.io.Resource;
import org.springframework.http.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import java.nio.charset.StandardCharsets;
import java.util.*;

@RestController @RequestMapping("/api/kpi-assessments")
@PreAuthorize("hasAuthority('ROLE_USER')")
public class KpiAssessmentController {
    private final KpiAssessmentService service;
    public KpiAssessmentController(KpiAssessmentService service){this.service=service;}
    @GetMapping("/periods") public List<KpiPeriodContextDto> periods(Authentication auth){return service.periods(actor(auth));}
    @GetMapping("/checkpoints") public List<KpiAssessmentCheckpointDto> checkpoints(@RequestParam Long reviewPeriodId,Authentication auth){return service.checkpoints(reviewPeriodId,actor(auth));}
    @GetMapping("/mine") public KpiAssessmentDto mine(@RequestParam Long checkpointId,Authentication auth){return service.mine(checkpointId,actor(auth));}
    @GetMapping("/{id}") @PreAuthorize("hasAnyAuthority('ROLE_USER','CAN_REVIEW_KPI_ASSESSMENT')")
    public KpiAssessmentDto get(@PathVariable Long id,Authentication auth){return service.get(id,actor(auth));}
    @PostMapping public ResponseEntity<KpiAssessmentDto> create(@RequestBody KpiAssessmentRequest request,Authentication auth){return ResponseEntity.status(201).body(service.create(request,actor(auth)));}
    @PutMapping("/{id}") public KpiAssessmentDto update(@PathVariable Long id,@RequestBody KpiAssessmentRequest request,Authentication auth){return service.update(id,request,actor(auth));}
    @PostMapping("/{id}/submit") public KpiAssessmentDto submit(@PathVariable Long id,Authentication auth){return service.submit(id,actor(auth));}
    @GetMapping("/reviews") @PreAuthorize("hasAuthority('CAN_REVIEW_KPI_ASSESSMENT')")
    public List<KpiAssessmentReviewDto> reviews(@RequestParam(required=false) Long reviewPeriodId,
            @RequestParam(required=false) com.tbm.careerpathlearning.enums.KpiAssessmentStatus status,Authentication auth) {
        return service.reviews(reviewPeriodId,status,actor(auth));
    }
    @PutMapping("/{id}/superior-draft") @PreAuthorize("hasAuthority('CAN_REVIEW_KPI_ASSESSMENT')")
    public KpiAssessmentDto saveSuperiorDraft(@PathVariable Long id,@RequestBody KpiSuperiorAssessmentRequest request,Authentication auth) {
        return service.saveSuperiorDraft(id,request,actor(auth));
    }
    @PostMapping("/{id}/complete-review") @PreAuthorize("hasAuthority('CAN_REVIEW_KPI_ASSESSMENT')")
    public KpiAssessmentDto completeReview(@PathVariable Long id,Authentication auth) {return service.completeReview(id,actor(auth));}
    @PostMapping(value="/items/{itemId}/evidence",consumes=MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<KpiAssessmentEvidenceDto> upload(@PathVariable Long itemId,@RequestParam MultipartFile file,Authentication auth){return ResponseEntity.status(201).body(service.upload(itemId,file,actor(auth)));}
    @GetMapping("/items/{itemId}/evidence") @PreAuthorize("hasAnyAuthority('ROLE_USER','CAN_REVIEW_KPI_ASSESSMENT')")
    public List<KpiAssessmentEvidenceDto> evidence(@PathVariable Long itemId,Authentication auth){return service.evidence(itemId,actor(auth));}
    @GetMapping("/evidence/{id}") @PreAuthorize("hasAnyAuthority('ROLE_USER','CAN_REVIEW_KPI_ASSESSMENT')")
    public ResponseEntity<Resource> download(@PathVariable Long id,Authentication auth){
        var download=service.download(id,actor(auth));
        return ResponseEntity.ok().contentType(MediaType.parseMediaType(download.metadata().getContentType()))
                .header("X-Content-Type-Options","nosniff")
                .header(HttpHeaders.CACHE_CONTROL,"private, no-store")
                .header(HttpHeaders.CONTENT_DISPOSITION,ContentDisposition.attachment()
                        .filename(download.metadata().getOriginalFilename(),StandardCharsets.UTF_8).build().toString())
                .body(download.resource());
    }
    @DeleteMapping("/evidence/{id}") public ResponseEntity<Void> delete(@PathVariable Long id,Authentication auth){service.deleteEvidence(id,actor(auth));return ResponseEntity.noContent().build();}
    private UUID actor(Authentication auth){return UUID.fromString(auth.getName());}
}
