package com.tbm.careerpathlearning.enums;

import java.util.Arrays;
import java.util.Optional;

public enum StaffAccountStatus {
    ACTIVE("ACTIVE"),
    INACTIVE("INACTIVE");

    private final String accountStatus;

    /**
     * Private constructor for the enum constants.
     *
     * @param accountStatus The status of the staff account (e.g., "ACTIVE", "LOCKED").
     */
    StaffAccountStatus(String accountStatus) {
        this.accountStatus = accountStatus;
    }


    /**
     * Gets the name of GrantedAuthority associated with this enum constant.
     *
     * @return The status of the staff account (e.g., "ACTIVE").
     */
    public String getAccountStatus() {
        return accountStatus;
    }

    /**
     * Static method to find an Authority enum constant by its authority name.
     *
     * @param accountStatus The status of the staff account to search for (e.g., "ACTIVE" or "LOCKED").
     * @return An Optional containing the StaffAccountStatus enum if found, otherwise an empty Optional.
     */
    public static Optional<StaffAccountStatus> fromAccountStatus(String accountStatus) {
        return Arrays.stream(StaffAccountStatus.values()).filter(staffAccountStatus -> staffAccountStatus.getAccountStatus().equalsIgnoreCase(accountStatus)).findFirst();
    }
}
