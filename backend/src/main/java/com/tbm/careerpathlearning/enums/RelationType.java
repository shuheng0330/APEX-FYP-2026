package com.tbm.careerpathlearning.enums;

import java.util.Arrays;
import java.util.Optional;

public enum RelationType {
    ORG_CHART("ORG_CHART"),
    CAREER_PATHWAY("CAREER_PATHWAY");

    private final String type;

    /**
     * Private constructor for the enum constants.
     *
     * @param type The descriptive, human-readable type (e.g., "Role", "Department").
     */
    RelationType(String type) {
        this.type = type;
    }

    /**
     * Gets the descriptive type associated with this enum constant.
     *
     * @return The type (e.g., "Role" for ROLE).
     */
    public String getType() {
        return type;
    }

    /**
     * Static method to find an ProposalRole enum constant by its type in string.
     *
     * @param typeInString The type of relation to search for (e.g., "ORG_CHART" or "CAREER_PATHWAY").
     * @return An Optional containing the RelationType enum if found, otherwise an empty Optional.
     */
    public static Optional<RelationType> fromProficiency(String typeInString) {
        return Arrays.stream(RelationType.values()).filter(relationType -> relationType.getType().equalsIgnoreCase(typeInString)).findFirst();
    }
}