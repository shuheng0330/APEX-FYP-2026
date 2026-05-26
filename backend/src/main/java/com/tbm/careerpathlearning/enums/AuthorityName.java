package com.tbm.careerpathlearning.enums;

import java.util.Arrays;
import java.util.Optional;

public enum AuthorityName {
    ROLE_USER("ROLE_USER"),
    CAN_VIEW_ACCESS_CONTROL("CAN_VIEW_ACCESS_CONTROL"),
    CAN_MANAGE_ACCESS_CONTROL("CAN_MANAGE_ACCESS_CONTROL"),
    CAN_VIEW_STAFF("CAN_VIEW_STAFF"),
    CAN_MANAGE_STAFF("CAN_MANAGE_STAFF"),
    CAN_VIEW_INVISIBLE_ROLE("CAN_VIEW_INVISIBLE_ROLE"),
    CAN_MANAGE_ROLE("CAN_MANAGE_ROLE"),
    CAN_MANAGE_COMPETENCY("CAN_MANAGE_COMPETENCY"),
    CAN_PROPOSE_ROLE_COMPETENCIES("CAN_PROPOSE_ROLE_COMPETENCIES"),
    CAN_MANAGE_CAREER_PATHWAY("CAN_MANAGE_CAREER_PATHWAY"),
    CAN_MANAGE_ORG_CHART("CAN_MANAGE_ORG_CHART"),
    CAN_MANAGE_TRAINING("CAN_MANAGE_TRAINING"),
    CAN_ASSIGN_TRAINING("CAN_ASSIGN_TRAINING"),
    CAN_MANAGE_LEARNING_MATERIAL("CAN_MANAGE_LEARNING_MATERIAL"),
    CAN_MANAGE_EVALUATION("CAN_MANAGE_EVALUATION"),
    CAN_MANAGE_EVALUATION_CYCLE("CAN_MANAGE_EVALUATION_CYCLE")
    ;

    private final String authorityName;

    /**
     * Private constructor for the enum constants.
     *
     * @param authorityName The name of GrantedAuthority (e.g., "ROLE_USER", "ROLE_ROLE_CREATOR").
     */
    AuthorityName(String authorityName) {
        this.authorityName = authorityName;
    }


    /**
     * Gets the name of GrantedAuthority associated with this enum constant.
     *
     * @return The authority name (e.g., "ROLE_USER").
     */
    public String getAuthorityName() {
        return authorityName;
    }

    /**
     * Static method to find an Authority enum constant by its authority name.
     *
     * @param authorityNameInString The name of GrantedAuthority to search for (e.g., "ROLE_USER" or "ROLE_ADMIN").
     * @return An Optional containing the Authority enum if found, otherwise an empty Optional.
     */
    public static Optional<AuthorityName> fromAuthority(String authorityNameInString) {
        return Arrays.stream(AuthorityName.values()).filter(authorityName -> authorityName.getAuthorityName().equalsIgnoreCase(authorityNameInString)).findFirst();
    }
}
