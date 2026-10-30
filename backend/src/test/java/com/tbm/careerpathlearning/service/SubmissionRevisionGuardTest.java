package com.tbm.careerpathlearning.service;

import com.tbm.careerpathlearning.exception.BadRequestException;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import static org.junit.jupiter.api.Assertions.*;

class SubmissionRevisionGuardTest {
    @Test void blocksOnlyUnrevisedRecordsAndIgnoresNoOpSaves() {
        assertDoesNotThrow(()->SubmissionRevisionGuard.requireRevisionComplete(false));
        assertThrows(BadRequestException.class,()->SubmissionRevisionGuard.requireRevisionComplete(true));
        assertTrue(SubmissionRevisionGuard.afterSave(true,"same","same"));
        assertFalse(SubmissionRevisionGuard.afterSave(true,"old","new"));
        assertFalse(SubmissionRevisionGuard.afterSave(false,"same","same"));
    }
    @Test void normalizesOuterWhitespaceAndDecimalScale() {
        assertEquals("Target",SubmissionRevisionGuard.text(" Target "));
        assertNull(SubmissionRevisionGuard.text(" "));assertNull(SubmissionRevisionGuard.text(null));
        assertEquals(SubmissionRevisionGuard.decimal(new BigDecimal("100.00")),SubmissionRevisionGuard.decimal(new BigDecimal("100")));
    }
}
