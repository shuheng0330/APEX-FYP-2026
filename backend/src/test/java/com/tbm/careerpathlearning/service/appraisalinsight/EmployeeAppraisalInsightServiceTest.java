package com.tbm.careerpathlearning.service.appraisalinsight;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.tbm.careerpathlearning.dto.*;
import com.tbm.careerpathlearning.dto.EmployeeInsightDto.*;
import com.tbm.careerpathlearning.enums.*;
import com.tbm.careerpathlearning.exception.*;
import com.tbm.careerpathlearning.model.*;
import com.tbm.careerpathlearning.repository.*;
import com.tbm.careerpathlearning.service.GeminiClient;
import com.tbm.careerpathlearning.service.GeminiClient.AppraisalProviderException;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.*;
import org.springframework.transaction.support.SimpleTransactionStatus;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import java.util.concurrent.locks.ReentrantLock;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

/** Deterministic coordinator tests; PostgreSQL lock/constraint verification is separate. */
class EmployeeAppraisalInsightServiceTest {
    final EmployeeInsightScopeRepository scopes=mock(EmployeeInsightScopeRepository.class);
    final EmployeeInsightGenerationRepository generations=mock(EmployeeInsightGenerationRepository.class);
    final EmployeeInsightRequestRepository receipts=mock(EmployeeInsightRequestRepository.class);
    final AnnualKpiReviewPeriodRepository periods=mock(AnnualKpiReviewPeriodRepository.class);
    final EmployeeInsightAccess access=mock(EmployeeInsightAccess.class);
    final EmployeeInsightEvidenceService evidence=mock(EmployeeInsightEvidenceService.class);
    final EmployeeInsightSettings settings=mock(EmployeeInsightSettings.class);
    final GeminiClient gemini=mock(GeminiClient.class);
    final PlatformTransactionManager transactions=mock(PlatformTransactionManager.class);
    final Map<UUID,EmployeeInsightGeneration> rows=new ConcurrentHashMap<>();
    final Map<String,EmployeeInsightRequest> keys=new ConcurrentHashMap<>();
    final MutableClock clock=new MutableClock();
    final Queue<Runnable> jobs=new ConcurrentLinkedQueue<>();
    final UUID actor=UUID.randomUUID();
    final ObjectMapper json=new ObjectMapper().registerModule(new JavaTimeModule());
    final AppraisalInsightTemplate template=new AppraisalInsightTemplate(json);
    final AppraisalInsightDto.Evidence fixture=template.evidence(AppraisalInsightTemplate.ScenarioId.CONSISTENT_RESULTS);
    EmployeeAppraisalInsightService service;
    @BeforeEach void setup() throws Exception {
        var lock=new ReentrantLock(true);
        when(transactions.getTransaction(any())).thenAnswer(i->{lock.lock();return new SimpleTransactionStatus();});
        doAnswer(i->{lock.unlock();return null;}).when(transactions).commit(any());
        doAnswer(i->{lock.unlock();return null;}).when(transactions).rollback(any());
        when(settings.isEnabled()).thenReturn(true);when(settings.getCooldownSeconds()).thenReturn(60);when(settings.getDailyAttempts()).thenReturn(5);
        when(gemini.appraisalModel()).thenReturn("gemini-test");
        when(access.superior(anyLong(),eq(actor),anyBoolean())).thenAnswer(i->participant(i.getArgument(0)));
        when(access.worker(anyLong(),eq(actor))).thenAnswer(i->participant(i.getArgument(0)));
        when(evidence.capture(any())).thenReturn(new EmployeeInsightEvidenceService.Captured(fixture,"a".repeat(64),json.createObjectNode()));
        when(scopes.lockByParticipantId(anyLong())).thenReturn(Optional.of(new EmployeeInsightScope()));
        when(generations.saveAndFlush(any())).thenAnswer(i->{var g=i.getArgument(0,EmployeeInsightGeneration.class);rows.put(g.getId(),g);return g;});
        when(generations.findById(any())).thenAnswer(i->Optional.ofNullable(rows.get(i.getArgument(0))));
        when(generations.lockById(any())).thenAnswer(i->Optional.ofNullable(rows.get(i.getArgument(0))));
        when(generations.findAllByParticipantIdOrderByCreatedAtDescIdDesc(anyLong())).thenAnswer(i->rows.values().stream()
                .filter(g->Objects.equals(g.getParticipantId(),i.getArgument(0)))
                .sorted(Comparator.comparing(EmployeeInsightGeneration::getCreatedAt).thenComparing(EmployeeInsightGeneration::getId).reversed()).toList());
        when(generations.maxProviderRetryAt()).thenAnswer(i->rows.values().stream().map(EmployeeInsightGeneration::getRetryAt).filter(Objects::nonNull).max(OffsetDateTime::compareTo));
        when(receipts.saveAndFlush(any())).thenAnswer(i->{var r=i.getArgument(0,EmployeeInsightRequest.class);keys.put(r.getActorId()+":"+r.getRequestKey(),r);return r;});
        when(receipts.findByActorIdAndRequestKey(any(),any())).thenAnswer(i->Optional.ofNullable(keys.get(i.getArgument(0)+":"+i.getArgument(1))));
        when(gemini.generateAppraisalJson(any())).thenReturn(AppraisalInsightTemplateTest.validSummary(json));
        service=service(jobs::add);
    }
    EmployeeAppraisalInsightService service(Executor executor) {
        return new EmployeeAppraisalInsightService(scopes,generations,receipts,periods,access,evidence,settings,template,gemini,executor,transactions,clock);
    }
    @AfterEach void clearAuthentication() {SecurityContextHolder.clearContext();}
    AccessFixture realAccess(AnnualKpiReviewPeriodStatus periodStatus,AppraisalStatus appraisalStatus) {
        var staff=mock(StaffRepository.class);var permissions=mock(RoleAuthorityRepository.class);
        var participants=mock(ReviewPeriodParticipantRepository.class);var appraisals=mock(AnnualAppraisalRepository.class);
        var role=new Role();role.setId(1L);role.setPerformanceReviewEligible(true);
        var superior=new Staff();superior.setId(actor);superior.setRole(role);superior.setAccountStatus(StaffAccountStatus.ACTIVE);
        var p=participant(7L);p.getReviewPeriod().setStatus(periodStatus);
        p.getStaff().setRole(role);p.getStaff().setAccountStatus(StaffAccountStatus.ACTIVE);p.getStaff().setManager(superior);
        when(staff.findById(actor)).thenReturn(Optional.of(superior));when(participants.findById(7L)).thenReturn(Optional.of(p));
        when(permissions.existsByRoleIdAndAuthorityName(1L,AuthorityName.CAN_SUBMIT_ANNUAL_APPRAISAL)).thenReturn(true);
        AnnualAppraisal appraisal=null;
        if (appraisalStatus!=null) {
            appraisal=new AnnualAppraisal();appraisal.setSuperiorId(actor);appraisal.setStatus(appraisalStatus);
            appraisal.setJustification("Unchanged human justification");
            when(appraisals.findByParticipantId(7L)).thenReturn(Optional.of(appraisal));
        }
        var environment=new MockEnvironment();environment.setActiveProfiles("uat");var database=mock(JdbcTemplate.class);
        when(settings.getDatabaseName()).thenReturn("disposable");when(settings.getDatabaseMarker()).thenReturn("verified");
        when(database.queryForObject("select current_database()",String.class)).thenReturn("disposable");
        when(database.queryForObject("select shobj_description(oid,'pg_database') from pg_database where datname=current_database()",String.class)).thenReturn("verified");
        var checked=spy(new EmployeeInsightAccess(staff,permissions,participants,appraisals,settings,environment,database));
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(actor,null,
                List.of(new SimpleGrantedAuthority(AuthorityName.CAN_SUBMIT_ANNUAL_APPRAISAL.name()))));
        service=new EmployeeAppraisalInsightService(scopes,generations,receipts,periods,checked,evidence,settings,template,gemini,jobs::add,transactions,clock);
        return new AccessFixture(p,checked,appraisals,appraisal);
    }
    void assertNoAppraisalWrites(AccessFixture fixture,AppraisalStatus originalStatus) {
        verify(fixture.appraisals(),atLeastOnce()).findByParticipantId(7L);verifyNoMoreInteractions(fixture.appraisals());
        if (fixture.appraisal()!=null) {
            assertEquals(originalStatus,fixture.appraisal().getStatus());
            assertEquals("Unchanged human justification",fixture.appraisal().getJustification());
            assertNull(fixture.appraisal().getRecommendation());assertNull(fixture.appraisal().getSnapshot());
        }
    }
    record AccessFixture(ReviewPeriodParticipant participant,EmployeeInsightAccess access,
            AnnualAppraisalRepository appraisals,AnnualAppraisal appraisal) {}
    ReviewPeriodParticipant participant(Long id) {
        var p=new ReviewPeriodParticipant();p.setId(id);var employee=new Staff();employee.setId(new UUID(0,id));p.setStaff(employee);
        var period=new AnnualKpiReviewPeriod();period.setId(1L);p.setReviewPeriod(period);return p;
    }
    Result generate(boolean regenerate) {return service.generate(7L,UUID.randomUUID(),actor,regenerate);}
    void complete() {Objects.requireNonNull(jobs.poll()).run();}
    @ParameterizedTest @CsvSource({"OPEN,UNSAVED","CLOSED,UNSAVED","OPEN,DRAFT","CLOSED,DRAFT","OPEN,RETURNED","CLOSED,RETURNED"})
    void realEligibilityAllowsGenerateCacheAndRegenerateWithoutSavingAnAppraisal(AnnualKpiReviewPeriodStatus period,String appraisalState) {
        var status="UNSAVED".equals(appraisalState)?null:AppraisalStatus.valueOf(appraisalState);
        var fixture=realAccess(period,status);UUID key=UUID.randomUUID();
        var first=service.generate(7L,key,actor,false);assertEquals("NEWLY_QUEUED",first.disposition());complete();
        var initial=service.state(7L,actor);assertTrue(initial.eligible());assertEquals(1,initial.currentInsight().version());
        assertEquals(4,initial.limits().remainingAttempts());
        assertEquals(first.generation().id(),service.generate(7L,key,actor,false).generation().id());
        var cached=generate(false);assertEquals("REUSED",cached.disposition());assertEquals(first.generation().id(),cached.generation().id());
        assertEquals("COOLDOWN",assertThrows(EmployeeInsightLimitException.class,()->generate(true)).detail().reason());
        assertTrue(jobs.isEmpty());assertEquals(1,rows.size());verify(gemini,times(1)).generateAppraisalJson(any());
        clock.advance(60);var regenerated=generate(true);assertEquals("NEWLY_QUEUED",regenerated.disposition());complete();
        assertNotEquals(first.generation().id(),regenerated.generation().id());
        var current=service.state(7L,actor);assertTrue(current.eligible());assertEquals(2,current.currentInsight().version());
        assertEquals(2,rows.size());assertEquals(3,current.limits().remainingAttempts());
        assertEquals(1,rows.get(first.generation().id()).getInsightVersion());
        verify(gemini,times(2)).generateAppraisalJson(any());verify(fixture.access(),times(4)).worker(7L,actor);
        assertNoAppraisalWrites(fixture,status);
    }
    @ParameterizedTest @CsvSource({"OPEN,PENDING_REVIEW","CLOSED,PENDING_REVIEW","OPEN,APPROVED","CLOSED,APPROVED"})
    void realEligibilityRejectsFreshGenerateAndRegenerateAfterSubmissionOrApproval(AnnualKpiReviewPeriodStatus period,AppraisalStatus status) {
        var fixture=realAccess(period,status);
        assertThrows(BadRequestException.class,()->generate(false));assertThrows(BadRequestException.class,()->generate(true));
        assertFalse(service.state(7L,actor).eligible());assertTrue(rows.isEmpty());assertTrue(keys.isEmpty());assertTrue(jobs.isEmpty());
        verify(gemini,never()).generateAppraisalJson(any());assertNoAppraisalWrites(fixture,status);
    }
    @ParameterizedTest @ValueSource(booleans={false,true})
    void closureBeforeDispatchOrDuringProviderWorkPreservesTheUnsavedJobAndCache(boolean closeDuringProvider) {
        var fixture=realAccess(AnnualKpiReviewPeriodStatus.OPEN,null);
        var period=fixture.participant().getReviewPeriod();
        when(gemini.generateAppraisalJson(any())).thenAnswer(i->{
            if (closeDuringProvider) period.setStatus(AnnualKpiReviewPeriodStatus.CLOSED);
            return AppraisalInsightTemplateTest.validSummary(json);
        });
        var queued=generate(false);
        if (!closeDuringProvider) period.setStatus(AnnualKpiReviewPeriodStatus.CLOSED);
        complete();
        var state=service.state(7L,actor);assertTrue(state.eligible());assertNotNull(state.currentInsight());
        assertEquals("COMPLETED",state.currentInsight().status());assertFalse(state.currentInsight().outdated());
        assertEquals(queued.generation().id(),state.currentInsight().id());assertEquals(4,state.limits().remainingAttempts());
        var cached=generate(false);assertEquals("REUSED",cached.disposition());assertEquals(queued.generation().id(),cached.generation().id());
        assertEquals(1,rows.size());assertTrue(jobs.isEmpty());verify(gemini,times(1)).generateAppraisalJson(any());
        verify(fixture.access(),times(2)).worker(7L,actor);assertNoAppraisalWrites(fixture,null);
    }
    @Test void insightCompletedWhileOpenIsReusedAfterClosureEvenWhenFreshQuotaIsExhausted() {
        var fixture=realAccess(AnnualKpiReviewPeriodStatus.OPEN,null);var first=generate(false);complete();
        fixture.participant().getReviewPeriod().setStatus(AnnualKpiReviewPeriodStatus.CLOSED);
        when(settings.getDailyAttempts()).thenReturn(1);
        var state=service.state(7L,actor);assertTrue(state.eligible());assertFalse(state.currentInsight().outdated());
        assertEquals(0,state.limits().remainingAttempts());
        var cached=generate(false);assertEquals("REUSED",cached.disposition());assertEquals(first.generation().id(),cached.generation().id());
        assertEquals("DAILY_LIMIT",assertThrows(EmployeeInsightLimitException.class,()->generate(true)).detail().reason());
        assertEquals(1,rows.size());assertTrue(jobs.isEmpty());verify(gemini,times(1)).generateAppraisalJson(any());
        assertNoAppraisalWrites(fixture,null);
    }
    @ParameterizedTest @EnumSource(value=AppraisalStatus.class,names={"PENDING_REVIEW","APPROVED"})
    void closureDoesNotBypassAnAppraisalBecomingNonEditableDuringProviderWork(AppraisalStatus submittedStatus) {
        var fixture=realAccess(AnnualKpiReviewPeriodStatus.OPEN,AppraisalStatus.DRAFT);
        when(gemini.generateAppraisalJson(any())).thenAnswer(i->{
            fixture.participant().getReviewPeriod().setStatus(AnnualKpiReviewPeriodStatus.CLOSED);
            fixture.appraisal().setStatus(submittedStatus);return AppraisalInsightTemplateTest.validSummary(json);
        });
        var queued=generate(false);complete();
        assertEquals("FAILED",rows.get(queued.generation().id()).getStatus());assertNull(rows.get(queued.generation().id()).getInsightVersion());
        assertFalse(service.state(7L,actor).eligible());assertNull(service.state(7L,actor).currentInsight());
        assertEquals(4,service.state(7L,actor).limits().remainingAttempts());assertTrue(jobs.isEmpty());
        verify(gemini,times(1)).generateAppraisalJson(any());assertNoAppraisalWrites(fixture,submittedStatus);
    }
    @Test void oneHundredCachedRequestsMakeNoNewProviderCallsAndRemainAvailableAfterQuotaAndDuringBackoff() {
        var first=generate(false);complete();verify(gemini,times(1)).generateAppraisalJson(any());
        when(settings.getDailyAttempts()).thenReturn(1);
        var g=rows.get(first.generation().id());
        var failed=new EmployeeInsightGeneration();failed.setId(UUID.randomUUID());failed.setParticipantId(99L);
        failed.setRetryAt(OffsetDateTime.now(clock).plusMinutes(5));rows.put(failed.getId(),failed);
        for (int i=0;i<100;i++) {
            var cached=generate(false);assertEquals("REUSED",cached.disposition());assertEquals(g.getId(),cached.generation().id());
            assertEquals(0,cached.limits().remainingAttempts());
        }
        assertEquals(1,rows.values().stream().filter(r->Objects.equals(7L,r.getParticipantId())).count());
        assertEquals(101,keys.size());assertEquals(0,jobs.size());verify(gemini,times(1)).generateAppraisalJson(any());
    }
    @Test void fiftySimultaneousColdRequestsAcrossCoordinatorsJoinOneDispatch() throws Exception {
        var other=service(jobs::add);var start=new CountDownLatch(1);var ready=new CountDownLatch(50);
        var pool=Executors.newFixedThreadPool(50);var results=new ArrayList<Future<Result>>();
        try {
            for (int i=0;i<50;i++) {var coordinator=i%2==0?service:other;results.add(pool.submit(()->{
                ready.countDown();assertTrue(start.await(20,TimeUnit.SECONDS));return coordinator.generate(7L,UUID.randomUUID(),actor,false);
            }));}
            assertTrue(ready.await(20,TimeUnit.SECONDS));start.countDown();var ids=new HashSet<UUID>();
            for (var future:results) ids.add(future.get(20,TimeUnit.SECONDS).generation().id());
            assertEquals(1,ids.size());assertEquals(1,rows.size());assertEquals(1,jobs.size());assertEquals(50,keys.size());
            complete();verify(gemini,times(1)).generateAppraisalJson(any());
        } finally {start.countDown();pool.shutdownNow();assertTrue(pool.awaitTermination(20,TimeUnit.SECONDS));}
    }
    @Test void regenerateJoinsActiveWorkButNeverSubstitutesCompletedCacheAndReplaysAreFree() {
        generate(false);complete();clock.advance(60);UUID key=UUID.randomUUID();
        var fresh=service.generate(7L,key,actor,true);assertEquals("NEWLY_QUEUED",fresh.disposition());
        var joined=generate(true);assertEquals("ALREADY_IN_PROGRESS",joined.disposition());assertEquals(fresh.generation().id(),joined.generation().id());
        assertEquals(fresh.generation().id(),service.generate(7L,key,actor,true).generation().id());assertEquals(1,jobs.size());
        assertThrows(KpiVersionConflictException.class,()->service.generate(7L,key,actor,false));
        assertThrows(KpiVersionConflictException.class,()->service.generate(8L,key,actor,true));
        complete();assertEquals(2,rows.size());verify(gemini,times(2)).generateAppraisalJson(any());
    }
    @Test void sharedFiveAttemptBudgetCooldownBoundaryAndMidnightResetUseBusinessClock() {
        generate(false);complete();clock.advance(59);
        assertEquals("COOLDOWN",assertThrows(EmployeeInsightLimitException.class,()->generate(true)).detail().reason());
        clock.advance(1);var other=service(jobs::add);
        for (int i=0;i<4;i++) {other.generate(7L,UUID.randomUUID(),actor,true);complete();clock.advance(60);}
        assertEquals("DAILY_LIMIT",assertThrows(EmployeeInsightLimitException.class,()->generate(true)).detail().reason());
        verify(gemini,times(5)).generateAppraisalJson(any());
        clock.instant.set(Instant.parse("2026-11-06T16:00:00Z"));generate(true);complete();
        assertEquals(4,service.state(7L,actor).limits().remainingAttempts());verify(gemini,times(6)).generateAppraisalJson(any());
    }
    @Test void queuedWorkCrossingMidnightChargesDispatchDayAndProviderFailuresKeepPriorSuccess() {
        generate(false);complete();clock.instant.set(Instant.parse("2026-11-06T15:59:59Z"));var pending=generate(true);
        clock.advance(2);when(gemini.generateAppraisalJson(any())).thenThrow(new AppraisalProviderException("RATE_LIMITED","120"));complete();
        var state=service.state(7L,actor);assertEquals(4,state.limits().remainingAttempts());assertNotNull(state.currentInsight());
        assertEquals("FAILED",rows.get(pending.generation().id()).getStatus());assertNotNull(state.latestAttempt().retryAt());
        assertEquals("REUSED",generate(false).disposition());
        assertEquals("PROVIDER_RATE_LIMIT",assertThrows(EmployeeInsightLimitException.class,()->generate(true)).detail().reason());
        verify(gemini,times(2)).generateAppraisalJson(any());assertTrue(jobs.isEmpty());
    }
    @Test void changedEvidenceOrModelPreventsReuseAndPreDispatchRevocationReleasesReservation() {
        generate(false);complete();clock.advance(60);
        when(evidence.capture(any())).thenReturn(new EmployeeInsightEvidenceService.Captured(fixture,"b".repeat(64),json.createObjectNode()));
        generate(false);when(access.worker(7L,actor)).thenThrow(new AccessDeniedException("revoked"));complete();
        var state=service.state(7L,actor);assertNull(state.currentInsight());assertEquals(4,state.limits().remainingAttempts());
        assertEquals("ACCESS_CHANGED",state.latestAttempt().errorCode());verify(gemini,times(1)).generateAppraisalJson(any());
        doReturn(participant(7L)).when(access).worker(7L,actor);when(gemini.appraisalModel()).thenReturn("gemini-next");
        assertEquals("NEWLY_QUEUED",generate(false).disposition());
    }
    @Test void evidenceChangedDuringProviderCallAndUncertainProviderFailureAreNeverRetried() {
        when(gemini.generateAppraisalJson(any())).thenAnswer(i->{
            when(evidence.capture(any())).thenReturn(new EmployeeInsightEvidenceService.Captured(fixture,"changed",json.createObjectNode()));
            return AppraisalInsightTemplateTest.validSummary(json);
        });generate(false);complete();
        assertEquals("EVIDENCE_CHANGED",service.state(7L,actor).latestAttempt().errorCode());
        assertEquals(4,service.state(7L,actor).limits().remainingAttempts());clock.advance(60);
        when(gemini.generateAppraisalJson(any())).thenThrow(new AppraisalProviderException("PROVIDER_UNAVAILABLE"));generate(true);complete();
        assertEquals(3,service.state(7L,actor).limits().remainingAttempts());assertTrue(jobs.isEmpty());
        verify(gemini,times(2)).generateAppraisalJson(any());
    }
    @Test void gracefulCancellationAndExecutorRejectionReleaseUnusedCapacityAndCooldown() {
        generate(false);service.cancelLocalWork();var state=service.state(7L,actor);
        assertEquals(5,state.limits().remainingAttempts());assertNull(state.limits().cooldownUntil());complete();verify(gemini,never()).generateAppraisalJson(any());
        var full=service(task->{throw new RejectedExecutionException();});
        assertEquals("BUSY",assertThrows(EmployeeInsightLimitException.class,()->full.generate(7L,UUID.randomUUID(),actor,true)).detail().reason());
        assertEquals(5,service.state(7L,actor).limits().remainingAttempts());
    }
    @Test void providerConfigurationFailureBeforeDispatchDoesNotConsumeAnAttempt() {
        doThrow(new AppraisalProviderException("NOT_CONFIGURED")).when(gemini).validateAppraisalConfiguration();
        var admitted=generate(false);complete();
        assertNull(rows.get(admitted.generation().id()).getDispatchedAt());
        assertEquals(5,service.state(7L,actor).limits().remainingAttempts());
        assertNull(service.state(7L,actor).limits().cooldownUntil());verify(gemini,never()).generateAppraisalJson(any());
    }
    @Test void readOnlyStatePreservesOlderContentWhenResultsBecomeIncompleteAndRecoveryNeverGenerates() {
        var first=generate(false);complete();
        when(evidence.capture(any())).thenThrow(new BadRequestException("Results incomplete"));
        var state=service.state(7L,actor);assertFalse(state.eligible());assertEquals("RESULTS_INCOMPLETE",state.unavailableReason());
        assertNull(state.currentInsight());assertTrue(state.latestAttempt().outdated());
        assertEquals(first.generation().id(),service.read(7L,first.generation().id(),actor).generation().id());
        assertTrue(jobs.isEmpty());verify(gemini,times(1)).generateAppraisalJson(any());
    }
    @Test void twentyContextsRespectOneWorkerEightQueuedJobsAndSafeBusyResponses() throws Exception {
        var executor=new ThreadPoolExecutor(1,1,0,TimeUnit.SECONDS,new ArrayBlockingQueue<>(8));
        var entered=new CountDownLatch(1);var release=new CountDownLatch(1);var active=new AtomicInteger();var peak=new AtomicInteger();var calls=new AtomicInteger();
        when(gemini.generateAppraisalJson(any())).thenAnswer(i->{int count=active.incrementAndGet();peak.accumulateAndGet(count,Math::max);
            calls.incrementAndGet();entered.countDown();assertTrue(release.await(20,TimeUnit.SECONDS));active.decrementAndGet();return AppraisalInsightTemplateTest.validSummary(json);});
        var bounded=service(executor);int busy=0;
        try {
            bounded.generate(1L,UUID.randomUUID(),actor,false);assertTrue(entered.await(20,TimeUnit.SECONDS));
            for (long id=2;id<=20;id++) try {bounded.generate(id,UUID.randomUUID(),actor,false);}
                catch (EmployeeInsightLimitException full) {assertEquals("BUSY",full.detail().reason());busy++;}
            assertEquals(11,busy);assertEquals(8,executor.getQueue().size());release.countDown();executor.shutdown();
            assertTrue(executor.awaitTermination(30,TimeUnit.SECONDS));assertEquals(9,calls.get());assertEquals(1,peak.get());
        } finally {release.countDown();executor.shutdownNow();assertTrue(executor.awaitTermination(30,TimeUnit.SECONDS));}
    }
    static class MutableClock extends Clock {
        final AtomicReference<Instant> instant=new AtomicReference<>(Instant.parse("2026-11-06T01:00:00Z"));
        final ZoneId zone;
        MutableClock() {zone=ZoneId.of("Asia/Kuala_Lumpur");}
        MutableClock(AtomicReference<Instant> value,ZoneId zone) {this.zone=zone;instant.set(value.get());delegate=value;}
        AtomicReference<Instant> delegate;
        public ZoneId getZone() {return zone;}
        public Clock withZone(ZoneId zone) {return zone.equals(this.zone)?this:new MutableClock(instant,zone);}
        public Instant instant() {return delegate==null?instant.get():delegate.get();}
        void advance(long seconds) {instant.updateAndGet(t->t.plusSeconds(seconds));}
    }
}
