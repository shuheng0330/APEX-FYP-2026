package com.tbm.careerpathlearning.repository;

import com.tbm.careerpathlearning.enums.*;
import com.tbm.careerpathlearning.model.*;
import org.hibernate.SessionFactory;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.*;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

/** Disposable in-memory database only. No application/UAT connection is used. */
@DataJpaTest(showSql=false, properties={
        "spring.datasource.url=jdbc:h2:mem:team-performance;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver", "spring.datasource.username=sa", "spring.datasource.password=",
        "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect", "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.flyway.enabled=false", "spring.sql.init.mode=never", "spring.jpa.properties.hibernate.generate_statistics=true",
        "spring.jpa.show-sql=false", "logging.level.org.hibernate.stat=ERROR"
})
@AutoConfigureTestDatabase(replace=AutoConfigureTestDatabase.Replace.NONE)
class TeamPerformanceRepositoryTest {
    @Autowired TeamPerformanceRepository repository;
    @Autowired ReviewCheckpointRepository checkpoints;
    @Autowired KpiAssessmentRepository kpis;
    @Autowired AttitudeAssessmentRepository attitudes;
    @Autowired TestEntityManager em;
    Staff alice, bob, other;
    OrgChart it, finance;
    AnnualKpiReviewPeriod period;
    ReviewPeriodEmployeeLevelConfiguration allocation;

    @BeforeEach void setup() {
        alice = staff("Alice", null); other = staff("Other manager", null); bob = staff("Bob", alice);
        it = department("IT"); finance = department("Finance");
        period = period("Published annual", AnnualKpiReviewPeriodStatus.OPEN, true);
        allocation = allocation(period);
    }

    @Test void realQueriesUseCurrentReportingButRecordedDepartmentAndPreserveUnassignedMembers() {
        var enrolled = participant(bob, period, it, allocation);
        var managerless = participant(other, period, it, allocation);
        var teamWithoutDepartment = participant(staff("No Department", alice), period, null, allocation);
        em.flush(); em.clear();
        var team = repository.findPopulation(true, alice.getId(), null, List.of(period.getId()));
        assertEquals(Set.of(enrolled.getId(), teamWithoutDepartment.getId()), ids(team));
        var department = repository.findPopulation(false, alice.getId(), it.getId(), List.of(period.getId()));
        assertEquals(Set.of(enrolled.getId(), managerless.getId()), ids(department));
        assertEquals(List.of(teamWithoutDepartment.getId()), repository.findPopulation(false, alice.getId(), null, List.of(period.getId())).stream().map(ReviewPeriodParticipant::getId).toList());
        assertTrue(repository.findPopulation(false, alice.getId(), finance.getId(), List.of(period.getId())).isEmpty());
        assertTrue(repository.findDepartments().stream().anyMatch(d -> d.getId() == null));

        em.find(Staff.class, bob.getId()).setManager(em.find(Staff.class, other.getId())); em.flush(); em.clear();
        assertEquals(Set.of(teamWithoutDepartment.getId()), ids(repository.findPopulation(true, alice.getId(), null, List.of(period.getId()))));
        assertEquals(2, repository.findPopulation(false, alice.getId(), it.getId(), List.of(period.getId())).size());
    }

    @Test void publishedUpcomingOpenAndClosedAreReadableButDraftAndUnsnapshottedAreExcluded() {
        for (var status : AnnualKpiReviewPeriodStatus.values()) {
            var p = period(status.name(), status, true); participant(staff(status.name(), alice), p, it, allocation(p));
        }
        var missing = period("No publication snapshot", AnnualKpiReviewPeriodStatus.OPEN, false);
        participant(staff("Missing roster", alice), missing, it, allocation(missing));
        em.flush(); em.clear();
        var available = repository.findPeriods(true, alice.getId(), null);
        assertEquals(Set.of(AnnualKpiReviewPeriodStatus.UPCOMING, AnnualKpiReviewPeriodStatus.OPEN, AnnualKpiReviewPeriodStatus.CLOSED),
                new HashSet<>(available.stream().map(AnnualKpiReviewPeriod::getStatus).toList()));
        assertEquals(3, available.size());
    }

    @Test void batchPopulationGraphDoesNotQueryPerEmployee() {
        for (int i = 0; i < 15; i++) participant(staff("Member " + i, alice), period, it, allocation);
        em.flush(); em.clear();
        var stats = em.getEntityManager().getEntityManagerFactory().unwrap(SessionFactory.class).getStatistics(); stats.clear();
        var population = repository.findPopulation(true, alice.getId(), null, List.of(period.getId()));
        for (var p : population) {
            assertNotNull(p.getStaff().getManager().getName()); assertNotNull(p.getReviewPeriod().getName());
            assertNotNull(p.getEmployeeLevelConfiguration().getIndividualKpiWeight());
        }
        assertTrue(stats.getPrepareStatementCount() <= 2, "population graph must avoid per-employee reads: " + stats.getPrepareStatementCount());
        assertEquals(0, stats.getEntityUpdateCount()); assertEquals(0, stats.getEntityInsertCount());
    }

    @Test void allReadProjectionsExecuteWithoutWorkflowOrSnapshotWrites() {
        var p = participant(bob, period, it, allocation); em.flush(); em.clear();
        assertTrue(checkpoints.findPerformanceCheckpoints(List.of(period.getId())).isEmpty());
        assertTrue(kpis.findPerformanceAssessments(List.of(p.getId())).isEmpty());
        assertTrue(attitudes.findPerformanceAssessments(List.of(p.getId())).isEmpty());
        assertTrue(repository.findFallbackVersions(List.of(p.getId())).isEmpty());
        assertTrue(repository.findPublishedSources(List.of(period.getId())).isEmpty());
        assertTrue(repository.findIndividualPlans(List.of(p.getId())).isEmpty());
        assertTrue(repository.findReturnedRevisions(List.of(p.getId())).isEmpty());
    }

    @Test void assessmentGraphPreservesBoundVersionsAndProjectionReadsDoNotLoadAnswers() {
        var p = participant(bob, period, it, allocation);
        var checkpoint = new ReviewCheckpoint(); checkpoint.setReviewPeriod(period); checkpoint.setReviewFrequency(ReviewFrequency.QUARTERLY);
        checkpoint.setSequenceNumber(1); checkpoint.setStartDate(period.getStartDate()); checkpoint.setEndDate(LocalDate.of(2026, 3, 31));
        checkpoint.setSelfAssessmentDeadline(checkpoint.getEndDate().plusDays(7)); checkpoint.setSuperiorAssessmentDeadline(checkpoint.getEndDate().plusDays(14)); em.persist(checkpoint);
        var plan = plan(KpiLevel.COMPANY, p); var version = version(plan, 1, KpiVersionStatus.EFFECTIVE);
        plan.setCurrentVersion(version); plan.setStatus(KpiPlanStatus.PUBLISHED);
        var kpi = new Kpi(); kpi.setPlanId(plan.getId()); kpi.setPlan(plan); kpi.setReviewPeriodId(period.getId()); kpi.setVersion(version); em.persist(kpi);
        var assignment = new EmployeeKpiAssignment(); assignment.setReviewPeriodId(period.getId()); assignment.setParticipantId(p.getId());
        assignment.setParticipant(p); assignment.setKpiId(kpi.getId()); assignment.setKpi(kpi); assignment.setAssignedAt(OffsetDateTime.now()); em.persist(assignment);
        var assessment = new KpiAssessment(); assessment.setParticipant(p); assessment.setCheckpoint(checkpoint); assessment.setReviewPeriodId(period.getId());
        assessment.setReviewFrequency(ReviewFrequency.QUARTERLY); assessment.setStatus(KpiAssessmentStatus.REVIEWED); assessment.setCheckpointScore(new BigDecimal("71"));
        assessment.setCreatedAt(OffsetDateTime.now()); assessment.setUpdatedAt(OffsetDateTime.now()); assessment.setCreatedBy(alice.getId()); assessment.setUpdatedBy(alice.getId()); em.persist(assessment);
        var binding = new KpiAssessmentPlanVersion(); binding.setAssessment(assessment); binding.setReviewPeriodId(period.getId()); binding.setLevel(KpiLevel.COMPANY);
        binding.setVersion(version); em.persist(binding);
        var result = new KpiCentralResult(); result.setReviewPeriodId(period.getId()); result.setCheckpoint(checkpoint); result.setReviewFrequency(ReviewFrequency.QUARTERLY);
        result.setPlanVersion(version); result.setLevel(KpiLevel.COMPANY); result.setStatus(KpiCentralResultStatus.PUBLISHED); result.setEditionNumber(1);
        result.setCreatedAt(OffsetDateTime.now()); result.setUpdatedAt(OffsetDateTime.now()); result.setCreatedBy(alice.getId()); result.setUpdatedBy(alice.getId()); em.persist(result);
        var individual = plan(KpiLevel.INDIVIDUAL, p); individual.setStatus(KpiPlanStatus.RETURNED); individual.setSubmittedToSuperiorId(alice.getId());
        var revision = version(individual, 2, KpiVersionStatus.RETURNED); revision.setRevisionSuperiorId(alice.getId());
        revision.setPreparedBy(alice.getId()); revision.setPreparationMode(KpiRevisionPreparationMode.SUPERIOR);
        em.flush(); em.clear();
        var read = kpis.findPerformanceAssessments(List.of(p.getId())).get(0);
        assertEquals(version.getId(), read.getPlanVersions().get(0).getVersion().getId());
        assertFalse(org.hibernate.Hibernate.isInitialized(read.getItems()), "dashboard must not fetch answer/comment payloads");
        var fallback = repository.findFallbackVersions(List.of(p.getId()));
        assertEquals(1, fallback.size()); assertEquals(version.getId(), fallback.get(0).getVersionId());
        assertEquals(KpiLevel.COMPANY, fallback.get(0).getLevel()); assertEquals(p.getId(), fallback.get(0).getParticipantId());
        assertEquals(checkpoint.getId(), repository.findPublishedSources(List.of(period.getId())).get(0).getCheckpointId());
        assertEquals(KpiPlanStatus.RETURNED, repository.findIndividualPlans(List.of(p.getId())).get(0).getStatus());
        assertEquals(alice.getId(), repository.findReturnedRevisions(List.of(p.getId())).get(0).getSuperiorId());
        assertEquals(KpiRevisionPreparationMode.SUPERIOR, repository.findReturnedRevisions(List.of(p.getId())).get(0).getMode());
    }

    private KpiPlan plan(KpiLevel level, ReviewPeriodParticipant p) {
        var plan = new KpiPlan(); plan.setReviewPeriod(period); plan.setLevel(level);
        if (level == KpiLevel.INDIVIDUAL) { plan.setOwnerParticipant(p); plan.setOwnerParticipantId(p.getId()); }
        plan.setCreatedAt(OffsetDateTime.now()); plan.setUpdatedAt(OffsetDateTime.now()); plan.setCreatedBy(alice.getId()); plan.setUpdatedBy(alice.getId()); em.persist(plan); return plan;
    }
    private KpiPlanVersion version(KpiPlan plan, int number, KpiVersionStatus status) {
        var v = new KpiPlanVersion(); v.setPlan(plan); v.setReviewPeriodId(period.getId()); v.setLevel(plan.getLevel()); v.setVersionNumber(number); v.setStatus(status);
        if (number > 1) { v.setRevisionSuperiorId(alice.getId()); v.setPreparationMode(KpiRevisionPreparationMode.SUPERIOR); }
        v.setCreatedAt(OffsetDateTime.now()); v.setUpdatedAt(OffsetDateTime.now()); v.setCreatedBy(alice.getId()); v.setUpdatedBy(alice.getId()); em.persist(v); return v;
    }

    private Set<Long> ids(List<ReviewPeriodParticipant> population) { return new HashSet<>(population.stream().map(ReviewPeriodParticipant::getId).toList()); }
    private Staff staff(String name, Staff manager) {
        var s = new Staff(); s.setId(UUID.randomUUID()); s.setName(name); s.setEmail(s.getId() + "@example.test"); s.setManager(manager);
        s.setAccountStatus(StaffAccountStatus.ACTIVE); s.setCreatedAt(OffsetDateTime.now()); s.setUpdatedAt(OffsetDateTime.now()); em.persist(s); return s;
    }
    private OrgChart department(String name) {
        var d = new OrgChart(name, OrgChartType.D, false, false); d.setCreatedAt(OffsetDateTime.now()); d.setUpdatedAt(OffsetDateTime.now()); em.persist(d); return d;
    }
    private AnnualKpiReviewPeriod period(String name, AnnualKpiReviewPeriodStatus status, boolean published) {
        var p = new AnnualKpiReviewPeriod(); p.setName(name); p.setStartDate(LocalDate.of(2026, 1, 1)); p.setEndDate(LocalDate.of(2026, 12, 31));
        p.setStatus(status); if (published) p.setParticipantsSnapshottedAt(OffsetDateTime.now()); em.persist(p); return p;
    }
    private ReviewPeriodEmployeeLevelConfiguration allocation(AnnualKpiReviewPeriod p) {
        var l = new EmployeeLevel(); l.setCode(UUID.randomUUID().toString()); l.setName(l.getCode()); l.setDisplayOrder((int) p.getId().longValue());
        l.setDefaultCompanyKpiWeight(BigDecimal.ZERO); l.setDefaultDepartmentKpiWeight(BigDecimal.ZERO); l.setDefaultIndividualKpiWeight(new BigDecimal("100")); em.persist(l);
        var w = new ReviewPeriodEmployeeLevelConfiguration(); w.setReviewPeriod(p); w.setEmployeeLevel(l);
        w.setCompanyKpiWeight(BigDecimal.ZERO); w.setDepartmentKpiWeight(BigDecimal.ZERO); w.setIndividualKpiWeight(new BigDecimal("100")); em.persist(w); return w;
    }
    private ReviewPeriodParticipant participant(Staff s, AnnualKpiReviewPeriod p, OrgChart d, ReviewPeriodEmployeeLevelConfiguration w) {
        var participant = new ReviewPeriodParticipant(); participant.setStaff(s); participant.setStaffName(s.getName()); participant.setReviewPeriod(p);
        participant.setDepartment(d); participant.setDepartmentName(d == null ? null : d.getName()); participant.setReviewFrequency(ReviewFrequency.QUARTERLY);
        participant.setEmployeeLevelConfiguration(w); participant.setEmployeeLevelConfigurationId(w.getId()); em.persist(participant); return participant;
    }
}
