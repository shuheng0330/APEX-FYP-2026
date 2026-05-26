package com.tbm.careerpathlearning.enums;

import java.util.Arrays;
import java.util.Optional;

public enum ProposalStatus {
    ONGOING("ONGOING"),
    REJECTED("REJECTED"),
    APPROVED("APPROVED");

    private final String proposalStatus;

    /**
     * Private constructor for the enum constants.
     *
     * @param proposalStatus The status of the proposal (e.g., "PENDING", "REJECTED").
     */
    ProposalStatus(String proposalStatus) {
        this.proposalStatus = proposalStatus;
    }


    /**
     * Gets the status of the proposal associated with this enum constant.
     *
     * @return The status of the proposal (e.g., "PENDING").
     */
    public String getProposalStatus() {
        return proposalStatus;
    }

    /**
     * Static method to find an ProposalStatus enum constant by its status in string.
     *
     * @param proposalStatusInString The status of the proposal to search for (e.g., "PENDING" or "REJECTED").
     * @return An Optional containing the ProposalStatus enum if found, otherwise an empty Optional.
     */
    public static Optional<ProposalStatus> fromAccountStatus(String proposalStatusInString) {
        return Arrays.stream(ProposalStatus.values()).filter(proposalStatus -> proposalStatus.getProposalStatus().equalsIgnoreCase(proposalStatusInString)).findFirst();
    }
}
