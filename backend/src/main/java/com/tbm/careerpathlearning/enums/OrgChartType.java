package com.tbm.careerpathlearning.enums;

import java.util.Arrays;
import java.util.Optional;

public enum OrgChartType {
    P("P", "Person"), D("D", "Department");

    private final String key;
    private final String type;

    /**
     * Private constructor for the enum constants.
     *
     * @param key  The short, often single-character, identifier (e.g., "P", "D").
     * @param type The descriptive, human-readable type (e.g., "Person", "Department").
     */
    OrgChartType(String key, String type) {
        this.key = key;
        this.type = type;
    }

    /**
     * Gets the short key associated with this enum constant.
     *
     * @return The key (e.g., "P" for ROLE).
     */
    public String getKey() {
        return key;
    }

    /**
     * Gets the descriptive type associated with this enum constant.
     *
     * @return The type (e.g., "Person" for ROLE).
     */
    public String getType() {
        return type;
    }

    /**
     * Static method to find an OrgChartType enum constant by its key.
     *
     * @param key The key to search for (e.g., "P" or "D").
     * @return An Optional containing the OrgChartType enum if found, otherwise an empty Optional.
     */
    public static Optional<OrgChartType> fromKey(String key) {
        return Arrays.stream(OrgChartType.values()).filter(orgChartType -> orgChartType.getKey().equalsIgnoreCase(key)).findFirst();
    }

    /**
     * Static method to find an OrgChartType enum constant by its type description.
     *
     * @param type The type description to search for (e.g., "Person" or "Department").
     * @return An Optional containing the OrgChartType enum if found, otherwise an empty Optional.
     */
    public static Optional<OrgChartType> fromType(String type) {
        return Arrays.stream(OrgChartType.values()).filter(orgChartType -> orgChartType.getType().equalsIgnoreCase(type)).findFirst();
    }
}