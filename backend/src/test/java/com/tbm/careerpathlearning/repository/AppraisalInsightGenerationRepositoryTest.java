package com.tbm.careerpathlearning.repository;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tbm.careerpathlearning.dto.AppraisalInsightDto.*;
import com.tbm.careerpathlearning.enums.*;
import com.tbm.careerpathlearning.model.*;
import com.tbm.careerpathlearning.service.appraisalinsight.AppraisalInsightTemplate;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.*;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;

@DataJpaTest(showSql = false, properties = {
        "spring.datasource.url=jdbc:h2:mem:appraisal-insight;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver", "spring.datasource.username=sa", "spring.datasource.password=",
        "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect", "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.flyway.enabled=false", "spring.sql.init.mode=never", "spring.jpa.show-sql=false"
})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class AppraisalInsightGenerationRepositoryTest {
    @Autowired AppraisalInsightGenerationRepository repository;
    @Autowired TestEntityManager em;
    @Autowired PlatformTransactionManager transactions;
    final AppraisalInsightTemplate template = new AppraisalInsightTemplate(new ObjectMapper().findAndRegisterModules());
    final Set<AuthorityName> permissions = Set.of(AuthorityName.CAN_SUBMIT_ANNUAL_APPRAISAL, AuthorityName.CAN_REVIEW_ANNUAL_APPRAISAL);

    @Test void jsonEvidenceAndSummaryRoundTripWithoutChangingRecordedNumbers() {
        var generation = generation(UUID.randomUUID());
        repository.saveAndFlush(generation); assertEquals(0, generation.getLockVersion());
        generation.setStatus("RUNNING"); generation.setStartedAt(generation.getCreatedAt()); repository.saveAndFlush(generation);
        var statement = new Statement("Evidence is insufficient.", List.of("GAPS"));
        var summary = new Summary(List.of(statement), List.of(statement), List.of(statement), List.of(statement),
                new Considerations(statement, statement), List.of(statement));
        generation.setSummary(summary); generation.setStatus("COMPLETED"); generation.setCompletedAt(generation.getCreatedAt());
        generation.setActiveKey(null); repository.saveAndFlush(generation); em.clear();
        var loaded = repository.findByOwnerIdAndRequestKey(generation.getOwnerId(), generation.getRequestKey()).orElseThrow();
        assertEquals(2, loaded.getLockVersion()); assertEquals(summary, loaded.getSummary());
        assertEquals(0, new java.math.BigDecimal("80").compareTo(loaded.getEvidence().scores().kpi()));
        assertEquals(generation.getEvidenceHash(), loaded.getEvidenceHash());
    }
    @Test void uniqueRequestKeyIsEnforcedByTheDatabaseRatherThanOnlyTheCoordinator() {
        var first = generation(UUID.randomUUID()); repository.saveAndFlush(first);
        var duplicate = generation(first.getOwnerId()); duplicate.setRequestKey(first.getRequestKey()); duplicate.setActiveKey(null);
        assertThrows(DataIntegrityViolationException.class, () -> repository.saveAndFlush(duplicate));
    }
    @Test void uniqueActiveKeyEnforcesOnePendingRequestPerOwner() {
        var first = generation(UUID.randomUUID()); repository.saveAndFlush(first);
        var duplicate = generation(first.getOwnerId());
        assertThrows(DataIntegrityViolationException.class, () -> repository.saveAndFlush(duplicate));
    }
    @Test void decimalAndHistoricalEvidenceRoundTripForEveryScenario() {
        for (var scenario : AppraisalInsightTemplate.ScenarioId.values()) {
            var generation = generation(UUID.randomUUID()); var evidence = template.evidence(scenario);
            generation.setScenario(scenario.name()); generation.setEvidence(evidence); generation.setEvidenceHash(template.hash(evidence));
            repository.saveAndFlush(generation); em.clear();
            var recorded = repository.findById(generation.getId()).orElseThrow();
            assertEquals(0, evidence.scores().finalAppraisal().compareTo(recorded.getEvidence().scores().finalAppraisal()));
            assertEquals(template.hash(evidence), template.hash(recorded.getEvidence()));
            assertEquals(evidence.history().size(), recorded.getEvidence().history().size());
        }
    }
    @Test void capturedEvidenceIsNotOverwrittenByJpaStatusUpdates() {
        var generation = generation(UUID.randomUUID()); repository.saveAndFlush(generation);
        String recordedHash = generation.getEvidenceHash();
        generation.setEvidence(template.evidence(AppraisalInsightTemplate.ScenarioId.MIXED_RESULTS)); generation.setEvidenceHash("changed");
        generation.setStatus("RUNNING"); generation.setStartedAt(generation.getCreatedAt()); repository.saveAndFlush(generation); em.clear();
        var recorded = repository.findById(generation.getId()).orElseThrow();
        assertEquals(recordedHash, recorded.getEvidenceHash());
        assertEquals(0, new java.math.BigDecimal("80").compareTo(recorded.getEvidence().scores().kpi()));
    }
    @Test @Timeout(15) void concurrentTransactionsCannotCreateTwoActiveRequestsForTheSameOwner() throws Exception {
        UUID owner = UUID.randomUUID(); var barrier = new CyclicBarrier(2); var pool = Executors.newFixedThreadPool(2);
        try {
            Callable<Boolean> insert = () -> {
                barrier.await();
                try { new TransactionTemplate(transactions).execute(s -> repository.saveAndFlush(generation(owner))); return true; }
                catch (DataIntegrityViolationException duplicate) { return false; }
            };
            var first = pool.submit(insert); var second = pool.submit(insert);
            assertNotEquals(first.get(10, TimeUnit.SECONDS), second.get(10, TimeUnit.SECONDS));
        } finally {
            pool.shutdownNow();
            assertTrue(pool.awaitTermination(5, TimeUnit.SECONDS));
            // Worker transactions commit independently of this test's rollback.
            var cleanup = new TransactionTemplate(transactions);
            cleanup.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
            cleanup.executeWithoutResult(s -> repository.deleteAll(repository.findAll().stream()
                    .filter(g -> owner.equals(g.getOwnerId())).toList()));
        }
    }
    @Test void restartingMarksUnfinishedHistoryFailedWithoutRegeneratingIt() {
        var generation = generation(UUID.randomUUID()); repository.saveAndFlush(generation); em.clear();
        assertEquals(1, repository.interruptUnfinished(generation.getCreatedAt().plusSeconds(30), generation.getCreatedAt().plusMinutes(1))); em.clear();
        var loaded = repository.findById(generation.getId()).orElseThrow();
        assertEquals("FAILED", loaded.getStatus()); assertEquals("INTERRUPTED", loaded.getErrorCode());
        assertNull(loaded.getActiveKey()); assertNull(loaded.getSummary()); assertEquals(1, loaded.getLockVersion());
    }
    @Test void startupRecoveryDoesNotInterruptRequestsAcceptedDuringCurrentStartup() {
        var startup = OffsetDateTime.now(ZoneOffset.UTC).truncatedTo(java.time.temporal.ChronoUnit.MICROS);
        var old = generation(UUID.randomUUID()); old.setCreatedAt(startup.minusMinutes(1));
        var atStartup = generation(UUID.randomUUID()); atStartup.setCreatedAt(startup);
        var fresh = generation(UUID.randomUUID()); fresh.setCreatedAt(startup.plusSeconds(1));
        fresh.setStatus("RUNNING"); fresh.setStartedAt(startup.plusSeconds(2));
        repository.saveAllAndFlush(List.of(old, atStartup, fresh)); em.clear();
        assertEquals(1, repository.interruptUnfinished(startup, startup.plusSeconds(5))); em.clear();
        assertEquals("FAILED", repository.findById(old.getId()).orElseThrow().getStatus());
        assertEquals("QUEUED", repository.findById(atStartup.getId()).orElseThrow().getStatus());
        var preserved = repository.findById(fresh.getId()).orElseThrow();
        assertEquals("RUNNING", preserved.getStatus()); assertNull(preserved.getCompletedAt());
        assertNotNull(preserved.getActiveKey()); assertEquals(0, preserved.getLockVersion());
    }
    @Test void permissionProjectionChecksCurrentRoleAccountAndDeletionWithoutLoadingAppraisals() {
        var role = new Role("Fictional preview role", "Test only", true, false);
        role.setCreatedAt(OffsetDateTime.now()); role.setUpdatedAt(OffsetDateTime.now()); em.persist(role);
        var staff = new Staff(UUID.randomUUID(), "Fictional reviewer", "fictional@example.test", role);
        staff.setAccountStatus(StaffAccountStatus.ACTIVE); staff.setCreatedAt(OffsetDateTime.now()); staff.setUpdatedAt(OffsetDateTime.now()); em.persist(staff);
        em.flush(); assertEquals(0, repository.countAuthorisedPreviewAccounts(staff.getId(), permissions));
        var authority = new Authority(AuthorityName.CAN_REVIEW_ANNUAL_APPRAISAL, "test"); authority.setLabelKey("test"); em.persist(authority);
        var assignment = new RoleAuthority(role, authority); em.persist(assignment); em.flush();
        assertEquals(1, repository.countAuthorisedPreviewAccounts(staff.getId(), permissions));
        role.setDeleted(true); em.flush(); assertEquals(0, repository.countAuthorisedPreviewAccounts(staff.getId(), permissions));
        role.setDeleted(false); staff.setDeleted(true); em.flush(); assertEquals(0, repository.countAuthorisedPreviewAccounts(staff.getId(), permissions));
        staff.setDeleted(false); staff.setAccountStatus(StaffAccountStatus.INACTIVE); em.flush();
        assertEquals(0, repository.countAuthorisedPreviewAccounts(staff.getId(), permissions));
    }
    private AppraisalInsightGeneration generation(UUID owner) {
        var evidence = template.evidence(AppraisalInsightTemplate.ScenarioId.LIMITED_EVIDENCE);
        var generation = new AppraisalInsightGeneration(); generation.setId(UUID.randomUUID()); generation.setOwnerId(owner);
        generation.setRequestKey(UUID.randomUUID()); generation.setScenario("LIMITED_EVIDENCE"); generation.setModel("gemini-test");
        generation.setTemplateVersion(AppraisalInsightTemplate.VERSION); generation.setFixtureVersion(AppraisalInsightTemplate.FIXTURE_VERSION);
        generation.setEvidence(evidence); generation.setEvidenceHash(template.hash(evidence));
        generation.setStatus("QUEUED"); generation.setActiveKey(owner.toString()); generation.setCreatedAt(OffsetDateTime.now(ZoneOffset.UTC));
        return generation;
    }
}
