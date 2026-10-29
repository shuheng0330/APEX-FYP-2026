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

@DataJpaTest(showSql=false,properties={
    "spring.datasource.url=jdbc:h2:mem:organisation-performance;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
    "spring.datasource.driver-class-name=org.h2.Driver","spring.datasource.username=sa","spring.datasource.password=",
    "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect","spring.jpa.hibernate.ddl-auto=create-drop",
    "spring.flyway.enabled=false","spring.sql.init.mode=never","spring.jpa.properties.hibernate.generate_statistics=true",
    "spring.jpa.show-sql=false","logging.level.org.hibernate.stat=ERROR"
})
@AutoConfigureTestDatabase(replace=AutoConfigureTestDatabase.Replace.NONE)
class OrganisationPerformanceRepositoryTest {
    @Autowired OrganisationPerformanceRepository repository;
    @Autowired AnnualPerformancePeriodRepository periodRepository;
    @Autowired TestEntityManager em;
    AnnualKpiReviewPeriod period;Staff staff;ReviewPeriodParticipant participant;
    int nextDisplayOrder=1;
    @BeforeEach void setup(){period=period("Annual 2025",AnnualKpiReviewPeriodStatus.OPEN,true);staff=staff("Employee");participant=participant(staff,period,null);}
    @Test void onlyPublishedAnnualPeriodsAreListedIncludingEmptyAndClosedPeriods() {
        period("Draft",AnnualKpiReviewPeriodStatus.DRAFT,true);period("No roster",AnnualKpiReviewPeriodStatus.OPEN,false);
        var empty=period("Empty published",AnnualKpiReviewPeriodStatus.UPCOMING,true);var closed=period("Closed",AnnualKpiReviewPeriodStatus.CLOSED,true);em.flush();em.clear();
        assertEquals(Set.of(period.getId(),empty.getId(),closed.getId()),new HashSet<>(periodRepository.findPeriods().stream().map(AnnualKpiReviewPeriod::getId).toList()));
    }
    @Test void populationsPreserveRecordedDepartmentsAndDoNotDependOnManagersOrAccountActivity() {
        var department=new OrgChart("Historical IT",OrgChartType.D,false,false);department.setCreatedAt(OffsetDateTime.now());department.setUpdatedAt(OffsetDateTime.now());em.persist(department);
        var deleted=staff("Deleted historical employee");deleted.setDeleted(true);var p=participant(deleted,period,department);
        var draft=period("Draft",AnnualKpiReviewPeriodStatus.DRAFT,true);participant(staff("Draft employee"),draft,null);em.flush();em.clear();
        var data=repository.findPopulation(List.of(period.getId(),draft.getId()));assertEquals(Set.of(participant.getId(),p.getId()),new HashSet<>(data.stream().map(ReviewPeriodParticipant::getId).toList()));
        assertTrue(data.stream().anyMatch(row -> row.getDepartment()==null));assertTrue(data.stream().anyMatch(row -> "Historical IT".equals(row.getDepartmentName())));
    }
    @Test void latestSubmissionIsSelectedByActivityIdIgnoringDraftSavesAndReturnedEdits() {
        var appraisal=appraisal(AppraisalStatus.RETURNED);appraisal.setRecommendation(AnnualAppraisalRecommendation.BOTH);
        event(appraisal,"SUBMITTED",AnnualAppraisalRecommendation.PROMOTION);
        event(appraisal,"RETURNED",AnnualAppraisalRecommendation.PROMOTION);
        event(appraisal,"DRAFT_SAVED",AnnualAppraisalRecommendation.BOTH);em.flush();em.clear();
        var record=repository.findDecisions(period.getId()).get(0);assertEquals(AnnualAppraisalRecommendation.PROMOTION,record.getSubmittedRecommendation());
        appraisal=em.find(AnnualAppraisal.class,appraisal.getId());appraisal.setStatus(AppraisalStatus.PENDING_REVIEW);
        event(appraisal,"RESUBMITTED",AnnualAppraisalRecommendation.SALARY_INCREMENT);event(appraisal,"DRAFT_SAVED",AnnualAppraisalRecommendation.BOTH);em.flush();em.clear();
        assertEquals(AnnualAppraisalRecommendation.SALARY_INCREMENT,repository.findDecisions(period.getId()).get(0).getSubmittedRecommendation());
    }
    @Test void unsubmittedDraftsAreExcludedAndMissingActivityDoesNotInventARecommendation() {
        var appraisal=appraisal(AppraisalStatus.DRAFT);appraisal.setSubmittedAt(null);event(appraisal,"DRAFT_SAVED",AnnualAppraisalRecommendation.PROMOTION);em.flush();em.clear();
        assertTrue(repository.findDecisions(period.getId()).isEmpty());
        appraisal=em.find(AnnualAppraisal.class,appraisal.getId());appraisal.setStatus(AppraisalStatus.PENDING_REVIEW);em.flush();em.clear();
        assertNull(repository.findDecisions(period.getId()).get(0).getSubmittedRecommendation());
    }
    @Test void approvedFinalDecisionIsSeparateAndInvalidRecordedIdentitiesAreExcluded() {
        var appraisal=appraisal(AppraisalStatus.APPROVED);appraisal.setFinalDecision(AnnualAppraisalRecommendation.PROMOTION);appraisal.setReviewedAt(OffsetDateTime.now());event(appraisal,"SUBMITTED",AnnualAppraisalRecommendation.SALARY_INCREMENT);
        em.flush();em.clear();var record=repository.findDecisions(period.getId()).get(0);
        assertEquals(AnnualAppraisalRecommendation.PROMOTION,record.getFinalDecision());assertEquals(AnnualAppraisalRecommendation.SALARY_INCREMENT,record.getSubmittedRecommendation());
        // Native test-only corruption checks the defensive identity predicate on the read projection.
        em.getEntityManager().createNativeQuery("update annual_appraisal set employee_id=:employee where id=:id").setParameter("employee",UUID.randomUUID()).setParameter("id",appraisal.getId()).executeUpdate();em.flush();em.clear();
        assertTrue(repository.findDecisions(period.getId()).isEmpty());assertTrue(repository.findAppraisalProgress(period.getId()).isEmpty());
    }
    @Test void projectionReadsDoNotLoadConfidentialEntitiesOrWriteAnyRows() {
        var appraisal=appraisal(AppraisalStatus.PENDING_REVIEW);appraisal.setJustification("Private justification must not be loaded");appraisal.setOverrideReason("Private reason");event(appraisal,"SUBMITTED",AnnualAppraisalRecommendation.PROMOTION);em.flush();em.clear();
        var statistics=em.getEntityManager().getEntityManagerFactory().unwrap(SessionFactory.class).getStatistics();statistics.clear();
        assertEquals(1,repository.findDecisions(period.getId()).size());assertEquals(AppraisalStatus.PENDING_REVIEW,repository.findAppraisalProgress(period.getId()).get(0).getStatus());
        assertEquals(0,statistics.getEntityLoadCount());assertEquals(0,statistics.getEntityInsertCount());assertEquals(0,statistics.getEntityUpdateCount());
    }
    @Test void populationGraphUsesBoundedQueriesRatherThanOnePerDepartmentOrParticipant() {
        for(int i=0;i<12;i++)participant(staff("Staff "+i),period,null);em.flush();em.clear();
        var statistics=em.getEntityManager().getEntityManagerFactory().unwrap(SessionFactory.class).getStatistics();statistics.clear();
        var population=repository.findPopulation(List.of(period.getId()));for(var p:population){assertNotNull(p.getReviewPeriod().getEndDate());assertNotNull(p.getEmployeeLevelConfiguration().getIndividualKpiWeight());}
        assertEquals(13,population.size());assertTrue(statistics.getPrepareStatementCount()<=2);assertEquals(0,statistics.getEntityUpdateCount());
    }
    private AnnualAppraisal appraisal(AppraisalStatus status) {
        var a=new AnnualAppraisal();a.setParticipant(participant);a.setReviewPeriodId(period.getId());a.setEmployeeId(staff.getId());a.setSuperiorId(UUID.randomUUID());a.setStatus(status);
        a.setSubmittedAt(OffsetDateTime.now());a.setCreatedAt(OffsetDateTime.now());a.setUpdatedAt(OffsetDateTime.now());a.setCreatedBy(staff.getId());a.setUpdatedBy(staff.getId());em.persist(a);return a;
    }
    private void event(AnnualAppraisal a,String action,AnnualAppraisalRecommendation recommendation) {
        var event=new AnnualAppraisalActivity();event.setAppraisalId(a.getId());event.setAction(action);event.setActorId(staff.getId());event.setOccurredAt(OffsetDateTime.parse("2026-01-01T00:00:00Z"));event.setRecommendation(recommendation);em.persist(event);
    }
    private Staff staff(String name) {
        var s=new Staff();s.setId(UUID.randomUUID());s.setName(name);s.setEmail(s.getId()+"@example.test");s.setAccountStatus(StaffAccountStatus.ACTIVE);s.setCreatedAt(OffsetDateTime.now());s.setUpdatedAt(OffsetDateTime.now());em.persist(s);return s;
    }
    private AnnualKpiReviewPeriod period(String name,AnnualKpiReviewPeriodStatus status,boolean published) {
        var p=new AnnualKpiReviewPeriod();p.setName(name);p.setStartDate(LocalDate.of(2025,1,1));p.setEndDate(LocalDate.of(2025,12,31));p.setStatus(status);if(published)p.setParticipantsSnapshottedAt(OffsetDateTime.now());em.persist(p);return p;
    }
    private ReviewPeriodParticipant participant(Staff staff,AnnualKpiReviewPeriod p,OrgChart department) {
        var l=new EmployeeLevel();l.setCode(UUID.randomUUID().toString());l.setName(l.getCode());l.setDisplayOrder(nextDisplayOrder++);l.setDefaultCompanyKpiWeight(BigDecimal.ZERO);l.setDefaultDepartmentKpiWeight(BigDecimal.ZERO);l.setDefaultIndividualKpiWeight(new BigDecimal("100"));em.persist(l);
        var weights=new ReviewPeriodEmployeeLevelConfiguration();weights.setReviewPeriod(p);weights.setEmployeeLevel(l);weights.setCompanyKpiWeight(BigDecimal.ZERO);weights.setDepartmentKpiWeight(BigDecimal.ZERO);weights.setIndividualKpiWeight(new BigDecimal("100"));em.persist(weights);
        var participant=new ReviewPeriodParticipant();participant.setStaff(staff);participant.setStaffName(staff.getName());participant.setReviewPeriod(p);participant.setDepartment(department);participant.setDepartmentName(department==null?null:department.getName());participant.setReviewFrequency(ReviewFrequency.ANNUALLY);participant.setEmployeeLevelConfigurationId(weights.getId());participant.setEmployeeLevelConfiguration(weights);em.persist(participant);return participant;
    }
}
