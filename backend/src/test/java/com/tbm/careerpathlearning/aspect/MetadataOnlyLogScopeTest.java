package com.tbm.careerpathlearning.aspect;

import org.junit.jupiter.api.Test;

import java.util.concurrent.Executors;

import static org.junit.jupiter.api.Assertions.*;

class MetadataOnlyLogScopeTest {
    @Test void nestedScopesRestoreTheOuterScopeAndRemoveContextOnExit() {
        assertFalse(MetadataOnlyLogScope.active());
        try (var outer = MetadataOnlyLogScope.enter()) {
            assertTrue(MetadataOnlyLogScope.active());
            try (var inner = MetadataOnlyLogScope.enter()) {
                assertTrue(MetadataOnlyLogScope.active());
            }
            assertTrue(MetadataOnlyLogScope.active());
        }
        assertFalse(MetadataOnlyLogScope.active());
    }

    @Test void exceptionsCleanUpBothNestedScopes() {
        assertThrows(IllegalStateException.class, () -> {
            try (var outer = MetadataOnlyLogScope.enter(); var inner = MetadataOnlyLogScope.enter()) {
                throw new IllegalStateException("private-error");
            }
        });
        assertFalse(MetadataOnlyLogScope.active());
    }

    @Test void closingAnInnerScopeTwiceDoesNotClearTheOuterScope() {
        try (var outer = MetadataOnlyLogScope.enter()) {
            var inner = MetadataOnlyLogScope.enter();
            inner.close();
            inner.close();
            assertTrue(MetadataOnlyLogScope.active());
        }
        assertFalse(MetadataOnlyLogScope.active());
    }

    @Test void workerThreadsMustEnterTheirOwnScopeAndDoNotLeakItToTheNextTask() throws Exception {
        var worker = Executors.newSingleThreadExecutor();
        try (var outer = MetadataOnlyLogScope.enter()) {
            assertFalse(worker.submit(MetadataOnlyLogScope::active).get());
            assertTrue(worker.submit(() -> {
                try (var scope = MetadataOnlyLogScope.enter()) {
                    return MetadataOnlyLogScope.active();
                }
            }).get());
            assertFalse(worker.submit(MetadataOnlyLogScope::active).get());
            assertTrue(MetadataOnlyLogScope.active());
        } finally {
            worker.shutdownNow();
        }
        assertFalse(MetadataOnlyLogScope.active());
    }

    @Test void anotherThreadCannotCloseTheOwnersScope() throws Exception {
        var worker = Executors.newSingleThreadExecutor();
        try (var scope = MetadataOnlyLogScope.enter()) {
            worker.submit(() -> assertThrows(IllegalStateException.class, scope::close)).get();
            assertTrue(MetadataOnlyLogScope.active());
        } finally {
            worker.shutdownNow();
        }
        assertFalse(MetadataOnlyLogScope.active());
    }
}
