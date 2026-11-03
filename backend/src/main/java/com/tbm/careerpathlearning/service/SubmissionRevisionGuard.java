package com.tbm.careerpathlearning.service;

import com.tbm.careerpathlearning.exception.BadRequestException;
import java.math.BigDecimal;
import java.util.Objects;

/** A no-op save must not satisfy a reviewer's request for revision. */
public final class SubmissionRevisionGuard {
    private SubmissionRevisionGuard() {}

    public static void requireRevisionComplete(boolean revisionRequired) {
        if (revisionRequired) throw new BadRequestException(
                "Make and save at least one change before resubmitting for approval.");
    }

    public static boolean afterSave(boolean revisionRequired, Object before, Object after) {
        return revisionRequired && Objects.equals(before, after);
    }

    public static String text(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }

    public static BigDecimal decimal(BigDecimal value) {
        return value == null ? null : value.stripTrailingZeros();
    }
}
