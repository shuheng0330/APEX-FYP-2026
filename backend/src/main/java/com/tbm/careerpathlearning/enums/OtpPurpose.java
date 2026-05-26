package com.tbm.careerpathlearning.enums;

import java.util.Arrays;
import java.util.Optional;

public enum OtpPurpose {
    PASSWORD_RESET("PASSWORD_RESET"),
    ACCOUNT_ACTIVATION("ACCOUNT_ACTIVATION");

    private final String otpPurpose;

    /**
     * Private constructor for the enum constants.
     *
     * @param otpPurpose The purpose of the opt code (e.g., "PASSWORD_RESET", "ACCOUNT_ACTIVATION").
     */
    OtpPurpose(String otpPurpose) {
        this.otpPurpose = otpPurpose;
    }


    /**
     * Gets the purpose of the opt code.
     *
     * @return The purpose of the opt code (e.g., "PASSWORD_RESET", "ACCOUNT_ACTIVATION").
     */
    public String getOtpPurpose() {
        return otpPurpose;
    }

    /**
     * Static method to find an OtpPurpose of the opt code enum constant by its purpose.
     *
     * @param otpPurpose The purpose of the opt code (e.g., "PASSWORD_RESET", "ACCOUNT_ACTIVATION").
     * @return An Optional containing the OtpPurpose enum if found, otherwise an empty Optional.
     */
    public static Optional<OtpPurpose> fromOtpPurpose(String otpPurpose) {
        return Arrays.stream(OtpPurpose.values()).filter(staffOtpPurpose -> staffOtpPurpose.getOtpPurpose().equalsIgnoreCase(otpPurpose)).findFirst();
    }
}
