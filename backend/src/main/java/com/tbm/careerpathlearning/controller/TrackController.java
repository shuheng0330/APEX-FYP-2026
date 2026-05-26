package com.tbm.careerpathlearning.controller;

import com.tbm.careerpathlearning.service.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;


@RestController
@RequestMapping("/api/track")
public class TrackController {

    @Autowired
    private TrackService trackService;

    @PreAuthorize("""
            hasAnyAuthority(
                T(com.tbm.careerpathlearning.enums.AuthorityName).CAN_MANAGE_CAREER_PATHWAY.getAuthorityName()
            )
            """)
    @GetMapping()
    @Transactional
    public ResponseEntity<?> getAll(Authentication authentication) {
        return ResponseEntity.ok(trackService.findAllByIsDeletedIsFalse());
    }
}