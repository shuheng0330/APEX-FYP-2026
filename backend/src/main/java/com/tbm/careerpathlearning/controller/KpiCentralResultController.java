package com.tbm.careerpathlearning.controller;

import com.tbm.careerpathlearning.dto.*;
import com.tbm.careerpathlearning.service.KpiCentralResultService;
import org.springframework.http.*;
import org.springframework.core.io.Resource;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import java.util.*;
import java.nio.charset.StandardCharsets;

@RestController @RequestMapping("/api/kpi-central-results")
@PreAuthorize("hasAnyAuthority('CAN_MANAGE_COMPANY_KPI','CAN_ENTER_COMPANY_KPI_RESULTS','CAN_MANAGE_DEPARTMENT_KPI')")
public class KpiCentralResultController {
    private final KpiCentralResultService service;
    public KpiCentralResultController(KpiCentralResultService service){this.service=service;}
    @GetMapping("/periods") public List<KpiPeriodContextDto> periods(Authentication auth){return service.periods(actor(auth));}
    @GetMapping("/options") public List<KpiCentralResultOptionDto> options(@RequestParam Long reviewPeriodId,Authentication auth){return service.options(reviewPeriodId,actor(auth));}
    @GetMapping("/checkpoints") public List<AnnualKpiReviewPeriodDto.Checkpoint> checkpoints(@RequestParam Long planVersionId,Authentication auth){return service.checkpoints(planVersionId,actor(auth));}
    @GetMapping public List<KpiCentralResultDto> list(@RequestParam Long reviewPeriodId,Authentication auth){return service.list(reviewPeriodId,actor(auth));}
    @GetMapping("/{id}") public KpiCentralResultDto get(@PathVariable Long id,Authentication auth){return service.get(id,actor(auth));}
    @PostMapping public ResponseEntity<KpiCentralResultDto> create(@RequestBody KpiCentralResultRequest request,Authentication auth){return ResponseEntity.status(201).body(service.create(request,actor(auth)));}
    @PutMapping("/{id}") public KpiCentralResultDto update(@PathVariable Long id,@RequestBody KpiCentralResultRequest request,Authentication auth){return service.update(id,request,actor(auth));}
    @PostMapping("/{id}/publish") public KpiCentralResultDto publish(@PathVariable Long id,@RequestBody KpiCentralResultDecisionRequest request,Authentication auth){return service.publish(id,request,actor(auth));}
    @PostMapping("/{id}/corrections") public ResponseEntity<KpiCentralResultDto> correct(@PathVariable Long id,@RequestBody KpiCentralResultDecisionRequest request,Authentication auth){return ResponseEntity.status(201).body(service.correct(id,request,actor(auth)));}
    @GetMapping("/{id}/history") public List<KpiCentralResultDto> history(@PathVariable Long id,Authentication auth){return service.history(id,actor(auth));}
    @PostMapping(value="/items/{itemId}/evidence",consumes=MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<KpiCentralResultEvidenceMutationDto> upload(@PathVariable Long itemId,@RequestParam Long expectedLockVersion,
            @RequestParam MultipartFile file,Authentication auth){return ResponseEntity.status(201).body(service.upload(itemId,expectedLockVersion,file,actor(auth)));}
    @GetMapping("/items/{itemId}/evidence") public List<KpiCentralResultEvidenceDto> evidence(@PathVariable Long itemId,Authentication auth){return service.evidence(itemId,actor(auth));}
    @GetMapping("/evidence/{id}") public ResponseEntity<Resource> download(@PathVariable Long id,Authentication auth){
        var download=service.download(id,actor(auth));
        return ResponseEntity.ok().contentType(MediaType.parseMediaType(download.metadata().getContentType()))
                .header("X-Content-Type-Options","nosniff").header(HttpHeaders.CACHE_CONTROL,"private, no-store")
                .header(HttpHeaders.CONTENT_DISPOSITION,ContentDisposition.attachment()
                        .filename(download.metadata().getOriginalFilename(),StandardCharsets.UTF_8).build().toString())
                .body(download.resource());
    }
    @DeleteMapping("/evidence/{id}") public KpiCentralResultEvidenceMutationDto deleteEvidence(@PathVariable Long id,
            @RequestParam Long expectedLockVersion,Authentication auth){return service.deleteEvidence(id,expectedLockVersion,actor(auth));}
    @GetMapping("/periods/{periodId}/delegates") @PreAuthorize("hasAuthority('CAN_MANAGE_COMPANY_KPI')")
    public List<CompanyKpiResultDelegateDto> delegates(@PathVariable Long periodId,Authentication auth){return service.delegates(periodId,actor(auth));}
    public record DelegateRequest(UUID staffId) {}
    @PostMapping("/periods/{periodId}/delegates") @PreAuthorize("hasAuthority('CAN_MANAGE_COMPANY_KPI')")
    public ResponseEntity<CompanyKpiResultDelegateDto> assignDelegate(@PathVariable Long periodId,@RequestBody DelegateRequest request,Authentication auth){
        return ResponseEntity.status(201).body(service.assignDelegate(periodId,request.staffId(),actor(auth)));
    }
    @PostMapping("/periods/{periodId}/delegates/{delegateId}/revoke") @PreAuthorize("hasAuthority('CAN_MANAGE_COMPANY_KPI')")
    public CompanyKpiResultDelegateDto revokeDelegate(@PathVariable Long periodId,@PathVariable Long delegateId,Authentication auth){return service.revokeDelegate(periodId,delegateId,actor(auth));}
    private UUID actor(Authentication auth){return UUID.fromString(auth.getName());}
}
