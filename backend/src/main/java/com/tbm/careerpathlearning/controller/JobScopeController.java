package com.tbm.careerpathlearning.controller;

import com.tbm.careerpathlearning.service.JobScopeService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/jobScope")
public class JobScopeController {

    @Autowired
    private JobScopeService jobScopeService;

    @PreAuthorize("""
            hasAnyAuthority(
                T(com.tbm.careerpathlearning.enums.AuthorityName).CAN_MANAGE_COMPETENCY.getAuthorityName(),
                T(com.tbm.careerpathlearning.enums.AuthorityName).CAN_PROPOSE_ROLE_COMPETENCIES.getAuthorityName()
            )
            """)
    @GetMapping
    public ResponseEntity<?> getAllJobScopes() {
        return ResponseEntity.ok(jobScopeService.findAllByIsDeletedIsFalse());
    }
}
