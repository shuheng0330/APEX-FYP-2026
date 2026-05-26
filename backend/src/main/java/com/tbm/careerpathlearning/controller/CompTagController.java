package com.tbm.careerpathlearning.controller;

import com.tbm.careerpathlearning.service.CompTagService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/compTag")
public class CompTagController {

    @Autowired
    private CompTagService compTagService;

    @PreAuthorize("""
            hasAnyAuthority(
                T(com.tbm.careerpathlearning.enums.AuthorityName).CAN_MANAGE_COMPETENCY.getAuthorityName(),
                T(com.tbm.careerpathlearning.enums.AuthorityName).CAN_PROPOSE_ROLE_COMPETENCIES.getAuthorityName()
            )
            """)
    @GetMapping
    public ResponseEntity<?> getAllCompTags() {
        return ResponseEntity.ok(compTagService.findAllByIsDeletedIsFalse());
    }
}
