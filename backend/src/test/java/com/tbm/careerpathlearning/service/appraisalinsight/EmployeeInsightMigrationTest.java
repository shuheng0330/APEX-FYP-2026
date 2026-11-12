package com.tbm.careerpathlearning.service.appraisalinsight;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tbm.careerpathlearning.dto.AppraisalInsightDto;
import com.tbm.careerpathlearning.model.*;
import com.tbm.careerpathlearning.repository.*;
import org.hibernate.SessionFactory;
import org.hibernate.cfg.Configuration;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.jpa.repository.support.JpaRepositoryFactory;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.orm.jpa.SharedEntityManagerCreator;
import org.springframework.orm.jpa.vendor.HibernateJpaDialect;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.tbm.careerpathlearning.service.GeminiClient;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import java.math.BigDecimal;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.sql.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;

class EmployeeInsightMigrationTest {
    private static final UUID EMPLOYEE = new UUID(0, 1), OTHER_EMPLOYEE = new UUID(0, 2), OWNER = new UUID(0, 3);
    private static final OffsetDateTime NOW = OffsetDateTime.parse("2026-11-06T00:00:00Z");
    private static final String GENERATION = "appraisal_employee_insight_generation";
    private static final String ROOTS = "{\"plans\":{\"COMPANY\":10,\"DEPARTMENT\":20,\"INDIVIDUAL\":30},"
            + "\"attitudeConfigurationId\":5,\"historicalParticipantIds\":[6]}";

    @Test void migrationIsForwardOnlyAndDoesNotSeedTrustOrRewriteExistingData() throws Exception {
        String sql = migration().replaceAll("(?m)--.*$", "");
        assertTrue(sql.contains("data_mode = 'FICTIONAL_UAT_EMPLOYEE'"));
        assertFalse(sql.matches("(?is).*\\b(?:INSERT\\s+INTO|UPDATE|DELETE\\s+FROM|ALTER\\s+TABLE|DROP\\s+TABLE|TRUNCATE)\\s+"
                + "(?:appraisal_insight_generation|annual_appraisal|annual_appraisal_activity|review_period_participant|"
                + "staff|authority|role_authority|kpi_plan|kpi_assessment|attitude_assessment)\\b.*"));
        assertFalse(sql.matches("(?is).*\\bINSERT\\s+INTO\\b.*"));
        assertTrue(sql.contains("REFERENCES review_period_participant(id,review_period_id,staff_id)"));
        assertTrue(sql.contains("REFERENCES appraisal_employee_insight_scope(participant_id,employee_id,review_period_id)"));
        assertTrue(sql.contains("UNIQUE (active_context)"));
        assertTrue(sql.contains("UNIQUE (participant_id,insight_version)"));
        assertTrue(sql.contains("UNIQUE (actor_id,request_key)"));
        assertEquals(2, sql.split("REFERENCES appraisal_employee_insight_generation\\(id,participant_id\\)", -1).length - 1);
    }

    @Test void migrationFreezesInputsAndChecksSubmissionIdentityAndExpiredLeases() throws Exception {
        String sql = migration();
        assertTrue(sql.contains("OLD.status IN ('COMPLETED','FAILED')"));
        assertTrue(sql.contains("NEW.evidence,NEW.source_references,NEW.model,NEW.template_version,NEW.data_mode,NEW.created_at"));
        assertTrue(sql.contains("ROW(NEW.dispatched_at,NEW.quota_day)"));
        assertTrue(sql.contains("NEW.completed_at >= OLD.lease_until"));
        assertTrue(sql.contains("event.action IN ('SUBMITTED','RESUBMITTED')"));
        assertTrue(sql.contains("generation.employee_id = appraisal.employee_id"));
        assertTrue(sql.contains("generation.review_period_id = appraisal.review_period_id"));
        assertTrue(sql.contains("generation.status = 'COMPLETED'"));
        assertTrue(sql.contains("BEFORE UPDATE OR DELETE ON appraisal_employee_insight_request"));
        assertTrue(sql.contains("BEFORE UPDATE OR DELETE ON appraisal_employee_insight_submission_link"));
        assertTrue(sql.contains("p.owner_participant_id = NEW.participant_id"));
        assertTrue(sql.contains("h.staff_id = NEW.employee_id"));
    }

    /** Uses only the separately provisioned disposable cluster, never application credentials or .env. */
    @Nested
    @EnabledIfEnvironmentVariable(named = "APEX_EMPLOYEE_INSIGHT_PG_URL", matches = ".+")
    class DisposablePostgres {
        @Test void validatesRootLineageAndScopeIdentityWithoutAutoRegisteringFixtures() throws Exception {
            isolated((c, schema) -> {
                assertEquals(0, count(c, "SELECT count(*) FROM appraisal_employee_insight_scope"));
                reject(c, "23514", () -> scope(c, 7, EMPLOYEE, ROOTS.replace("\"INDIVIDUAL\":30", "\"INDIVIDUAL\":31")));
                reject(c, "23514", () -> scope(c, 7, EMPLOYEE, ROOTS.replace("\"COMPANY\":10", "\"COMPANY\":20")));
                reject(c, "23514", () -> scope(c, 8, OTHER_EMPLOYEE, rootsForOther().replace("[]", "[6]")));
                reject(c, "23503", () -> scope(c, 7, OTHER_EMPLOYEE, ROOTS.replace("[6]", "[]")));
                scope(c, 7, EMPLOYEE, ROOTS);
                reject(c, "23514", () -> sql(c, "UPDATE appraisal_employee_insight_scope SET provenance='changed' WHERE participant_id=7"));
                reject(c, "23514", () -> sql(c, "DELETE FROM appraisal_employee_insight_scope WHERE participant_id=7"));
                sql(c, "UPDATE appraisal_employee_insight_scope SET enabled=false WHERE participant_id=7");
                assertEquals(0, count(c, "SELECT count(*) FROM appraisal_employee_insight_scope WHERE enabled"));
                reject(c, "23503", () -> queued(c, 1, 7, OTHER_EMPLOYEE));
            });
        }

        @Test void enforcesActiveAndVersionUniquenessAndImmutableInputsAndDispatchAccounting() throws Exception {
            isolated((c, schema) -> {
                scope(c, 7, EMPLOYEE, ROOTS);
                queued(c, 1, 7, EMPLOYEE);
                reject(c, "23505", () -> queued(c, 2, 7, EMPLOYEE));
                reject(c, "23514", () -> sql(c, "UPDATE " + GENERATION + " SET fingerprint=repeat('b',64) WHERE id='" + id(1) + "'"));
                running(c, 1);
                reject(c, "23514", () -> sql(c, "UPDATE " + GENERATION + " SET quota_day='2026-11-07' WHERE id='" + id(1) + "'"));
                completed(c, 1, 1);
                reject(c, "23514", () -> sql(c, "UPDATE " + GENERATION + " SET summary='{}' WHERE id='" + id(1) + "'"));
                reject(c, "23514", () -> sql(c, "DELETE FROM " + GENERATION + " WHERE id='" + id(1) + "'"));
                queued(c, 2, 7, EMPLOYEE);
                running(c, 2);
                reject(c, "23505", () -> completed(c, 2, 1));
                reject(c, "23514", () -> sql(c, "UPDATE " + GENERATION + " SET status='COMPLETED',active_context=NULL,lease_until=NULL,"
                        + "insight_version=2,summary='{}',completed_at=" + timestamp(91) + " WHERE id='" + id(2) + "'"));
                completed(c, 2, 2);
                assertEquals(2, count(c, "SELECT count(*) FROM " + GENERATION + " WHERE status='COMPLETED'"));
            });
        }

        @Test void receiptsAndSubmissionLinksAreImmutableAndCannotCrossContextsOrUseDraftEvents() throws Exception {
            isolated((c, schema) -> {
                scope(c, 7, EMPLOYEE, ROOTS); scope(c, 8, OTHER_EMPLOYEE, rootsForOther());
                queued(c, 1, 7, EMPLOYEE); running(c, 1); completed(c, 1, 1);
                queued(c, 2, 8, OTHER_EMPLOYEE); running(c, 2); completed(c, 2, 1);
                receipt(c, 10, 7, 1, 99);
                reject(c, "23505", () -> receipt(c, 11, 7, 1, 99));
                reject(c, "23503", () -> receipt(c, 12, 8, 1, 100));
                reject(c, "23514", () -> sql(c, "UPDATE appraisal_employee_insight_request SET disposition='NEWLY_QUEUED'"));
                reject(c, "23514", () -> sql(c, "DELETE FROM appraisal_employee_insight_request"));
                link(c, 700, 70, 1, 7);
                reject(c, "23514", () -> link(c, 701, 70, 1, 7));
                reject(c, "23514", () -> link(c, 702, 70, 1, 7));
                reject(c, "23514", () -> link(c, 703, 70, 2, 7));
                reject(c, "23514", () -> sql(c, "UPDATE appraisal_employee_insight_submission_link SET generation_id='" + id(2) + "'"));
                reject(c, "23514", () -> sql(c, "DELETE FROM appraisal_employee_insight_submission_link"));
                assertEquals(1, count(c, "SELECT count(*) FROM appraisal_employee_insight_submission_link"));
            });
        }

        @Test void repositoriesRoundTripJsonAndRecoverOnlyExpiredLeasesWithoutLosingDispatchCharges() throws Exception {
            isolated((c, schema) -> {
                scope(c, 7, EMPLOYEE, ROOTS); scope(c, 8, OTHER_EMPLOYEE, rootsForOther());
                try (var factory = sessionFactory(schema); var em = factory.withOptions().connection(c).openSession()) {
                    em.beginTransaction();
                    var repositories = new JpaRepositoryFactory(em);
                    var scopes = repositories.getRepository(EmployeeInsightScopeRepository.class);
                    var generations = repositories.getRepository(EmployeeInsightGenerationRepository.class);
                    var requests = repositories.getRepository(EmployeeInsightRequestRepository.class);
                    var links = repositories.getRepository(EmployeeInsightSubmissionLinkRepository.class);
                    assertEquals(30L, scopes.lockByParticipantId(7L).orElseThrow().getSourceRoots().plans().get("INDIVIDUAL"));
                    var first = generation(id(1), 7L, EMPLOYEE);
                    generations.saveAndFlush(first); em.clear();
                    first = generations.lockById(id(1)).orElseThrow();
                    assertEquals("Fictional 2022", first.getEvidence().periodName());
                    assertEquals(10, first.getSourceReferences().path("companyPlanId").intValue());
                    assertEquals(id(1), generations.findActive(7L).orElseThrow().getId());
                    assertTrue(generations.maxVersion(7L).isEmpty());
                    markRunning(first); generations.saveAndFlush(first);
                    first.setStatus("COMPLETED"); first.setCompletedAt(NOW.plusSeconds(3)); first.setActiveContext(null);
                    first.setLeaseUntil(null); first.setInsightVersion(1); first.setSummary(summary());
                    generations.saveAndFlush(first); em.clear();
                    assertEquals(1, generations.maxVersion(7L).orElseThrow());
                    assertNotNull(generations.findById(id(1)).orElseThrow().getSummary().appraisalConsiderations());

                    var request = new EmployeeInsightRequest(); request.setId(id(10)); request.setActorId(OWNER);
                    request.setRequestKey(id(99)); request.setParticipantId(7L); request.setGenerationId(id(1));
                    request.setOperation("GENERATE"); request.setDisposition("REUSED"); request.setCreatedAt(NOW.plusSeconds(4));
                    requests.saveAndFlush(request);
                    var link = new EmployeeInsightSubmissionLink(); link.setSubmissionActivityId(700L); link.setAppraisalId(70L);
                    link.setGenerationId(id(1)); link.setParticipantId(7L); link.setCreatedAt(NOW.plusSeconds(4));
                    links.saveAndFlush(link); em.clear();
                    assertEquals(id(1), requests.findByActorIdAndRequestKey(OWNER, id(99)).orElseThrow().getGenerationId());
                    assertEquals(id(1), links.findBySubmissionActivityId(700L).orElseThrow().getGenerationId());
                    assertEquals(1, links.findAllByAppraisalIdOrderBySubmissionActivityIdDesc(70L).size());

                    var expired = generation(id(2), 7L, EMPLOYEE); generations.saveAndFlush(expired);
                    markRunning(expired); generations.saveAndFlush(expired);
                    generations.saveAndFlush(generation(id(3), 8L, OTHER_EMPLOYEE));
                    assertEquals(1, generations.interruptExpired(NOW.plusSeconds(91)));
                    expired = generations.findById(id(2)).orElseThrow();
                    assertEquals("FAILED", expired.getStatus()); assertEquals("INTERRUPTED", expired.getErrorCode());
                    assertEquals(NOW.plusSeconds(2).toInstant(), expired.getDispatchedAt().toInstant());
                    assertEquals(NOW.toLocalDate(), expired.getQuotaDay()); assertNull(expired.getLeaseUntil());
                    assertEquals(id(3), generations.findActive(8L).orElseThrow().getId());

                    var rateLimited = generation(id(4), 7L, EMPLOYEE); generations.saveAndFlush(rateLimited);
                    rateLimited.setStatus("FAILED"); rateLimited.setActiveContext(null); rateLimited.setLeaseUntil(null);
                    rateLimited.setCompletedAt(NOW.plusSeconds(95)); rateLimited.setErrorCode("RATE_LIMITED");
                    rateLimited.setRetryAt(NOW.plusSeconds(150)); generations.saveAndFlush(rateLimited);
                    assertEquals(NOW.plusSeconds(150).toInstant(), generations.maxProviderRetryAt().orElseThrow().toInstant());
                    assertEquals(3, generations.findAllByParticipantIdOrderByCreatedAtDescIdDesc(7L).size());
                    em.getTransaction().rollback();
                }
            });
        }

        @Test void fiftyConcurrentAdmissionsAcrossTwoManagersShareOneDatabaseContextLock() throws Exception {
            String schema = newSchema();
            try (var c = connect()) {
                c.setAutoCommit(false);
                prepare(c, schema); scope(c, 7, EMPLOYEE, ROOTS); c.commit();
                try (var firstManager = sessionFactory(schema); var secondManager = sessionFactory(schema)) {
                    var executor = Executors.newFixedThreadPool(50);
                    var ready = new CountDownLatch(50); var start = new CountDownLatch(1);
                    List<Future<UUID>> results = new ArrayList<>();
                    try {
                        for (int i = 0; i < 50; i++) {
                            var manager = i % 2 == 0 ? firstManager : secondManager;
                            results.add(executor.submit(() -> {
                                ready.countDown(); assertTrue(start.await(30, TimeUnit.SECONDS));
                                try (var em = manager.openSession()) {
                                    em.beginTransaction();
                                    em.createNativeMutationQuery("SET LOCAL search_path TO " + schema).executeUpdate();
                                    var repositories = new JpaRepositoryFactory(em);
                                    var scopes = repositories.getRepository(EmployeeInsightScopeRepository.class);
                                    var generations = repositories.getRepository(EmployeeInsightGenerationRepository.class);
                                    scopes.lockByParticipantId(7L).orElseThrow();
                                    var active = generations.findActive(7L);
                                    UUID admitted;
                                    if (active.isPresent()) admitted = active.get().getId();
                                    else {
                                        admitted = UUID.randomUUID();
                                        generations.saveAndFlush(generation(admitted, 7L, EMPLOYEE));
                                    }
                                    em.getTransaction().commit();
                                    return admitted;
                                }
                            }));
                        }
                        assertTrue(ready.await(30, TimeUnit.SECONDS)); start.countDown();
                        Set<UUID> admitted = new HashSet<>();
                        for (var result : results) admitted.add(result.get(60, TimeUnit.SECONDS));
                        assertEquals(1, admitted.size());
                        sql(c, "SET LOCAL search_path TO " + schema);
                        assertEquals(1, count(c, "SELECT count(*) FROM " + GENERATION));
                    } finally {
                        start.countDown(); executor.shutdownNow();
                        assertTrue(executor.awaitTermination(30, TimeUnit.SECONDS));
                    }
                }
            } finally {
                // Only this generated schema in the guarded disposable database is removed.
                assertTrue(schema.matches("employee_insight_test_[0-9a-f]{32}"));
                try (var cleanup = connect()) { sql(cleanup, "DROP SCHEMA IF EXISTS " + schema + " CASCADE"); }
            }
        }

        @Test void actualCoordinatorsShareCacheDispatchQuotaAndReceiptsAcrossDatabaseSessions() throws Exception {
            String schema=newSchema();
            try (var c=connect()) {
                c.setAutoCommit(false);prepare(c,schema);scope(c,7,EMPLOYEE,ROOTS);c.commit();
                try (var first=sessionFactory(schema);var second=sessionFactory(schema)) {
                    var json=new ObjectMapper().registerModule(new JavaTimeModule());var template=new AppraisalInsightTemplate(json);
                    var settings=mock(EmployeeInsightSettings.class);when(settings.isEnabled()).thenReturn(true);
                    when(settings.getCooldownSeconds()).thenReturn(60);when(settings.getDailyAttempts()).thenReturn(5);
                    var clock=new EmployeeAppraisalInsightServiceTest.MutableClock();
                    var access=mock(EmployeeInsightAccess.class);var evidence=mock(EmployeeInsightEvidenceService.class);
                    var p=new ReviewPeriodParticipant();p.setId(7L);var employee=new Staff();employee.setId(EMPLOYEE);p.setStaff(employee);
                    var period=new AnnualKpiReviewPeriod();period.setId(1L);p.setReviewPeriod(period);
                    when(access.superior(eq(7L),eq(OWNER),anyBoolean())).thenReturn(p);when(access.worker(7L,OWNER)).thenReturn(p);
                    var fixture=template.evidence(AppraisalInsightTemplate.ScenarioId.CONSISTENT_RESULTS);
                    when(evidence.capture(p)).thenReturn(new EmployeeInsightEvidenceService.Captured(fixture,"a".repeat(64),json.createObjectNode()));
                    var provider=mock(GeminiClient.class);when(provider.appraisalModel()).thenReturn("test-model");
                    when(provider.generateAppraisalJson(any())).thenReturn(AppraisalInsightTemplateTest.validSummary(json));
                    var jobs=new ConcurrentLinkedQueue<Runnable>();
                    var one=coordinator(first,access,evidence,settings,template,provider,jobs,clock);
                    var two=coordinator(second,access,evidence,settings,template,provider,jobs,clock);
                    var ready=new CountDownLatch(50);var start=new CountDownLatch(1);var pool=Executors.newFixedThreadPool(50);
                    try {
                        var calls=new ArrayList<Future<UUID>>();
                        for (int i=0;i<50;i++) {var service=i%2==0?one:two;calls.add(pool.submit(()->{
                            ready.countDown();assertTrue(start.await(20,TimeUnit.SECONDS));return service.generate(7L,UUID.randomUUID(),OWNER,false).generation().id();
                        }));}
                        assertTrue(ready.await(20,TimeUnit.SECONDS));start.countDown();var ids=new HashSet<UUID>();
                        for (var call:calls) ids.add(call.get(60,TimeUnit.SECONDS));
                        assertEquals(1,ids.size());assertEquals(1,jobs.size());jobs.remove().run();
                        for (int i=0;i<100;i++) assertEquals("REUSED",two.generate(7L,UUID.randomUUID(),OWNER,false).disposition());
                        verify(provider,times(1)).generateAppraisalJson(any());
                        clock.advance(60);
                        var againReady=new CountDownLatch(50);var againStart=new CountDownLatch(1);
                        var regenerations=new ArrayList<Future<UUID>>();
                        for (int i=0;i<50;i++) {var service=i%2==0?one:two;regenerations.add(pool.submit(()->{
                            againReady.countDown();assertTrue(againStart.await(20,TimeUnit.SECONDS));
                            return service.generate(7L,UUID.randomUUID(),OWNER,true).generation().id();
                        }));}
                        assertTrue(againReady.await(20,TimeUnit.SECONDS));againStart.countDown();var freshIds=new HashSet<UUID>();
                        for (var call:regenerations) freshIds.add(call.get(60,TimeUnit.SECONDS));
                        assertEquals(1,freshIds.size());assertEquals(1,jobs.size());assertFalse(ids.containsAll(freshIds));jobs.remove().run();
                        verify(provider,times(2)).generateAppraisalJson(any());
                        for (int i=0;i<3;i++) {clock.advance(60);two.generate(7L,UUID.randomUUID(),OWNER,true);jobs.remove().run();}
                        clock.advance(60);
                        var limited=assertThrows(com.tbm.careerpathlearning.exception.EmployeeInsightLimitException.class,
                                ()->one.generate(7L,UUID.randomUUID(),OWNER,true));
                        assertEquals("DAILY_LIMIT",limited.detail().reason());verify(provider,times(5)).generateAppraisalJson(any());
                        sql(c,"SET LOCAL search_path TO "+schema);
                        assertEquals(5,count(c,"SELECT count(*) FROM "+GENERATION+" WHERE dispatched_at IS NOT NULL"));
                        assertEquals(203,count(c,"SELECT count(*) FROM appraisal_employee_insight_request"));
                        assertTrue(jobs.isEmpty());
                    } finally {start.countDown();pool.shutdownNow();assertTrue(pool.awaitTermination(30,TimeUnit.SECONDS));}
                }
            } finally {
                assertTrue(schema.matches("employee_insight_test_[0-9a-f]{32}"));
                try (var cleanup=connect()) {sql(cleanup,"DROP SCHEMA IF EXISTS "+schema+" CASCADE");}
            }
        }

        @Test void optionalAiChecksDoNotPoisonAnExistingAppraisalTransaction() throws Exception {
            isolated((c,schema)->{
                try (var factory=sessionFactory(schema)) {
                    var manager=new JpaTransactionManager(factory);manager.setJpaDialect(new HibernateJpaDialect());
                    var attributes=new org.springframework.transaction.annotation.AnnotationTransactionAttributeSource();
                    var target=mock(EmployeeInsightEvidenceService.class);
                    var proxyFactory=new org.springframework.aop.framework.ProxyFactory(target);proxyFactory.setProxyTargetClass(true);
                    proxyFactory.addAdvice(new org.springframework.transaction.interceptor.TransactionInterceptor(manager,attributes));
                    var proxy=(EmployeeInsightEvidenceService)proxyFactory.getProxy();
                    var accessTarget=mock(EmployeeInsightAccess.class);
                    var accessFactory=new org.springframework.aop.framework.ProxyFactory(accessTarget);accessFactory.setProxyTargetClass(true);
                    accessFactory.addAdvice(new org.springframework.transaction.interceptor.TransactionInterceptor(manager,attributes));
                    var accessProxy=(EmployeeInsightAccess)accessFactory.getProxy();
                    var transaction=new org.springframework.transaction.support.TransactionTemplate(manager);
                    // Reuse the transaction's connection for a harmless SELECT; no application schema is involved.
                    var entityManager=SharedEntityManagerCreator.createSharedEntityManager(factory);
                    when(target.capture(any())).thenThrow(new com.tbm.careerpathlearning.exception.BadRequestException("Incomplete AI evidence"));
                    when(target.captureForSubmission(7L)).thenThrow(new IllegalStateException("Unavailable optional historical source"));
                    when(accessTarget.superior(anyLong(),any(),anyBoolean())).thenThrow(new org.springframework.security.access.AccessDeniedException("AI scope revoked"));
                    assertDoesNotThrow(()->transaction.executeWithoutResult(status->{
                        entityManager.createNativeQuery("select 1").getSingleResult();
                        assertThrows(com.tbm.careerpathlearning.exception.BadRequestException.class,()->proxy.capture(new ReviewPeriodParticipant()));
                        assertThrows(IllegalStateException.class,()->proxy.captureForSubmission(7L));
                        assertThrows(org.springframework.security.access.AccessDeniedException.class,()->accessProxy.superior(7L,OWNER,false));
                        assertFalse(status.isRollbackOnly());
                    }));
                }
            });
        }

        @Test void managedCoordinatorsJoinOneAppraisalWhileAnotherEmployeeGeneratesConcurrently() throws Exception {
            String schema=newSchema();
            var release=new CountDownLatch(1);var entered=new CountDownLatch(2);var active=new java.util.concurrent.atomic.AtomicInteger();
            var peak=new java.util.concurrent.atomic.AtomicInteger();var calls=new java.util.concurrent.atomic.AtomicInteger();
            var executors=new ArrayList<SharedAppraisalExecutor>();
            try (var c=connect()) {
                c.setAutoCommit(false);prepare(c,schema);scope(c,7,EMPLOYEE,ROOTS);scope(c,8,OTHER_EMPLOYEE,rootsForOther());
                sql(c,new ClassPathResource("db/migration/annual-kpi/V54__shared_gemini_dispatch.sql").getContentAsString(StandardCharsets.UTF_8));c.commit();
                var source=new org.springframework.jdbc.datasource.DriverManagerDataSource(System.getenv("APEX_EMPLOYEE_INSIGHT_PG_URL")+"?currentSchema="+schema,"stage2_test","");
                var db=new org.springframework.jdbc.core.JdbcTemplate(source);
                try (var first=sessionFactory(schema);var second=sessionFactory(schema)) {
                    var json=new ObjectMapper().registerModule(new JavaTimeModule());var template=new AppraisalInsightTemplate(json);
                    var limits=new GeminiDispatchSettings();
                    for (var pair:Map.<String,Object>of("enabled",true,"projectKey","disposable-project","model","test-model","workers",2,"queueCapacity",8,
                            "queueWaitSeconds",600,"requestsPerMinute",1000,"tokensPerMinute",10000000L,"requestsPerDay",10000).entrySet())
                        org.springframework.test.util.ReflectionTestUtils.setField(limits,pair.getKey(),pair.getValue());
                    var settings=mock(EmployeeInsightSettings.class);when(settings.isEnabled()).thenReturn(true);when(settings.getCooldownSeconds()).thenReturn(60);when(settings.getDailyAttempts()).thenReturn(5);
                    var clock=new EmployeeAppraisalInsightServiceTest.MutableClock();var access=mock(EmployeeInsightAccess.class);var evidence=mock(EmployeeInsightEvidenceService.class);
                    when(access.superior(anyLong(),eq(OWNER),anyBoolean())).thenAnswer(i->context(i.getArgument(0)));
                    when(access.worker(anyLong(),eq(OWNER))).thenAnswer(i->context(i.getArgument(0)));
                    var fixture=template.evidence(AppraisalInsightTemplate.ScenarioId.CONSISTENT_RESULTS);
                    when(evidence.capture(any())).thenReturn(new EmployeeInsightEvidenceService.Captured(fixture,"a".repeat(64),json.createObjectNode()));
                    var coordinators=new ArrayList<EmployeeAppraisalInsightService>();
                    for (var factory:List.of(first,second)) {
                        var manager=new JpaTransactionManager(factory);manager.setJpaDialect(new HibernateJpaDialect());manager.setDataSource(source);
                        var gate=new GeminiDispatchGate(db,manager,limits,()->OffsetDateTime.now(clock));
                        var executor=new com.tbm.careerpathlearning.config.AppraisalInsightConfig().appraisalInsightExecutor(gate,limits);executors.add(executor);
                        var provider=new GeminiClient();
                        org.springframework.test.util.ReflectionTestUtils.setField(provider,"coordination",gate);
                        org.springframework.test.util.ReflectionTestUtils.setField(provider,"apiKey","fictional-test-key");
                        org.springframework.test.util.ReflectionTestUtils.setField(provider,"model","test-model");
                        org.springframework.test.util.ReflectionTestUtils.setField(provider,"baseUrl","https://not-contacted.invalid");
                        String body=json.writeValueAsString(Map.of("candidates",List.of(Map.of("finishReason","STOP","content",Map.of("parts",List.of(Map.of("text",AppraisalInsightTemplateTest.validSummary(json))))))));
                        var client=org.springframework.web.reactive.function.client.WebClient.builder().exchangeFunction(request->reactor.core.publisher.Mono.fromCallable(()->{
                            peak.accumulateAndGet(active.incrementAndGet(),Math::max);calls.incrementAndGet();entered.countDown();
                            try {assertTrue(release.await(30,TimeUnit.SECONDS));return org.springframework.web.reactive.function.client.ClientResponse.create(org.springframework.http.HttpStatus.OK)
                                    .header("Content-Type","application/json").body(body).build();}finally {active.decrementAndGet();}
                        })).build();
                        org.springframework.test.util.ReflectionTestUtils.setField(provider,"webClient",client);
                        var repositories=new JpaRepositoryFactory(SharedEntityManagerCreator.createSharedEntityManager(factory));
                        coordinators.add(new EmployeeAppraisalInsightService(repositories.getRepository(EmployeeInsightScopeRepository.class),repositories.getRepository(EmployeeInsightGenerationRepository.class),
                                repositories.getRepository(EmployeeInsightRequestRepository.class),mock(AnnualKpiReviewPeriodRepository.class),access,evidence,settings,template,provider,executor,manager,clock));
                    }
                    var pool=Executors.newFixedThreadPool(50);var start=new CountDownLatch(1);var ready=new CountDownLatch(50);
                    try {
                        var futures=new ArrayList<Future<UUID>>();
                        for (int i=0;i<50;i++) {var coordinator=coordinators.get(i%2);futures.add(pool.submit(()->{
                            ready.countDown();assertTrue(start.await(20,TimeUnit.SECONDS));return coordinator.generate(7L,UUID.randomUUID(),OWNER,false).generation().id();
                        }));}
                        assertTrue(ready.await(20,TimeUnit.SECONDS));start.countDown();var ids=new HashSet<UUID>();
                        for (var future:futures) ids.add(future.get(60,TimeUnit.SECONDS));assertEquals(1,ids.size());
                        coordinators.get(1).generate(8L,UUID.randomUUID(),OWNER,false);assertTrue(entered.await(20,TimeUnit.SECONDS));assertEquals(2,peak.get());
                        release.countDown();long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(20);
                        while (db.queryForObject("select count(*) from appraisal_employee_insight_generation where status='COMPLETED'",Long.class)<2 && System.nanoTime()<deadline) Thread.onSpinWait();
                        assertEquals(2,db.queryForObject("select count(*) from appraisal_employee_insight_generation where status='COMPLETED'",Long.class));
                        for (int i=0;i<100;i++) assertEquals("REUSED",coordinators.get(i%2).generate(7L,UUID.randomUUID(),OWNER,false).disposition());
                        org.springframework.test.util.ReflectionTestUtils.setField(limits,"requestsPerMinute",0);
                        assertEquals("REUSED",coordinators.get(0).generate(7L,UUID.randomUUID(),OWNER,false).disposition());
                        org.springframework.test.util.ReflectionTestUtils.setField(limits,"requestsPerMinute",1000);
                        assertEquals(2,calls.get());assertEquals(2,db.queryForObject("select count(*) from gemini_request_permit where dispatched_at is not null",Long.class));
                    } finally {start.countDown();release.countDown();pool.shutdownNow();assertTrue(pool.awaitTermination(20,TimeUnit.SECONDS));}
                    for (var executor:executors) {
                        var worker=(org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor)org.springframework.test.util.ReflectionTestUtils.getField(executor,"workers");
                        executor.shutdown();assertTrue(worker.getThreadPoolExecutor().awaitTermination(20,TimeUnit.SECONDS));
                    }
                }
            } finally {
                release.countDown();for (var executor:executors) executor.shutdown();
                assertTrue(schema.matches("employee_insight_test_[0-9a-f]{32}"));try (var cleanup=connect()) {sql(cleanup,"DROP SCHEMA IF EXISTS "+schema+" CASCADE");}
            }
        }
    }

    private static ReviewPeriodParticipant context(Long id) {
        var p=new ReviewPeriodParticipant();p.setId(id);var employee=new Staff();employee.setId(id==7?EMPLOYEE:OTHER_EMPLOYEE);p.setStaff(employee);
        var period=new AnnualKpiReviewPeriod();period.setId(1L);p.setReviewPeriod(period);return p;
    }

    private static EmployeeAppraisalInsightService coordinator(SessionFactory factory,EmployeeInsightAccess access,
            EmployeeInsightEvidenceService evidence,EmployeeInsightSettings settings,AppraisalInsightTemplate template,
            GeminiClient provider,Queue<Runnable> jobs,Clock clock) {
        var repositories=new JpaRepositoryFactory(SharedEntityManagerCreator.createSharedEntityManager(factory));
        var manager=new JpaTransactionManager(factory);manager.setJpaDialect(new HibernateJpaDialect());
        return new EmployeeAppraisalInsightService(repositories.getRepository(EmployeeInsightScopeRepository.class),
                repositories.getRepository(EmployeeInsightGenerationRepository.class),repositories.getRepository(EmployeeInsightRequestRepository.class),
                mock(AnnualKpiReviewPeriodRepository.class),access,evidence,settings,template,provider,jobs::add,manager,clock);
    }

    private static String migration() throws Exception {
        return new ClassPathResource("db/migration/annual-kpi/V53__employee_appraisal_insight.sql")
                .getContentAsString(StandardCharsets.UTF_8);
    }

    private static Connection connect() throws Exception {
        String url = System.getenv("APEX_EMPLOYEE_INSIGHT_PG_URL");
        assertNotNull(url); assertTrue(url.startsWith("jdbc:postgresql://"));
        URI uri = URI.create(url.substring(5));
        assertTrue(List.of("localhost", "127.0.0.1", "[::1]", "::1").contains(uri.getHost()));
        assertEquals(55439, uri.getPort()); assertEquals("/apex_ai_stage2_disposable", uri.getPath());
        return DriverManager.getConnection(url, "stage2_test", "");
    }

    private static SessionFactory sessionFactory(String schema) {
        var config = new Configuration();
        config.setProperty("hibernate.connection.url", System.getenv("APEX_EMPLOYEE_INSIGHT_PG_URL")+"?currentSchema="+schema);
        config.setProperty("hibernate.connection.username", "stage2_test");
        config.setProperty("hibernate.connection.password", "");
        config.setProperty("hibernate.connection.pool_size", "30");
        config.setProperty("hibernate.hbm2ddl.auto", "none");
        config.setProperty("hibernate.default_schema", schema);
        config.setProperty("hibernate.physical_naming_strategy", "org.hibernate.boot.model.naming.CamelCaseToUnderscoresNamingStrategy");
        config.addAnnotatedClass(EmployeeInsightScope.class); config.addAnnotatedClass(EmployeeInsightGeneration.class);
        config.addAnnotatedClass(EmployeeInsightRequest.class); config.addAnnotatedClass(EmployeeInsightSubmissionLink.class);
        return config.buildSessionFactory();
    }

    private static void isolated(DatabaseWork work) throws Exception {
        try (var c = connect()) {
            c.setAutoCommit(false);
            try { String schema = newSchema(); prepare(c, schema); work.run(c, schema); }
            finally { c.rollback(); }
        }
    }

    private static String newSchema() { return "employee_insight_test_" + UUID.randomUUID().toString().replace("-", ""); }

    private static void prepare(Connection c, String schema) throws Exception {
        sql(c, "CREATE SCHEMA " + schema); sql(c, "SET LOCAL search_path TO " + schema);
        sql(c, "CREATE TABLE staff(id UUID PRIMARY KEY)");
        sql(c, "INSERT INTO staff VALUES ('" + EMPLOYEE + "'),('" + OTHER_EMPLOYEE + "'),('" + OWNER + "')");
        sql(c, "CREATE TABLE annual_kpi_review_period(id BIGINT PRIMARY KEY,start_date DATE,end_date DATE,attitude_configuration_id BIGINT)");
        sql(c, "INSERT INTO annual_kpi_review_period VALUES (1,'2022-01-01','2022-12-31',5),(2,'2021-01-01','2021-12-31',5)");
        sql(c, "CREATE TABLE review_period_participant(id BIGINT PRIMARY KEY,review_period_id BIGINT,staff_id UUID,department_id BIGINT,"
                + "UNIQUE(id,review_period_id,staff_id))");
        sql(c, "INSERT INTO review_period_participant VALUES (7,1,'" + EMPLOYEE + "',11),(8,1,'" + OTHER_EMPLOYEE + "',11),(6,2,'" + EMPLOYEE + "',11)");
        sql(c, "CREATE TABLE kpi_plan(id BIGINT PRIMARY KEY,review_period_id BIGINT,level TEXT,department_id BIGINT,owner_participant_id BIGINT)");
        sql(c, "INSERT INTO kpi_plan VALUES (10,1,'COMPANY',NULL,NULL),(20,1,'DEPARTMENT',11,NULL),(30,1,'INDIVIDUAL',NULL,7),(31,1,'INDIVIDUAL',NULL,8)");
        sql(c, "CREATE TABLE annual_appraisal(id BIGINT PRIMARY KEY,participant_id BIGINT,review_period_id BIGINT,employee_id UUID)");
        sql(c, "INSERT INTO annual_appraisal VALUES (70,7,1,'" + EMPLOYEE + "'),(80,8,1,'" + OTHER_EMPLOYEE + "')");
        sql(c, "CREATE TABLE annual_appraisal_activity(id BIGINT PRIMARY KEY,appraisal_id BIGINT,action TEXT)");
        sql(c, "INSERT INTO annual_appraisal_activity VALUES (700,70,'SUBMITTED'),(701,70,'DRAFT_SAVED'),(702,80,'RESUBMITTED'),(703,70,'RESUBMITTED')");
        sql(c, migration());
    }

    private static void scope(Connection c, long participant, UUID employee, String roots) throws Exception {
        try (var statement = c.prepareStatement("INSERT INTO appraisal_employee_insight_scope VALUES (?, ?, 1, true, 'disposable-stage2', 'verified fictional fixture', ?::jsonb)")) {
            statement.setLong(1, participant); statement.setObject(2, employee); statement.setString(3, roots); statement.executeUpdate();
        }
    }

    private static String rootsForOther() { return ROOTS.replace("\"INDIVIDUAL\":30", "\"INDIVIDUAL\":31").replace("[6]", "[]"); }
    private static UUID id(long id) { return new UUID(1, id); }
    private static String timestamp(long seconds) { return "'" + NOW.plusSeconds(seconds) + "'::timestamptz"; }

    private static void queued(Connection c, long generation, long participant, UUID employee) throws Exception {
        sql(c, "INSERT INTO " + GENERATION + "(id,participant_id,employee_id,review_period_id,owner_id,fingerprint,evidence,source_references,"
                + "model,template_version,data_mode,status,active_context,created_at,quota_day,lease_until) VALUES ('" + id(generation) + "',"
                + participant + ",'" + employee + "',1,'" + OWNER + "',repeat('a',64),'{}','{}','test-model','v1','FICTIONAL_UAT_EMPLOYEE','QUEUED',"
                + participant + "," + timestamp(0) + ",'2026-11-06'," + timestamp(600) + ")");
    }

    private static void running(Connection c, long generation) throws Exception {
        sql(c, "UPDATE " + GENERATION + " SET status='RUNNING',started_at=" + timestamp(1) + ",dispatched_at=" + timestamp(2)
                + ",lease_until=" + timestamp(90) + ",lock_version=lock_version+1 WHERE id='" + id(generation) + "'");
    }

    private static void completed(Connection c, long generation, int version) throws Exception {
        sql(c, "UPDATE " + GENERATION + " SET status='COMPLETED',active_context=NULL,lease_until=NULL,insight_version=" + version
                + ",summary='{}',completed_at=" + timestamp(3) + ",lock_version=lock_version+1 WHERE id='" + id(generation) + "'");
    }

    private static void receipt(Connection c, long request, long participant, long generation, long key) throws Exception {
        sql(c, "INSERT INTO appraisal_employee_insight_request VALUES ('" + id(request) + "','" + OWNER + "','" + id(key) + "',"
                + participant + ",'GENERATE','" + id(generation) + "','REUSED'," + timestamp(4) + ")");
    }

    private static void link(Connection c, long event, long appraisal, long generation, long participant) throws Exception {
        sql(c, "INSERT INTO appraisal_employee_insight_submission_link VALUES (" + event + "," + appraisal + ",'" + id(generation)
                + "'," + participant + "," + timestamp(4) + ")");
    }

    private static EmployeeInsightGeneration generation(UUID id, Long participant, UUID employee) throws Exception {
        var generation = new EmployeeInsightGeneration(); generation.setId(id); generation.setParticipantId(participant);
        generation.setEmployeeId(employee); generation.setReviewPeriodId(1L); generation.setOwnerId(OWNER);
        generation.setFingerprint("a".repeat(64)); generation.setModel("test-model"); generation.setTemplateVersion("v1");
        generation.setStatus("QUEUED"); generation.setActiveContext(participant); generation.setCreatedAt(NOW);
        generation.setQuotaDay(NOW.toLocalDate()); generation.setLeaseUntil(NOW.plusSeconds(600));
        generation.setSourceReferences(new ObjectMapper().readTree("{\"companyPlanId\":10}"));
        generation.setEvidence(new AppraisalInsightDto.Evidence("Fictional 2022", LocalDate.parse("2022-01-01"), LocalDate.parse("2022-12-31"),
                true, "ANNUALLY", "AVERAGE", new BigDecimal("50"), new BigDecimal("50"),
                new AppraisalInsightDto.Scores(BigDecimal.ZERO, new BigDecimal("80"), new BigDecimal("40")),
                List.of(new AppraisalInsightDto.Fact("f1", "COMPANY", "Recorded zero")), List.of(), List.of()));
        return generation;
    }

    private static void markRunning(EmployeeInsightGeneration generation) {
        generation.setStatus("RUNNING"); generation.setStartedAt(NOW.plusSeconds(1));
        generation.setDispatchedAt(NOW.plusSeconds(2)); generation.setLeaseUntil(NOW.plusSeconds(90));
    }

    private static AppraisalInsightDto.Summary summary() {
        var advisory = new AppraisalInsightDto.Statement("Advisory only", List.of("f1"));
        return new AppraisalInsightDto.Summary(List.of(advisory), List.of(), List.of(), List.of(),
                new AppraisalInsightDto.Considerations(advisory, advisory), List.of());
    }

    private static void sql(Connection c, String sql) throws SQLException { try (var s = c.createStatement()) { s.execute(sql); } }
    private static long count(Connection c, String sql) throws SQLException {
        try (var s = c.createStatement(); var result = s.executeQuery(sql)) { assertTrue(result.next()); return result.getLong(1); }
    }
    private static void reject(Connection c, String state, SqlWork work) throws Exception {
        var point = c.setSavepoint();
        assertEquals(state, assertThrows(SQLException.class, work::run).getSQLState()); c.rollback(point);
    }
    @FunctionalInterface private interface SqlWork { void run() throws Exception; }
    @FunctionalInterface private interface DatabaseWork { void run(Connection c, String schema) throws Exception; }
}
