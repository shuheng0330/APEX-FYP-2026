package com.tbm.careerpathlearning.service.appraisalinsight;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tbm.careerpathlearning.dto.AppraisalInsightDto;
import com.tbm.careerpathlearning.exception.*;
import com.tbm.careerpathlearning.model.AppraisalInsightGeneration;
import com.tbm.careerpathlearning.repository.AppraisalInsightGenerationRepository;
import com.tbm.careerpathlearning.service.GeminiClient;
import org.junit.jupiter.api.*;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.*;
import org.springframework.transaction.support.SimpleTransactionStatus;
import java.time.*;
import java.util.*;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class SyntheticAppraisalInsightServiceTest {
    final UUID actor = UUID.randomUUID();
    final AppraisalInsightGenerationRepository repository = mock(AppraisalInsightGenerationRepository.class);
    final GeminiClient gemini = mock(GeminiClient.class);
    final PlatformTransactionManager transactions = mock(PlatformTransactionManager.class);
    final AppraisalInsightTemplate template = new AppraisalInsightTemplate(new ObjectMapper().findAndRegisterModules());
    final MockEnvironment environment = new MockEnvironment();
    final Map<UUID, AppraisalInsightGeneration> stored = new LinkedHashMap<>();
    final Queue<Runnable> queue = new ArrayDeque<>();
    final AtomicInteger transactionDepth = new AtomicInteger();
    SyntheticAppraisalInsightService service;

    @BeforeEach void setup() throws Exception {
        environment.setActiveProfiles("test");
        authenticate(actor, "CAN_SUBMIT_ANNUAL_APPRAISAL");
        when(transactions.getTransaction(any())).thenAnswer(inv -> { transactionDepth.incrementAndGet(); return new SimpleTransactionStatus(); });
        doAnswer(inv -> { transactionDepth.decrementAndGet(); return null; }).when(transactions).commit(any());
        doAnswer(inv -> { transactionDepth.decrementAndGet(); return null; }).when(transactions).rollback(any());
        when(repository.countAuthorisedPreviewAccounts(any(), anySet())).thenReturn(1L);
        when(repository.findById(any())).thenAnswer(inv -> Optional.ofNullable(stored.get(inv.getArgument(0))));
        when(repository.findByOwnerIdAndRequestKey(any(), any())).thenAnswer(inv -> stored.values().stream()
                .filter(g -> g.getOwnerId().equals(inv.getArgument(0)) && g.getRequestKey().equals(inv.getArgument(1))).findFirst());
        when(repository.saveAndFlush(any())).thenAnswer(inv -> {
            AppraisalInsightGeneration g = inv.getArgument(0);
            if (g.getActiveKey() != null && stored.values().stream().anyMatch(other -> !other.getId().equals(g.getId())
                    && g.getActiveKey().equals(other.getActiveKey()))) throw new DataIntegrityViolationException("duplicate active key");
            stored.put(g.getId(), g); return g;
        });
        when(gemini.appraisalModel()).thenReturn("gemini-test");
        when(gemini.generateAppraisalJson(anyString())).thenAnswer(inv -> {
            assertEquals(0, transactionDepth.get(), "No database transaction may surround the provider call");
            assertEquals(template.prompt(template.evidence(AppraisalInsightTemplate.ScenarioId.LIMITED_EVIDENCE)), inv.getArgument(0));
            return AppraisalInsightTemplateTest.validSummary(new ObjectMapper());
        });
        service = service(queue::add, true);
    }
    @AfterEach void clear() { SecurityContextHolder.clearContext(); }
    @Test void commitsBeforeDispatchAndUsesOnlyFixedFictionalEvidence() {
        var started = generate(UUID.randomUUID());
        assertEquals("QUEUED", started.status()); assertTrue(started.synthetic()); assertNull(started.summary());
        assertEquals(1, queue.size()); assertEquals(0, transactionDepth.get());
        verify(gemini, never()).generateAppraisalJson(anyString());
        queue.remove().run();
        var completed = service.get(started.id(), actor);
        assertEquals("COMPLETED", completed.status()); assertNotNull(completed.summary());
        assertNotNull(completed.completedAt()); assertTrue(completed.disclaimer().contains("Review the supporting evidence"));
        assertNull(stored.get(started.id()).getActiveKey()); verify(gemini).generateAppraisalJson(anyString());
    }
    @Test void repeatedRequestsReturnTheSameGenerationBeforeAndAfterCompletion() {
        UUID key = UUID.randomUUID(); var original = generate(key);
        assertEquals(original.id(), generate(key).id()); assertEquals(1, queue.size());
        queue.remove().run();
        assertEquals(original.id(), generate(key).id()); assertEquals(1, stored.size()); assertTrue(queue.isEmpty());
        verify(gemini, times(1)).generateAppraisalJson(anyString());
    }
    @Test void concurrentDifferentRequestsAreStoppedByTheUniqueActiveKey() {
        generate(UUID.randomUUID());
        assertThrows(KpiVersionConflictException.class, () -> generate(UUID.randomUUID()));
        assertEquals(1, stored.size()); assertEquals(1, queue.size());
    }
    @Test void idempotencyKeyCannotBeReusedForAnotherScenario() {
        UUID key = UUID.randomUUID(); generate(key);
        assertThrows(KpiVersionConflictException.class, () -> service.generate("MIXED_RESULTS", key, actor));
        assertEquals(1, stored.size());
    }
    @Test void disabledAndNonUatProfilesCannotReadOrGenerateOrRecover() {
        var disabled = service(queue::add, false);
        assertThrows(BadRequestException.class, () -> disabled.scenarios(actor));
        disabled.interruptPreviousRun();
        environment.setActiveProfiles("prod");
        assertThrows(BadRequestException.class, () -> generate(UUID.randomUUID()));
        service.interruptPreviousRun();
        verifyNoInteractions(repository); verify(gemini, never()).generateAppraisalJson(anyString());
    }
    @Test void employeesDashboardViewersInactiveAccountsAndImpersonationAreDenied() {
        for (var permission : List.of("ROLE_USER", "ROLE_SUPER_ADMIN", "CAN_VIEW_ORGANISATION_PERFORMANCE")) {
            authenticate(actor, permission);
            assertThrows(AccessDeniedException.class, () -> service.scenarios(actor));
        }
        authenticate(UUID.randomUUID(), "CAN_SUBMIT_ANNUAL_APPRAISAL");
        assertThrows(AccessDeniedException.class, () -> service.scenarios(actor));
        authenticate(actor, "CAN_SUBMIT_ANNUAL_APPRAISAL");
        when(repository.countAuthorisedPreviewAccounts(any(), anySet())).thenReturn(0L);
        assertThrows(AccessDeniedException.class, () -> service.scenarios(actor));
        verify(gemini, never()).generateAppraisalJson(anyString());
    }
    @Test void anotherAuthorisedAccountCannotReadSomeoneElsesPreview() {
        var started = generate(UUID.randomUUID()); var other = UUID.randomUUID();
        authenticate(other, "CAN_REVIEW_ANNUAL_APPRAISAL");
        assertThrows(AccessDeniedException.class, () -> service.get(started.id(), other));
    }
    @Test void revokedAccessBeforeCallingOrPublishingPreventsResultsBeingExposed() {
        var started = generate(UUID.randomUUID());
        when(repository.countAuthorisedPreviewAccounts(any(), anySet())).thenReturn(0L);
        queue.remove().run();
        assertEquals("ACCESS_CHANGED", stored.get(started.id()).getErrorCode());
        assertNull(stored.get(started.id()).getSummary()); verify(gemini, never()).generateAppraisalJson(anyString());
    }
    @Test void accessIsRecheckedAfterTheProviderReturns() throws Exception {
        var started = generate(UUID.randomUUID());
        doAnswer(inv -> {
            when(repository.countAuthorisedPreviewAccounts(any(), anySet())).thenReturn(0L);
            return AppraisalInsightTemplateTest.validSummary(new ObjectMapper());
        }).when(gemini).generateAppraisalJson(anyString());
        queue.remove().run();
        assertEquals("ACCESS_CHANGED", stored.get(started.id()).getErrorCode()); assertNull(stored.get(started.id()).getSummary());
    }
    @Test void changedEvidenceIsRejectedBeforeAnyProviderRequest() {
        var started = generate(UUID.randomUUID()); stored.get(started.id()).setEvidenceHash("tampered");
        queue.remove().run();
        assertEquals("EVIDENCE_CHANGED", stored.get(started.id()).getErrorCode()); verify(gemini, never()).generateAppraisalJson(anyString());
    }
    @Test void providerFailureIsSafeAndExplicitRetryKeepsTheEarlierGeneration() {
        doThrow(new GeminiClient.AppraisalProviderException("RATE_LIMITED")).when(gemini).generateAppraisalJson(anyString());
        var failed = generate(UUID.randomUUID()); queue.remove().run();
        assertEquals("FAILED", service.get(failed.id(), actor).status());
        assertEquals("RATE_LIMITED", service.get(failed.id(), actor).errorCode()); assertNull(service.get(failed.id(), actor).summary());
        var retry = generate(UUID.randomUUID()); assertNotEquals(failed.id(), retry.id()); assertEquals(2, stored.size());
        assertEquals("QUEUED", retry.status());
    }
    @Test void malformedOutputNeverBecomesACompletedInsight() {
        doReturn("{\"finalDecision\":\"PROMOTION\"}").when(gemini).generateAppraisalJson(anyString());
        var started = generate(UUID.randomUUID()); queue.remove().run();
        assertEquals("INVALID_RESPONSE", service.get(started.id(), actor).errorCode()); assertNull(service.get(started.id(), actor).summary());
    }
    @Test void unexpectedFailureDoesNotExposeDetailsOrReplaceThePreviousValidSummary() {
        var original = generate(UUID.randomUUID()); queue.remove().run(); var recorded = service.get(original.id(), actor).summary();
        doThrow(new IllegalStateException("private evidence and secret provider key")).when(gemini).generateAppraisalJson(anyString());
        var next = generate(UUID.randomUUID()); queue.remove().run();
        assertEquals("GENERATION_FAILED", service.get(next.id(), actor).errorCode());
        assertNull(service.get(next.id(), actor).summary()); assertEquals(recorded, service.get(original.id(), actor).summary());
        assertEquals(2, stored.size()); assertTrue(queue.isEmpty());
    }
    @Test void fullQueueHasASafeFailureAndRestartNeverRepeatsRequests() {
        var busy = service(task -> { throw new RejectedExecutionException(); }, true);
        var failed = busy.generate("LIMITED_EVIDENCE", UUID.randomUUID(), actor);
        assertEquals("BUSY", failed.errorCode()); assertEquals("FAILED", failed.status());
        service.interruptPreviousRun(); verify(repository).interruptUnfinished(
                eq(OffsetDateTime.parse("2026-11-05T04:00:00Z")), eq(OffsetDateTime.parse("2026-11-05T04:00:00Z")));
        verify(gemini, never()).generateAppraisalJson(anyString());
    }
    private AppraisalInsightDto generate(UUID key) { return service.generate("LIMITED_EVIDENCE", key, actor); }
    private SyntheticAppraisalInsightService service(Executor executor, boolean enabled) {
        return new SyntheticAppraisalInsightService(repository, template, gemini, executor, transactions,
                Clock.fixed(Instant.parse("2026-11-05T04:00:00Z"), ZoneOffset.UTC), environment, enabled);
    }
    private void authenticate(UUID id, String permission) {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(id, null,
                List.of(new SimpleGrantedAuthority(permission))));
    }
}
