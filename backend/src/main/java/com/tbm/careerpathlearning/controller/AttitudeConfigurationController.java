package com.tbm.careerpathlearning.controller;

import com.tbm.careerpathlearning.dto.*;
import com.tbm.careerpathlearning.service.AttitudeConfigurationService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import java.util.*;

@RestController @RequestMapping("/api/attitude-configurations")
@PreAuthorize("hasAuthority('CAN_MANAGE_ATTITUDE_CONFIGURATION')")
public class AttitudeConfigurationController {
    private final AttitudeConfigurationService service;
    public AttitudeConfigurationController(AttitudeConfigurationService service) {this.service=service;}
    @GetMapping public List<AttitudeConfigurationDto> list(Authentication auth) {return service.list(actor(auth));}
    @GetMapping("/current") public ResponseEntity<AttitudeConfigurationDto> current(Authentication auth) {
        var current=service.current(actor(auth));return current==null?ResponseEntity.noContent().build():ResponseEntity.ok(current);
    }
    @GetMapping("/options") public AttitudeConfigurationOptionsDto options(Authentication auth) {return service.options(actor(auth));}
    @GetMapping("/{id}") public AttitudeConfigurationDto get(@PathVariable Long id,Authentication auth) {return service.get(id,actor(auth));}
    @PostMapping public ResponseEntity<AttitudeConfigurationDto> create(@RequestBody AttitudeConfigurationRequest request,Authentication auth) {
        return ResponseEntity.status(201).body(service.create(request,actor(auth)));
    }
    @PutMapping("/{id}") public AttitudeConfigurationDto update(@PathVariable Long id,@RequestBody AttitudeConfigurationRequest request,Authentication auth) {
        return service.update(id,request,actor(auth));
    }
    @PostMapping("/{id}/copy") public ResponseEntity<AttitudeConfigurationDto> copy(@PathVariable Long id,Authentication auth) {
        return ResponseEntity.status(201).body(service.copy(id,actor(auth)));
    }
    @PostMapping("/{id}/publish") public AttitudeConfigurationDto publish(@PathVariable Long id,Authentication auth) {return service.publish(id,actor(auth));}
    @GetMapping("/periods/{periodId}") public AttitudePeriodConfigurationDto period(@PathVariable Long periodId,Authentication auth) {return service.period(periodId,actor(auth));}
    @PostMapping("/periods/{periodId}/bind") public AttitudePeriodConfigurationDto bind(@PathVariable Long periodId,@RequestBody BindingRequest request,Authentication auth) {
        return service.bindInitially(periodId,request.configurationId(),actor(auth));
    }
    public record BindingRequest(Long configurationId) {}
    private UUID actor(Authentication auth) {return UUID.fromString(auth.getName());}
}
