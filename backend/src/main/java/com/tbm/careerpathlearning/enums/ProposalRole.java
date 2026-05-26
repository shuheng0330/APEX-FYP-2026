package com.tbm.careerpathlearning.enums;

import java.util.Arrays;
import java.util.Optional;

public enum ProposalRole {
    PROPOSER("PROPOSER"),
    REVIEWER("REVIEWER"),
    CONSUMER("CONSUMER");

    private final String proposalRole;

    /**
     * Private constructor for the enum constants.
     *
     * @param proposalRole The role of the participant within the proposal (e.g., "PROPOSER", "INITIATOR").
     */
    ProposalRole(String proposalRole) {
        this.proposalRole = proposalRole;
    }

    /**
     * Gets the role of the participant within the proposal associated with this enum constant.
     *
     * @return The role of the participant within the proposal (e.g., "PROPOSER").
     */
    public String getProposalRole() {
        return proposalRole;
    }

    /**
     * Static method to find an ProposalRole enum constant by its type in string.
     *
     * @param ProposalRoleInString The role of the participant within the proposal to search for (e.g., "PROPOSER" or "INITIATOR").
     * @return An Optional containing the ProposalRole enum if found, otherwise an empty Optional.
     */
    public static Optional<ProposalRole> fromAuthority(String ProposalRoleInString) {
        return Arrays.stream(ProposalRole.values()).filter(proposalRole -> proposalRole.getProposalRole().equalsIgnoreCase(ProposalRoleInString)).findFirst();
    }
}
