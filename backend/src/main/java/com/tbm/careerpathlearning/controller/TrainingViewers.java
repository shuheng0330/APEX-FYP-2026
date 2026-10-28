package com.tbm.careerpathlearning.controller;

import com.tbm.careerpathlearning.enums.AuthorityName;
import com.tbm.careerpathlearning.service.TrainingViewer;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;

import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/** Who is using the Training Calendar, taken from the sign-in and never from the request. */
final class TrainingViewers {

    /** Staff with Trainer access, and HR, schedule sessions and change who is on them (FR-12-05). */
    static final String SCHEDULERS = """
            hasAnyAuthority(
                T(com.tbm.careerpathlearning.enums.AuthorityName).CAN_MANAGE_TRAINING.getAuthorityName(),
                T(com.tbm.careerpathlearning.enums.AuthorityName).CAN_ASSIGN_TRAINING.getAuthorityName()
            )
            """;

    private TrainingViewers() {
    }

    static TrainingViewer of(Authentication authentication) {
        Set<String> granted = authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .collect(Collectors.toSet());
        return new TrainingViewer(UUID.fromString(authentication.getPrincipal().toString()),
                granted.contains(AuthorityName.CAN_MANAGE_TRAINING.getAuthorityName()),
                granted.contains(AuthorityName.CAN_ASSIGN_TRAINING.getAuthorityName()),
                granted.contains(AuthorityName.CAN_MANAGE_ACCESS_CONTROL.getAuthorityName()));
    }
}
