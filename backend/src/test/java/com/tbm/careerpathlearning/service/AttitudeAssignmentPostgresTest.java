package com.tbm.careerpathlearning.service;

import com.tbm.careerpathlearning.dto.*;
import com.tbm.careerpathlearning.enums.*;
import com.tbm.careerpathlearning.exception.AttitudeAssignmentBlockedException;
import com.tbm.careerpathlearning.exception.BadRequestException;
import com.tbm.careerpathlearning.model.*;
import com.tbm.careerpathlearning.repository.EmployeeInsightScopeRepository;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.jpa.repository.Query;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.datasource.*;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.support.TransactionTemplate;
import java.net.URI;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.locks.LockSupport;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Service transactions against real PostgreSQL; never reads application credentials or databases. */
@EnabledIfEnvironmentVariable(named="APEX_ATTITUDE_POSTGRES_URL",matches=".+")
class AttitudeAssignmentPostgresTest {
    DriverManagerDataSource source; JdbcTemplate db,admin; TransactionTemplate tx;
    String schema; AttitudeAssignmentServiceTest f; ExecutorService workers;
    final List<CountDownLatch> releases=new ArrayList<>();
    @BeforeEach void setup() throws Exception {
        String url=System.getenv("APEX_ATTITUDE_POSTGRES_URL");var uri=URI.create(url.substring(5));
        assertThat(uri.getHost()).isIn("127.0.0.1","localhost");assertThat(uri.getPort()).isEqualTo(55461);
        assertThat(uri.getPath()).matches("/apex_attitude_test_[a-z0-9_]+");assertThat(uri.getQuery()).isNull();assertThat(uri.getUserInfo()).isNull();
        String user=Objects.requireNonNull(System.getenv("APEX_ATTITUDE_POSTGRES_USER"));
        admin=new JdbcTemplate(new DriverManagerDataSource(url,user,""));
        schema="assignment_"+UUID.randomUUID().toString().replace("-","");admin.execute("CREATE SCHEMA "+schema);
        source=new DriverManagerDataSource(url,user,"");var props=new Properties();
        props.setProperty("currentSchema",schema);props.setProperty("ApplicationName",schema);props.setProperty("options","-c lock_timeout=15000 -c statement_timeout=20000");source.setConnectionProperties(props);
        db=new JdbcTemplate(source);tx=new TransactionTemplate(new DataSourceTransactionManager(source));
        db.execute("CREATE TABLE staff(id uuid PRIMARY KEY,name text)");
        db.execute("CREATE TABLE attitude_configuration(id bigint PRIMARY KEY,name text)");
        db.execute("CREATE TABLE annual_kpi_review_period(id bigint PRIMARY KEY,status text,attitude_configuration_id bigint REFERENCES attitude_configuration(id),updated_by uuid,kpi_evidence text,UNIQUE(id,attitude_configuration_id))");
        db.execute("CREATE TABLE review_period_participant(id bigint PRIMARY KEY,review_period_id bigint REFERENCES annual_kpi_review_period(id))");
        db.execute("CREATE TABLE attitude_assessment(id bigint PRIMARY KEY,participant_id bigint,review_period_id bigint,configuration_id bigint,status text,FOREIGN KEY(review_period_id,configuration_id) REFERENCES annual_kpi_review_period(id,attitude_configuration_id))");
        db.execute("CREATE TABLE appraisal_employee_insight_scope(review_period_id bigint,source_roots jsonb)");
        f=new AttitudeAssignmentServiceTest();f.setup();
        db.update("INSERT INTO staff VALUES (?,?)",f.actor,"Setup Administrator");
        db.update("INSERT INTO attitude_configuration VALUES (1,'Original'),(2,'Corrected')");
        db.update("INSERT INTO annual_kpi_review_period VALUES (5,'OPEN',1,NULL,'unchanged KPI evidence')");
        db.update("INSERT INTO review_period_participant VALUES(10,5)");
        try(var connection=source.getConnection();var statement=connection.createStatement()) {
            statement.execute(new ClassPathResource("db/migration/annual-kpi/V56__attitude_configuration_assignment_activity.sql").getContentAsString(java.nio.charset.StandardCharsets.UTF_8));
        }
        when(f.periods.lockConfiguration()).thenAnswer(i->db.queryForObject("SELECT 1 FROM pg_advisory_xact_lock(20261006,1)",Integer.class));
        when(f.periods.findById(5L)).thenAnswer(i->Optional.of(period()));
        when(f.periods.saveAndFlush(any())).thenAnswer(i->{AnnualKpiReviewPeriod p=i.getArgument(0);db.update("UPDATE annual_kpi_review_period SET attitude_configuration_id=?,updated_by=? WHERE id=5",p.getAttitudeConfiguration().getId(),p.getUpdatedBy());return p;});
        when(f.configurations.period(eq(5L),eq(f.actor))).thenAnswer(i->{var p=period();var dto=new AttitudePeriodConfigurationDto();dto.setReviewPeriodId(5L);dto.setReviewPeriodStatus(p.getStatus());dto.setReviewPeriodName("Disposable test period");
            var c=new AttitudeConfigurationDto();c.setId(p.getAttitudeConfiguration().getId());c.setName(p.getAttitudeConfiguration().getName());dto.setConfiguration(c);dto.setUnmappedRoleNames(List.of());return dto;});
        when(f.assessments.findAllByReviewPeriodIdOrderByIdAsc(5L)).thenAnswer(i->db.query("SELECT * FROM attitude_assessment ORDER BY id",(r,n)->{
            var a=new AttitudeAssessment();a.setId(r.getLong("id"));a.setParticipant(f.participant);a.setStatus(AttitudeAssessmentStatus.valueOf(r.getString("status")));a.setConfiguration(r.getLong("configuration_id")==1?f.old:f.target);return a;}));
        when(f.audit.findByRequestId(any())).thenAnswer(i->history().stream().filter(a->a.getRequestId().equals(i.getArgument(0))).findFirst());
        when(f.audit.findAllByReviewPeriodIdOrderByIdAsc(5L)).thenAnswer(i->history());
        when(f.audit.saveAndFlush(any())).thenAnswer(i->{AttitudeConfigurationAssignmentActivity a=i.getArgument(0);
            Long id=db.queryForObject("INSERT INTO attitude_configuration_assignment_activity(request_id,review_period_id,previous_configuration_id,configuration_id,actor_id,recorded_at,reason) VALUES (?,?,?,?,?,?,?) RETURNING id",Long.class,
                    a.getRequestId(),a.getReviewPeriodId(),a.getPreviousConfiguration().getId(),a.getConfiguration().getId(),f.actor,a.getRecordedAt(),a.getReason());ReflectionTestUtils.setField(a,"id",id);return a;});
        String query=EmployeeInsightScopeRepository.class.getMethod("referencesReviewPeriod",Long.class).getAnnotation(Query.class).value();
        when(f.scopes.referencesReviewPeriod(5L)).thenAnswer(i->new NamedParameterJdbcTemplate(source).queryForObject(query,Map.of("periodId",5L),Boolean.class));
    }
    AnnualKpiReviewPeriod period() {
        return db.queryForObject("SELECT * FROM annual_kpi_review_period WHERE id=5",(r,n)->{
            var p=new AnnualKpiReviewPeriod();p.setId(5L);p.setStatus(AnnualKpiReviewPeriodStatus.valueOf(r.getString("status")));p.setAttitudeConfiguration(r.getLong("attitude_configuration_id")==1?f.old:f.target);p.setParticipantsSnapshottedAt(OffsetDateTime.now());return p;});
    }
    List<AttitudeConfigurationAssignmentActivity> history() {
        return db.query("SELECT * FROM attitude_configuration_assignment_activity ORDER BY id",(r,n)->{
            var a=new AttitudeConfigurationAssignmentActivity(r.getObject("request_id",UUID.class),r.getLong("review_period_id"),f.old,f.target,f.admin,r.getObject("recorded_at",OffsetDateTime.class),r.getString("reason"));ReflectionTestUtils.setField(a,"id",r.getLong("id"));return a;});
    }
    AttitudeAssignmentState update(AttitudeAssignmentRequest request) {return tx.execute(s->f.service.update(5L,request,f.actor));}
    int count() {return db.queryForObject("SELECT count(*) FROM attitude_configuration_assignment_activity",Integer.class);}
    @AfterEach void cleanup() throws Exception {
        releases.forEach(CountDownLatch::countDown);
        if(workers!=null) {workers.shutdownNow();assertThat(workers.awaitTermination(20,TimeUnit.SECONDS)).isTrue();}
        if(schema!=null) {assertThat(schema).matches("assignment_[a-f0-9]{32}");admin.execute("DROP SCHEMA "+schema+" CASCADE");}
    }
    @Test void migrationDoesNotRewriteExistingValuesAndAuditIsAppendOnly() {
        assertThat(db.queryForObject("SELECT kpi_evidence FROM annual_kpi_review_period",String.class)).isEqualTo("unchanged KPI evidence");assertThat(count()).isZero();
        update(f.request());assertThat(count()).isEqualTo(1);
        assertThatThrownBy(()->db.update("UPDATE attitude_configuration_assignment_activity SET reason='rewrite'")).hasMessageContaining("append-only");
        assertThatThrownBy(()->db.update("DELETE FROM attitude_configuration_assignment_activity")).hasMessageContaining("append-only");
        assertThat(db.queryForObject("SELECT name FROM attitude_configuration WHERE id=1",String.class)).isEqualTo("Original");
    }
    @Test void disabledServicePreservesExistingAssignmentAssessmentsAndAuditHistory() {
        var recordedRequest=f.request();update(recordedRequest);
        db.update("INSERT INTO attitude_assessment VALUES(21,10,5,2,'REVIEWED')");
        var before=db.queryForList("SELECT * FROM attitude_configuration_assignment_activity ORDER BY id");
        var periodBefore=db.queryForList("SELECT * FROM annual_kpi_review_period");
        var assessmentBefore=db.queryForList("SELECT * FROM attitude_assessment");
        f.service=f.createService(false);
        assertThatThrownBy(()->tx.execute(s->f.service.state(5L,f.actor))).isInstanceOf(BadRequestException.class);
        assertThatThrownBy(()->update(recordedRequest)).isInstanceOf(BadRequestException.class);
        assertThatThrownBy(()->update(f.request())).isInstanceOf(BadRequestException.class);
        assertThat(db.queryForList("SELECT * FROM attitude_configuration_assignment_activity ORDER BY id")).isEqualTo(before);
        assertThat(db.queryForList("SELECT * FROM annual_kpi_review_period")).isEqualTo(periodBefore);
        assertThat(db.queryForList("SELECT * FROM attitude_assessment")).isEqualTo(assessmentBefore);
    }
    @Test void twentyConcurrentIdenticalRequestsProduceOneAtomicCorrectionAndAudit() throws Exception {
        workers=Executors.newFixedThreadPool(20);var start=new CountDownLatch(1);releases.add(start);var ready=new CountDownLatch(20);var request=f.request();
        var results=new ArrayList<Future<AttitudeAssignmentState>>();
        for(int i=0;i<20;i++) results.add(workers.submit(()->{ready.countDown();assertThat(start.await(10,TimeUnit.SECONDS)).isTrue();return update(request);}));
        assertThat(ready.await(10,TimeUnit.SECONDS)).isTrue();start.countDown();int replayed=0;
        for(var result:results) if(result.get(25,TimeUnit.SECONDS).replayed()) replayed++;
        assertThat(replayed).isEqualTo(19);assertThat(count()).isEqualTo(1);assertThat(period().getAttitudeConfiguration().getId()).isEqualTo(2);
        assertThat(db.queryForObject("SELECT kpi_evidence FROM annual_kpi_review_period",String.class)).isEqualTo("unchanged KPI evidence");
    }
    @Test void competingDifferentCorrectionsRejectStaleRequestWithoutDuplicateAudit() throws Exception {
        workers=Executors.newFixedThreadPool(2);var start=new CountDownLatch(1);releases.add(start);
        Callable<Boolean> call=()->{start.await(10,TimeUnit.SECONDS);try {update(f.request());return true;}catch(AttitudeAssignmentBlockedException e){return false;}};
        var a=workers.submit(call);var b=workers.submit(call);start.countDown();assertThat(List.of(a.get(20,TimeUnit.SECONDS),b.get(20,TimeUnit.SECONDS))).containsExactlyInAnyOrder(true,false);assertThat(count()).isEqualTo(1);
    }
    @Test void auditInsertFailureRollsBackAssignmentAndActorTogether() {
        db.execute("CREATE FUNCTION reject_test_audit() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN RAISE EXCEPTION 'Injected audit failure'; END; $$");
        db.execute("CREATE TRIGGER reject_test_audit BEFORE INSERT ON attitude_configuration_assignment_activity FOR EACH ROW EXECUTE FUNCTION reject_test_audit()");
        assertThatThrownBy(()->update(f.request())).hasMessageContaining("Injected audit failure");
        assertThat(period().getAttitudeConfiguration().getId()).isEqualTo(1);assertThat(count()).isZero();
        assertThat(db.queryForObject("SELECT updated_by FROM annual_kpi_review_period",UUID.class)).isNull();
    }
    @Test void firstAssessmentWinsSharedLockAndBlocksCorrection() throws Exception {
        workers=Executors.newFixedThreadPool(2);var held=new CountDownLatch(1);var release=new CountDownLatch(1);releases.add(release);
        var writer=workers.submit(()->tx.execute(s->{f.periods.lockConfiguration();db.update("INSERT INTO attitude_assessment VALUES(21,10,5,1,'DRAFT')");held.countDown();await(release);return null;}));
        assertThat(held.await(10,TimeUnit.SECONDS)).isTrue();var correction=workers.submit(()->update(f.request()));awaitLockWaiter();release.countDown();writer.get(20,TimeUnit.SECONDS);
        assertThatThrownBy(()->correction.get(20,TimeUnit.SECONDS)).hasCauseInstanceOf(AttitudeAssignmentBlockedException.class);
        assertThat(period().getAttitudeConfiguration().getId()).isEqualTo(1);assertThat(count()).isZero();
        assertThatThrownBy(()->db.update("UPDATE annual_kpi_review_period SET attitude_configuration_id=2 WHERE id=5")).hasMessageContaining("foreign key");
    }
    @Test void correctionWinsLockAndStaleOldConfigurationInsertCannotCommit() throws Exception {
        workers=Executors.newFixedThreadPool(2);var held=new CountDownLatch(1);var release=new CountDownLatch(1);releases.add(release);
        var correction=workers.submit(()->tx.execute(s->{var result=f.service.update(5L,f.request(),f.actor);held.countDown();await(release);return result;}));
        assertThat(held.await(10,TimeUnit.SECONDS)).isTrue();var writer=workers.submit(()->tx.execute(s->{f.periods.lockConfiguration();db.update("INSERT INTO attitude_assessment VALUES(21,10,5,1,'DRAFT')");return null;}));
        awaitLockWaiter();release.countDown();correction.get(20,TimeUnit.SECONDS);
        assertThatThrownBy(()->writer.get(20,TimeUnit.SECONDS)).hasMessageContaining("foreign key");assertThat(count()).isEqualTo(1);
        assertThat(db.queryForObject("SELECT count(*) FROM attitude_assessment",Integer.class)).isZero();
    }
    @Test void actualRegistryQueryProtectsBothDirectAndHistoricalFixtureReferences() {
        db.update("INSERT INTO appraisal_employee_insight_scope VALUES(5,'{\"historicalParticipantIds\":[]}'::jsonb)");
        assertThatThrownBy(()->update(f.request())).isInstanceOf(AttitudeAssignmentBlockedException.class);assertThat(count()).isZero();
        db.update("UPDATE appraisal_employee_insight_scope SET review_period_id=99,source_roots='{\"historicalParticipantIds\":[10]}'::jsonb");
        assertThatThrownBy(()->update(f.request())).isInstanceOf(AttitudeAssignmentBlockedException.class);assertThat(count()).isZero();
    }
    @Test void closureWinningSharedLockRejectsAWaitingCorrection() throws Exception {
        workers=Executors.newFixedThreadPool(2);var held=new CountDownLatch(1);var release=new CountDownLatch(1);releases.add(release);
        var closer=workers.submit(()->tx.execute(s->{f.periods.lockConfiguration();db.update("UPDATE annual_kpi_review_period SET status='CLOSED' WHERE id=5");held.countDown();await(release);return null;}));
        assertThat(held.await(10,TimeUnit.SECONDS)).isTrue();var correction=workers.submit(()->update(f.request()));awaitLockWaiter();release.countDown();closer.get(20,TimeUnit.SECONDS);
        assertThatThrownBy(()->correction.get(20,TimeUnit.SECONDS)).hasCauseInstanceOf(AttitudeAssignmentBlockedException.class);
        assertThat(period().getStatus()).isEqualTo(AnnualKpiReviewPeriodStatus.CLOSED);assertThat(count()).isZero();
    }
    void await(CountDownLatch latch) {try {if(!latch.await(10,TimeUnit.SECONDS))throw new AssertionError("Barrier expired");}catch(InterruptedException e){Thread.currentThread().interrupt();throw new AssertionError(e);}}
    void awaitLockWaiter() {
        long end=System.nanoTime()+TimeUnit.SECONDS.toNanos(10);
        while(System.nanoTime()<end) {if(admin.queryForObject("SELECT count(*) FROM pg_locks WHERE locktype='advisory' AND NOT granted",Integer.class)>0)return;LockSupport.parkNanos(TimeUnit.MILLISECONDS.toNanos(2));}
        throw new AssertionError("Expected the competing transaction to wait on the shared configuration lock");
    }
}
