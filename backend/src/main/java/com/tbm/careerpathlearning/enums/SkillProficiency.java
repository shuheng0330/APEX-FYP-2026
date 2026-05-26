package com.tbm.careerpathlearning.enums;

import java.util.Arrays;
import java.util.Optional;

public enum SkillProficiency {
    BEGINNER("BEGINNER"),
    INTERMEDIATE("INTERMEDIATE"),
    ADVANCED("ADVANCED");

    private final String proficiency;

    /**
     * Private constructor for the enum constants.
     *
     * @param proficiency The descriptive, human-readable type (e.g., "BEGINNER", "INTERMEDIATE").
     */
    SkillProficiency(String proficiency) {
        this.proficiency = proficiency;
    }

    /**
     * Gets the descriptive type associated with this enum constant.
     *
     * @return The proficiency (e.g., "BEGINNER" for ROLE).
     */
    public String getProficiency() {
        return proficiency;
    }

    /**
     * Static method to find an ProposalRole enum constant by its type in string.
     *
     * @param proficiencyInString The proficiency to search for (e.g., "BEGINNER" or "INTERMEDIATE").
     * @return An Optional containing the SkillProficiency enum if found, otherwise an empty Optional.
     */
    public static Optional<SkillProficiency> fromProficiency(String proficiencyInString) {
        return Arrays.stream(SkillProficiency.values()).filter(relationType -> relationType.getProficiency().equalsIgnoreCase(proficiencyInString)).findFirst();
    }
}