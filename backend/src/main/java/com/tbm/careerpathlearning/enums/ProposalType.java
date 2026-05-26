package com.tbm.careerpathlearning.enums;

import java.util.Arrays;
import java.util.Optional;

public enum ProposalType {
    COMPETENCY("COMPETENCY"),
    ROLE_COMPETENCY("ROLE_COMPETENCY"),
    COMP_TAG("COMP_TAG"),
    ;

    private final String proposalType;

    /**
     * Private constructor for the enum constants.
     *
     * @param proposalType The type of Proposal (e.g., "COMPETENCY", "ROLE_COMPETENCY").
     */
    ProposalType(String proposalType) {
        this.proposalType = proposalType;
    }


    /**
     * Gets the type of Proposal associated with this enum constant.
     *
     * @return The proposal type (e.g., "COMPETENCY").
     */
    public String getProposalType() {
        return proposalType;
    }

    /**
     * Static method to find an ProposalType enum constant by its type in string.
     *
     * @param proposalTypeInString The type of Proposal to search for (e.g., "COMPETENCY" or "ROLE_COMPETENCY").
     * @return An Optional containing the Proposal enum if found, otherwise an empty Optional.
     */
    public static Optional<ProposalType> fromAuthority(String proposalTypeInString) {
        return Arrays.stream(ProposalType.values()).filter(proposalType -> proposalType.getProposalType().equalsIgnoreCase(proposalTypeInString)).findFirst();
    }
}
