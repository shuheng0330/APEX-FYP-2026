package com.tbm.careerpathlearning.service.appraisalinsight;

import com.tbm.careerpathlearning.config.AppraisalInsightConfig;
import com.tbm.careerpathlearning.service.GeminiClient;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.*;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.reactive.function.client.*;
import reactor.core.publisher.Mono;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import static org.junit.jupiter.api.Assertions.*;

/** A private disposable PostgreSQL cluster and mocked HTTP transport; never application credentials. */
@EnabledIfEnvironmentVariable(named="APEX_EMPLOYEE_INSIGHT_PG_URL",matches=".+")
class GeminiDispatchGatePostgresTest {
    static final String MODEL="gemini-test";
    String schema,url;JdbcTemplate db,admin;
    DataSourceTransactionManager manager;
    GeminiDispatchSettings settings;
    final AtomicReference<Instant> instant=new AtomicReference<>(Instant.parse("2026-11-06T01:00:00Z"));
    final List<SharedAppraisalExecutor> executors=new ArrayList<>();
    @BeforeEach void setup() throws Exception {
        url=System.getenv("APEX_EMPLOYEE_INSIGHT_PG_URL");var uri=URI.create(url.substring(5));
        assertEquals("127.0.0.1",uri.getHost());assertEquals(55439,uri.getPort());assertEquals("/apex_ai_stage2_disposable",uri.getPath());
        admin=new JdbcTemplate(new DriverManagerDataSource(url,"stage2_test",""));
        schema="gemini_gate_test_"+UUID.randomUUID().toString().replace("-","");admin.execute("create schema "+schema);
        var source=new DriverManagerDataSource(url+"?currentSchema="+schema,"stage2_test","");db=new JdbcTemplate(source);manager=new DataSourceTransactionManager(source);
        db.execute(new ClassPathResource("db/migration/annual-kpi/V54__shared_gemini_dispatch.sql").getContentAsString(StandardCharsets.UTF_8));
        // No performance data: only columns needed to verify recovery's conditional status updates.
        db.execute("create table appraisal_employee_insight_generation(id uuid primary key,status text,error_code text,active_context bigint,lease_until timestamptz,completed_at timestamptz,started_at timestamptz,created_at timestamptz,lock_version bigint default 0)");
        db.execute("create table appraisal_insight_generation(id uuid primary key,status text,error_code text,active_key text,completed_at timestamptz,started_at timestamptz,created_at timestamptz,lock_version bigint default 0)");
        settings=config(2);
    }
    @AfterEach void cleanup() throws Exception {
        for (var executor:executors) {
            var pool=(org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor)ReflectionTestUtils.getField(executor,"workers");
            executor.shutdown();assertTrue(pool.getThreadPoolExecutor().awaitTermination(15,TimeUnit.SECONDS));
        }
        if (schema!=null) {assertTrue(schema.matches("gemini_gate_test_[0-9a-f]{32}"));admin.execute("drop schema "+schema+" cascade");}
    }
    GeminiDispatchSettings config(int workers) {
        var s=new GeminiDispatchSettings();set(s,"enabled",true);set(s,"projectKey","disposable-project");set(s,"model",MODEL);
        set(s,"workers",workers);set(s,"queueCapacity",8);set(s,"queueWaitSeconds",600);
        set(s,"requestsPerMinute",1000);set(s,"tokensPerMinute",10_000_000L);set(s,"requestsPerDay",10000);return s;
    }
    void set(Object target,String name,Object value) {ReflectionTestUtils.setField(target,name,value);}
    OffsetDateTime now() {return instant.get().atOffset(ZoneOffset.UTC);}
    void advance(long seconds) {instant.updateAndGet(t->t.plusSeconds(seconds));}
    GeminiDispatchGate gate(GeminiDispatchSettings s) {return new GeminiDispatchGate(db,manager,s,this::now);}
    SharedAppraisalExecutor executor(GeminiDispatchGate gate,GeminiDispatchSettings s) {
        var executor=new AppraisalInsightConfig().appraisalInsightExecutor(gate,s);executors.add(executor);return executor;
    }
    AppraisalGenerationTask task(UUID id,long tokens) {return new AppraisalGenerationTask(id,"EMPLOYEE",MODEL,tokens,()->{},code->{});}
    GeminiClient client(GeminiDispatchGate gate,ExchangeFunction response) {
        var client=new GeminiClient();set(client,"webClient",WebClient.builder().exchangeFunction(response).build());
        set(client,"coordination",gate);set(client,"apiKey","fictional-test-key");set(client,"model",MODEL);set(client,"baseUrl","https://not-contacted.invalid");return client;
    }
    ClientResponse response() {return ClientResponse.create(HttpStatus.OK).header("Content-Type","application/json").body(
            "{\"usageMetadata\":{\"promptTokenCount\":40},\"candidates\":[{\"finishReason\":\"STOP\",\"content\":{\"parts\":[{\"text\":\"{}\"}]}}]}").build();}

    @ParameterizedTest @ValueSource(ints={2,3,5})
    void differentAppraisalsOverlapAcrossTwoInstancesWithEightWaitingAndNoExcessDispatch(int workers) throws Exception {
        settings=config(workers);var gate1=gate(settings);var gate2=gate(config(workers));
        var first=executor(gate1,settings);var second=executor(gate2,settings);
        var entered=new CountDownLatch(workers);var release=new CountDownLatch(1);var active=new AtomicInteger();var peak=new AtomicInteger();var calls=new AtomicInteger();
        ExchangeFunction http=request->Mono.fromCallable(()->{
            int running=active.incrementAndGet();peak.accumulateAndGet(running,Math::max);calls.incrementAndGet();entered.countDown();
            try {assertTrue(release.await(30,TimeUnit.SECONDS));return response();}finally {active.decrementAndGet();}
        });
        var client1=client(gate1,http);var client2=client(gate2,http);var completed=new CountDownLatch(workers+8);var failures=new ConcurrentLinkedQueue<Throwable>();
        int refused=0;var timer=Executors.newSingleThreadScheduledExecutor();
        try {
            for (int i=0;i<20;i++) {
                var local=i%2==0?first:second;var provider=i%2==0?client1:client2;String prompt="Fictional appraisal "+i;
                try {local.execute(new AppraisalGenerationTask(UUID.randomUUID(),i%2==0?"EMPLOYEE":"SYNTHETIC",MODEL,provider.appraisalTokenReservation(prompt),()->{
                    try {assertEquals("{}",provider.generateAppraisalJson(prompt));}catch(Throwable failure) {failures.add(failure);}finally {completed.countDown();}
                },code->failures.add(new AssertionError(code))));}
                catch (GeminiDispatchGate.Rejected busy) {assertEquals("BUSY",busy.code());refused++;}
            }
            assertTrue(entered.await(20,TimeUnit.SECONDS));assertEquals(20-workers-8,refused);
            assertEquals(workers,gate1.counts(MODEL).active());assertEquals(8,gate2.counts(MODEL).queued());assertEquals(workers,peak.get());
            release.countDown();timer.scheduleWithFixedDelay(()->{first.dispatchWaiting();second.dispatchWaiting();},0,10,TimeUnit.MILLISECONDS);
            assertTrue(completed.await(30,TimeUnit.SECONDS));assertTrue(failures.isEmpty(),failures.toString());
            assertEquals(workers+8,calls.get());assertEquals(workers,peak.get());
        } finally {release.countDown();timer.shutdownNow();assertTrue(timer.awaitTermination(10,TimeUnit.SECONDS));}
    }

    @Test void rateReservationsAreAtomicAcrossInstancesAndMinuteBoundaryIsExact() {
        set(settings,"requestsPerMinute",1);var one=gate(settings);var two=gate(settings);
        var first=one.enqueue(task(UUID.randomUUID(),100),UUID.randomUUID()).permit();assertNotNull(first);
        one.begin(first,100,()->{});one.finishPermit(first,100L,true);one.finishWork(first.workId(),MODEL);
        var instance=UUID.randomUUID();var queued=task(UUID.randomUUID(),100);assertNull(two.enqueue(queued,instance).permit());
        advance(59);assertEquals("MINUTE_LIMIT",two.claim(queued.id(),instance,MODEL).reason());
        advance(1);assertNotNull(two.claim(queued.id(),instance,MODEL).permit());
    }
    @Test void tokenReservationsReconcileKnownUsageButUnknownDispatchRetainsItsCharge() {
        set(settings,"tokensPerMinute",100L);var gate=gate(settings);
        var first=gate.enqueue(task(UUID.randomUUID(),90),UUID.randomUUID()).permit();gate.begin(first,90,()->{});
        gate.finishPermit(first,10L,true);gate.finishWork(first.workId(),MODEL);
        var second=gate.enqueue(task(UUID.randomUUID(),80),UUID.randomUUID()).permit();assertNotNull(second);
        gate.begin(second,80,()->{});gate.finishPermit(second,null,false);gate.finishWork(second.workId(),MODEL);
        assertEquals("UNCERTAIN",db.queryForObject("select phase from gemini_request_permit where id=?",String.class,second.id()));
        var instance=UUID.randomUUID();var waiting=task(UUID.randomUUID(),20);assertNull(gate.enqueue(waiting,instance).permit());
        assertEquals("MINUTE_LIMIT",gate.claim(waiting.id(),instance,MODEL).reason());
    }
    @Test void providerDailyResetUsesPacificTimeRatherThanAppraisalBusinessMidnight() {
        set(settings,"requestsPerDay",1);var gate=gate(settings);instant.set(Instant.parse("2026-11-06T07:59:59Z"));
        var permit=gate.enqueue(task(UUID.randomUUID(),100),UUID.randomUUID()).permit();gate.begin(permit,100,()->{});gate.finishPermit(permit,null,true);gate.finishWork(permit.workId(),MODEL);
        var refused=assertThrows(GeminiDispatchGate.Rejected.class,()->gate.enqueue(task(UUID.randomUUID(),100),UUID.randomUUID()));
        assertEquals(Instant.parse("2026-11-06T08:00:00Z"),refused.retryAt().toInstant());advance(2);
        assertNotNull(gate.enqueue(task(UUID.randomUUID(),100),UUID.randomUUID()).permit());
    }
    @Test void sharedBackoffBlocksAnotherInstanceAndSopAndDoesNotCancelRunningCalls() {
        var one=gate(settings);var two=gate(settings);var permit=one.enqueue(task(UUID.randomUUID(),100),UUID.randomUUID()).permit();one.begin(permit,100,()->{});
        one.providerRateLimited(MODEL,new GeminiClient.AppraisalProviderException("RATE_LIMITED","120"));
        assertEquals(now().plusSeconds(120).toInstant(),two.retryAt(MODEL).toInstant());assertEquals(1,two.counts(MODEL).active());
        var instance=UUID.randomUUID();var task=task(UUID.randomUUID(),100);assertNull(two.enqueue(task,instance).permit());
        assertEquals("PROVIDER_BACKOFF",two.claim(task.id(),instance,MODEL).reason());
        one.finishPermit(permit,null,true);one.finishWork(permit.workId(),MODEL);advance(120);
        assertNotNull(two.claim(task.id(),instance,MODEL).permit());
    }
    @Test void sopAndAppraisalShareProviderBudgetsWithoutSharingWorkerSlots() {
        set(settings,"requestsPerMinute",1);var gate=gate(settings);var provider=client(gate,request->Mono.just(response()));
        assertEquals("{}",provider.generateJson("Fictional SOP evidence"));
        var instance=UUID.randomUUID();var task=task(UUID.randomUUID(),100);assertNull(gate.enqueue(task,instance).permit());
        assertEquals("MINUTE_LIMIT",gate.claim(task.id(),instance,MODEL).reason());assertEquals(0,gate.counts(MODEL).active());
    }
    @Test void callbacksAreRolledBackWithRefusedDispatchAndNoProviderAttemptIsCharged() {
        var gate=gate(settings);var permit=gate.enqueue(task(UUID.randomUUID(),100),UUID.randomUUID()).permit();
        assertThrows(IllegalStateException.class,()->gate.begin(permit,100,()->{db.update("update gemini_quota_scope set retry_until=?",now().plusDays(1));throw new IllegalStateException("Source invalid");}));
        assertNull(gate.retryAt(MODEL));assertNull(db.queryForObject("select dispatched_at from gemini_request_permit where id=?",OffsetDateTime.class,permit.id()));
        gate.finishPermit(permit,null,false);gate.finishWork(permit.workId(),MODEL);
        assertEquals("RELEASED",db.queryForObject("select phase from gemini_request_permit where id=?",String.class,permit.id()));
    }
    @Test void simultaneousSopAndAppraisalTrafficSharesAdmissionAcrossInstances() throws Exception {
        set(settings,"requestsPerMinute",2);var one=gate(settings);var two=gate(settings);
        var executor=executor(one,settings);var entered=new CountDownLatch(2);var release=new CountDownLatch(1);var finished=new CountDownLatch(1);
        var errors=new ConcurrentLinkedQueue<Throwable>();var calls=new AtomicInteger();
        ExchangeFunction http=request->Mono.fromCallable(()->{
            calls.incrementAndGet();entered.countDown();assertTrue(release.await(20,TimeUnit.SECONDS));return response();
        });
        var appraisal=client(one,http);var sop=client(two,http);var sopWorker=Executors.newSingleThreadExecutor();
        try {
            var sopResult=sopWorker.submit(()->sop.generateJson("Fictional SOP evidence"));String prompt="Fictional appraisal evidence";
            executor.execute(new AppraisalGenerationTask(UUID.randomUUID(),"EMPLOYEE",MODEL,appraisal.appraisalTokenReservation(prompt),()->{
                try {assertEquals("{}",appraisal.generateAppraisalJson(prompt));}catch(Throwable error) {errors.add(error);}finally {finished.countDown();}
            },code->errors.add(new AssertionError(code))));
            assertTrue(entered.await(20,TimeUnit.SECONDS));assertEquals(2,calls.get());assertEquals(1,one.counts(MODEL).active());
            var instance=UUID.randomUUID();var waiting=task(UUID.randomUUID(),100);assertNull(two.enqueue(waiting,instance).permit());
            assertEquals("MINUTE_LIMIT",one.claim(waiting.id(),instance,MODEL).reason());
            release.countDown();assertEquals("{}",sopResult.get(20,TimeUnit.SECONDS));assertTrue(finished.await(20,TimeUnit.SECONDS));assertTrue(errors.isEmpty(),errors.toString());
            assertEquals(2,db.queryForObject("select count(*) from gemini_request_permit where dispatched_at is not null",Long.class));
        } finally {release.countDown();sopWorker.shutdownNow();assertTrue(sopWorker.awaitTermination(20,TimeUnit.SECONDS));}
    }
    @Test void missingOrInconsistentSettingsFailClosedWithoutChangingExistingBudgets() {
        var gate=gate(settings);var permit=gate.enqueue(task(UUID.randomUUID(),100),UUID.randomUUID()).permit();assertNotNull(permit);
        var other=config(3);assertThrows(GeminiDispatchGate.Rejected.class,()->gate(other).enqueue(task(UUID.randomUUID(),100),UUID.randomUUID()));
        set(other,"enabled",false);assertThrows(GeminiDispatchGate.Rejected.class,()->gate(other).enqueue(task(UUID.randomUUID(),100),UUID.randomUUID()));
        assertEquals(2,db.queryForObject("select workers from gemini_quota_scope",Integer.class));
    }
    @Test void tuningWorkersRequiresAForwardConfigurationRevisionAndDrainedWork() {
        var old=gate(settings);var permit=old.enqueue(task(UUID.randomUUID(),100),UUID.randomUUID()).permit();
        var changed=config(5);set(changed,"configRevision",2L);var next=gate(changed);
        assertThrows(GeminiDispatchGate.Rejected.class,()->next.enqueue(task(UUID.randomUUID(),100),UUID.randomUUID()));
        old.finishPermit(permit,null,false);old.finishWork(permit.workId(),MODEL);
        assertNotNull(next.enqueue(task(UUID.randomUUID(),100),UUID.randomUUID()).permit());
        assertEquals(5,db.queryForObject("select workers from gemini_quota_scope",Integer.class));
        assertThrows(GeminiDispatchGate.Rejected.class,()->old.enqueue(task(UUID.randomUUID(),100),UUID.randomUUID()));
    }
    @Test void everyExistingSopRetryAttemptUsesTheSameSharedGateWithoutAdditionalRetries() {
        var gate=gate(settings);var calls=new AtomicInteger();var pauses=new ArrayList<Long>();
        var provider=new GeminiClient() {
            @Override protected void waitBeforeSopRetry(long seconds) { pauses.add(seconds);advance(200); }
        };
        set(provider,"coordination",gate);set(provider,"apiKey","fictional-test-key");set(provider,"model",MODEL);set(provider,"baseUrl","https://not-contacted.invalid");
        set(provider,"webClient",WebClient.builder().exchangeFunction(request->{
            int attempt=calls.incrementAndGet();
            if (attempt<3) return Mono.just(ClientResponse.create(attempt==1?HttpStatus.TOO_MANY_REQUESTS:HttpStatus.SERVICE_UNAVAILABLE)
                    .header("Retry-After","120").body("fictional failure").build());
            return Mono.just(response());
        }).build());
        assertEquals("{}",provider.generateJson("Fictional SOP evidence"));assertEquals(3,calls.get());assertEquals(List.of(30L,60L),pauses);
        assertEquals(3,db.queryForObject("select count(*) from gemini_request_permit where dispatched_at is not null",Long.class));
        assertEquals(2,gate.counts(MODEL).failures());
    }
    @Test void anAppraisalProvider429PersistsSharedBackoffAndNeverRetriesTheCall() throws Exception {
        var gate=gate(settings);var executor=executor(gate,settings);var calls=new AtomicInteger();var finished=new CountDownLatch(1);
        var provider=client(gate,request->{calls.incrementAndGet();return Mono.just(ClientResponse.create(HttpStatus.TOO_MANY_REQUESTS)
                .header("Retry-After","120").body("fictional response").build());});
        String prompt="Fictional appraisal";
        executor.execute(new AppraisalGenerationTask(UUID.randomUUID(),"EMPLOYEE",MODEL,provider.appraisalTokenReservation(prompt),()->{
            try {assertEquals("RATE_LIMITED",assertThrows(GeminiClient.AppraisalProviderException.class,()->provider.generateAppraisalJson(prompt)).code());}
            finally {finished.countDown();}
        },code->fail(code)));
        assertTrue(finished.await(20,TimeUnit.SECONDS));assertEquals(1,calls.get());
        assertEquals(now().plusSeconds(120).toInstant(),gate.retryAt(MODEL).toInstant());
        assertEquals(1,db.queryForObject("select count(*) from gemini_request_permit where dispatched_at is not null",Long.class));
        assertEquals(1,gate.counts(MODEL).failures());
    }
    @Test void expiryReleasesUndispatchedCapacityAndRetainsUncertainDispatchedUsageWithoutReplay() {
        var gate=gate(settings);var queuedId=UUID.randomUUID();
        db.update("insert into appraisal_employee_insight_generation(id,status,created_at) values(?,'QUEUED',?)",queuedId,now());
        var first=gate.enqueue(task(queuedId,100),UUID.randomUUID()).permit();
        var running=gate.enqueue(task(UUID.randomUUID(),100),UUID.randomUUID()).permit();gate.begin(running,100,()->{});
        advance(181);gate.recover(MODEL);
        assertEquals(0,gate.counts(MODEL).active());assertEquals("RELEASED",db.queryForObject("select phase from gemini_request_permit where id=?",String.class,first.id()));
        assertEquals("UNCERTAIN",db.queryForObject("select phase from gemini_request_permit where id=?",String.class,running.id()));
        assertEquals("FAILED",db.queryForObject("select status from appraisal_employee_insight_generation where id=?",String.class,queuedId));
        assertNotNull(db.queryForObject("select dispatched_at from gemini_request_permit where id=?",OffsetDateTime.class,running.id()));
        assertFalse(gate.waiting(queuedId));
    }
    @Test void workerLimitIsSharedEvenAcrossDifferentModelsAndCannotReuseAPermit() {
        var gate=gate(settings);var first=gate.enqueue(task(UUID.randomUUID(),100),UUID.randomUUID()).permit();
        var another=new AppraisalGenerationTask(UUID.randomUUID(),"SYNTHETIC","other-model",100,()->{},code->{});
        var second=gate.enqueue(another,UUID.randomUUID()).permit();assertNotNull(second);
        assertNull(gate.enqueue(task(UUID.randomUUID(),100),UUID.randomUUID()).permit());
        gate.begin(first,100,()->{});assertThrows(GeminiClient.AppraisalProviderException.class,()->gate.begin(first,100,()->{}));
    }
    @Test void expiredWaitingJobsReleaseCapacityWithoutChargingOrReplaying() {
        var gate=gate(settings);
        gate.enqueue(task(UUID.randomUUID(),100),UUID.randomUUID());gate.enqueue(task(UUID.randomUUID(),100),UUID.randomUUID());
        var id=UUID.randomUUID();db.update("insert into appraisal_employee_insight_generation(id,status,created_at) values(?,'QUEUED',?)",id,now());
        assertNull(gate.enqueue(task(id,100),UUID.randomUUID()).permit());assertEquals(1,gate.counts(MODEL).queued());
        advance(601);gate.recover(MODEL);
        assertEquals(0,gate.counts(MODEL).queued());assertEquals(0,gate.counts(MODEL).active());
        assertEquals("FAILED",db.queryForObject("select status from appraisal_employee_insight_generation where id=?",String.class,id));
        assertEquals(0,db.queryForObject("select count(*) from gemini_request_permit where dispatched_at is not null",Long.class));
        assertNotNull(gate.enqueue(task(UUID.randomUUID(),100),UUID.randomUUID()).permit());
    }
}
