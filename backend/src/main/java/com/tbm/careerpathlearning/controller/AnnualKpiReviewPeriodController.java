package com.tbm.careerpathlearning.controller;

import com.tbm.careerpathlearning.dto.AnnualKpiReviewPeriodDto;
import com.tbm.careerpathlearning.dto.AnnualKpiReviewPeriodRequest;
import com.tbm.careerpathlearning.service.AnnualKpiReviewPeriodService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/annual-kpi-review-periods")
@PreAuthorize("hasAuthority(T(com.tbm.careerpathlearning.enums.AuthorityName).CAN_MANAGE_EVALUATION_CYCLE.getAuthorityName())")
public class AnnualKpiReviewPeriodController {
    private final AnnualKpiReviewPeriodService service;

    public AnnualKpiReviewPeriodController(AnnualKpiReviewPeriodService service) {
        this.service = service;
    }

    @PostMapping
    public ResponseEntity<AnnualKpiReviewPeriodDto> create(
            @Valid @RequestBody AnnualKpiReviewPeriodRequest request,
            @RequestParam(defaultValue = "false") boolean publish, Authentication authentication) {
        return ResponseEntity.status(201).body(service.create(request, publish, actor(authentication)));
    }

    @PutMapping("/{id}")
    public AnnualKpiReviewPeriodDto update(@PathVariable Long id,
            @Valid @RequestBody AnnualKpiReviewPeriodRequest request, Authentication authentication) {
        return service.update(id, request, actor(authentication));
    }

    @PostMapping("/{id}/publish")
    public AnnualKpiReviewPeriodDto publish(@PathVariable Long id, Authentication authentication) {
        return service.publish(id, actor(authentication));
    }

    @GetMapping
    public List<AnnualKpiReviewPeriodDto> list() {
        return service.list();
    }

    @GetMapping("/{id}")
    public AnnualKpiReviewPeriodDto get(@PathVariable Long id) {
        return service.get(id);
    }

    @GetMapping("/roles")
    public List<AnnualKpiReviewPeriodDto.RoleConfiguration> availableRoles() {
        return service.availableRoles();
    }

    @PostMapping("/preview")
    public AnnualKpiReviewPeriodDto preview(@Valid @RequestBody AnnualKpiReviewPeriodRequest request,
            @RequestParam(required = false) Long excludedPeriodId) {
        return service.preview(request, excludedPeriodId);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }

    private UUID actor(Authentication authentication) {
        return UUID.fromString(authentication.getName());
    }
}
