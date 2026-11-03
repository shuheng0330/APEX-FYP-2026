package com.tbm.careerpathlearning.service.impl;

import com.tbm.careerpathlearning.dto.*;
import com.tbm.careerpathlearning.enums.*;
import com.tbm.careerpathlearning.exception.BadRequestException;
import com.tbm.careerpathlearning.exception.KpiVersionConflictException;
import com.tbm.careerpathlearning.mapper.*;
import com.tbm.careerpathlearning.model.*;
import com.tbm.careerpathlearning.repository.*;
import com.tbm.careerpathlearning.service.PerformanceDepartmentResolver;
import com.tbm.careerpathlearning.service.KpiAssessmentEvidenceStorage;
import com.tbm.careerpathlearning.service.KpiCheckpointAggregateService;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mapstruct.factory.Mappers;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.time.*;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class KpiCentralResultServiceTest {
    private final AnnualKpiReviewPeriodRepository periods = mock(AnnualKpiReviewPeriodRepository.class);
    private final ReviewCheckpointRepository checkpoints = mock(ReviewCheckpointRepository.class);
    private final KpiPlanVersionRepository versions = mock(KpiPlanVersionRepository.class);
    private final KpiCentralResultRepository results = mock(KpiCentralResultRepository.class);
    private final CompanyKpiResultDelegateRepository delegates = mock(CompanyKpiResultDelegateRepository.class);
    private final StaffRepository staff = mock(StaffRepository.class);
    private final PerformanceDepartmentResolver departments = mock(PerformanceDepartmentResolver.class);
    private final KpiCentralResultItemRepository itemRepository = mock(KpiCentralResultItemRepository.class);
    private final KpiCentralResultEvidenceRepository files = mock(KpiCentralResultEvidenceRepository.class);
    private final KpiAssessmentEvidenceStorage storage = mock(KpiAssessmentEvidenceStorage.class);
    private final Map<Long, KpiCentralResult> editions = new LinkedHashMap<>();
    private final UUID actor = UUID.randomUUID();
    private final UUID delegateId = UUID.randomUUID();
    private Staff employee;
    private AnnualKpiReviewPeriod period;
    private ReviewCheckpoint checkpoint;
    private KpiPlanVersion version;
    private KpiCentralResult saved;
    private KpiCentralResultServiceImpl service;

    private void date(String day) {
        var planMapper = Mappers.getMapper(KpiPlanMapper.class);
        var resultMapper = Mappers.getMapper(KpiCentralResultMapper.class);
        // The generated Spring mapper uses field injection for its KPI item mapper.
        ReflectionTestUtils.setField(resultMapper, "kpiPlanMapper", planMapper);
        service = new KpiCentralResultServiceImpl(periods, checkpoints, versions, results, delegates, staff,
                departments, resultMapper, planMapper, Mappers.getMapper(AnnualKpiReviewPeriodMapper.class),
                Clock.fixed(Instant.parse(day + "T12:00:00Z"), ZoneOffset.UTC), itemRepository, files, storage,
                mock(KpiCheckpointAggregateService.class));
    }

    @BeforeEach void setup() {
        date("2027-02-01");
        employee = new Staff();
        employee.setId(actor);
        employee.setAccountStatus(StaffAccountStatus.ACTIVE);
        employee.setRole(new Role());
        when(staff.findById(actor)).thenReturn(Optional.of(employee));
        var delegate = new Staff();
        delegate.setId(delegateId);
        delegate.setAccountStatus(StaffAccountStatus.ACTIVE);
        when(staff.findById(delegateId)).thenReturn(Optional.of(delegate));
        period = new AnnualKpiReviewPeriod();
        period.setId(1L);
        period.setName("Central results period");
        period.setAssessmentModel(KpiAssessmentModel.CENTRAL_RESULTS);
        period.setStatus(AnnualKpiReviewPeriodStatus.OPEN);
        period.setStartDate(LocalDate.of(2027, 1, 1));
        period.setEndDate(LocalDate.of(2027, 12, 31));
        when(periods.findById(1L)).thenReturn(Optional.of(period));
        when(periods.findAllByOrderByStartDateDescIdDesc()).thenReturn(List.of(period));
        checkpoint = new ReviewCheckpoint();
        checkpoint.setId(11L);
        checkpoint.setReviewPeriod(period);
        checkpoint.setReviewFrequency(ReviewFrequency.MONTHLY);
        checkpoint.setSequenceNumber(1);
        checkpoint.setStartDate(LocalDate.of(2027, 1, 1));
        checkpoint.setEndDate(LocalDate.of(2027, 1, 31));
        checkpoint.setSelfAssessmentDeadline(LocalDate.of(2027, 2, 5));
        checkpoint.setSuperiorAssessmentDeadline(LocalDate.of(2027, 2, 10));
        when(checkpoints.findById(11L)).thenReturn(Optional.of(checkpoint));
        when(checkpoints.findAllByReviewPeriodIdOrderByReviewFrequencyAscSequenceNumberAsc(1L))
                .thenReturn(List.of(checkpoint));
        version = version(101L, KpiLevel.COMPANY, null);
        addItem(version, 1001L, "Revenue");
        addItem(version, 1002L, "Service");
        when(versions.findAllByReviewPeriodIdOrderByVersionNumberDescIdDesc(1L)).thenReturn(List.of(version));
        when(results.eligibleParticipants(101L, 11L)).thenReturn(2L);
        when(results.saveAndFlush(any())).thenAnswer(invocation -> {
            saved = invocation.getArgument(0);
            if (saved.getId() == null) saved.setId(301L + editions.size());
            editions.put(saved.getId(), saved);
            when(results.findById(saved.getId())).thenReturn(Optional.of(saved));
            when(results.lockById(saved.getId())).thenReturn(Optional.of(saved));
            for (int index = 0; index < saved.getItems().size(); index++) {
                var item = saved.getItems().get(index);
                if (item.getId() == null) item.setId(saved.getId() * 10 + index);
                when(itemRepository.findById(item.getId())).thenReturn(Optional.of(item));
            }
            return saved;
        });
        authenticate("CAN_MANAGE_COMPANY_KPI");
    }

    @AfterEach void cleanup() {
        SecurityContextHolder.clearContext();
        if (TransactionSynchronizationManager.isSynchronizationActive()) TransactionSynchronizationManager.clearSynchronization();
    }

    private void authenticate(String... authorities) {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(actor, null,
                Arrays.stream(authorities).map(SimpleGrantedAuthority::new).toList()));
    }

    private KpiPlanVersion version(long id, KpiLevel level, OrgChart department) {
        var plan = new KpiPlan();
        plan.setId(id);
        plan.setReviewPeriod(period);
        plan.setLevel(level);
        plan.setDepartment(department);
        plan.setStatus(level == KpiLevel.COMPANY ? KpiPlanStatus.PUBLISHED : KpiPlanStatus.APPROVED);
        var v = new KpiPlanVersion();
        v.setId(id);
        v.setPlan(plan);
        v.setReviewPeriodId(1L);
        v.setLevel(level);
        v.setVersionNumber(1);
        v.setStatus(KpiVersionStatus.EFFECTIVE);
        plan.setCurrentVersion(v);
        when(versions.findById(id)).thenReturn(Optional.of(v));
        return v;
    }

    private void addItem(KpiPlanVersion v, long id, String name) {
        var kpi = new Kpi();
        kpi.setId(id);
        kpi.setPlan(v.getPlan());
        kpi.setVersion(v);
        kpi.setName(name);
        kpi.setTarget("100 units");
        kpi.setWeightage(new BigDecimal("50"));
        v.getItems().add(kpi);
    }

    private KpiCentralResultRequest request() {
        var r = new KpiCentralResultRequest();
        r.setPlanVersionId(101L);
        r.setCheckpointId(11L);
        r.setExpectedLockVersion(0L);
        return r;
    }

    private KpiCentralResultRequest.Answer answer(Long id, Integer point) {
        var answer = new KpiCentralResultRequest.Answer();
        answer.setKpiId(id);
        answer.setPoint(point);
        answer.setActualAchievement("  80 units  ");
        answer.setComment("  Checked  ");
        return answer;
    }

    private void noWrites() {
        verify(results, never()).saveAndFlush(any());
        verify(results, never()).save(any());
        verify(delegates, never()).saveAndFlush(any());
        verify(delegates, never()).save(any());
    }

    @Test void allGetEndpointsAreReadOnlyAndMapContextAndReadiness() {
        service.create(request(), actor);
        when(results.findAllByReviewPeriodIdOrderByUpdatedAtDescIdDesc(1L)).thenReturn(List.of(saved));
        clearInvocations(results, periods, delegates);
        assertEquals(1L, service.periods(actor).get(0).getId());
        var option = service.options(1L, actor).get(0);
        assertEquals(101L, option.planVersionId());
        assertTrue(option.currentVersion());
        assertEquals(11L, service.checkpoints(101L, actor).get(0).getId());
        assertEquals(1, service.list(1L, actor).size());
        var detail = service.get(301L, actor);
        assertEquals("Central results period", detail.getReviewPeriodName());
        assertEquals("Revenue", detail.getItems().get(0).getKpi().getName());
        assertEquals("100 units", detail.getItems().get(0).getKpi().getTarget());
        assertEquals(4, detail.getPublicationBlockers().size());
        assertEquals(List.of("UPDATE_DRAFT", "UPLOAD_EVIDENCE"), detail.getAllowedActions());
        assertNull(detail.getItems().get(0).getPoint());
        noWrites();
        verify(periods, never()).lockConfiguration();
    }

    @Test void emptyDraftPreservesMissingPointsAndReportsSpecificBlockers() {
        var dto = service.create(request(), actor);
        assertEquals(KpiCentralResultStatus.DRAFT, dto.getStatus());
        assertEquals(2, dto.getItems().size());
        assertNull(dto.getItems().get(0).getPoint());
        assertNull(dto.getItems().get(0).getActualAchievement());
        assertTrue(dto.getPublicationBlockers().contains("Enter Actual Achievement for Revenue"));
        assertTrue(dto.getPublicationBlockers().contains("Select a Result Point for Revenue"));
        assertEquals(actor, dto.getCreatedBy());
        assertEquals(actor, dto.getUpdatedBy());
        assertNull(dto.getPublishedAt());
        assertNull(dto.getPublishedBy());
        assertEquals(List.of("UPDATE_DRAFT", "UPLOAD_EVIDENCE"), dto.getAllowedActions());
        verify(periods).lockConfiguration();
    }

    @Test void partialUpdatePreservesOmittedAnswersAndAdvancesAuditEvenAtFixedClock() {
        var first = request();
        first.setItems(List.of(answer(1001L, 4), answer(1002L, 3)));
        service.create(first, actor);
        var created = saved.getCreatedAt();
        var update = request();
        update.setItems(List.of(answer(1001L, 5)));
        var dto = service.update(301L, update, actor);
        assertEquals(5, dto.getItems().get(0).getPoint());
        assertEquals(3, dto.getItems().get(1).getPoint());
        assertEquals("80 units", dto.getItems().get(1).getActualAchievement());
        assertEquals("Checked", dto.getItems().get(0).getComment());
        assertEquals(created, dto.getCreatedAt());
        assertTrue(dto.getUpdatedAt().isAfter(created));
        assertTrue(dto.getPublicationBlockers().isEmpty());
        assertEquals(List.of("UPDATE_DRAFT", "UPLOAD_EVIDENCE", "PUBLISH"), dto.getAllowedActions());
        verify(results).lockById(301L);
    }

    @Test void nullPointsAndBlankAchievementAreAllowedForDraftAndCanClearAnAnswer() {
        var r = request();
        r.setItems(List.of(answer(1001L, 4)));
        service.create(r, actor);
        var blank = answer(1001L, null);
        blank.setActualAchievement("   ");
        blank.setComment(null);
        r.setItems(List.of(blank));
        var dto = service.update(301L, r, actor);
        assertNull(dto.getItems().get(0).getPoint());
        assertEquals("", dto.getItems().get(0).getActualAchievement());
        assertNull(dto.getItems().get(0).getComment());
        assertEquals(4, dto.getPublicationBlockers().size());
    }

    @ParameterizedTest @EnumSource(ReviewFrequency.class)
    void entryOpensDayAfterCheckpointEndForEveryFrequency(ReviewFrequency frequency) {
        checkpoint.setReviewFrequency(frequency);
        var end = switch (frequency) {
            case MONTHLY -> LocalDate.of(2027, 1, 31);
            case QUARTERLY -> LocalDate.of(2027, 3, 31);
            case ANNUALLY -> LocalDate.of(2027, 12, 31);
        };
        checkpoint.setEndDate(end);
        date(end.toString());
        assertThrows(BadRequestException.class, () -> service.create(request(), actor));
        noWrites();
        date(end.plusDays(1).toString());
        assertEquals(frequency, service.create(request(), actor).getReviewFrequency());
    }

    @Test void endedOpenPeriodAndPassedAssessmentDeadlinesDoNotBlockCentralDraft() {
        date("2028-02-01");
        assertEquals(KpiCentralResultStatus.DRAFT, service.create(request(), actor).getStatus());
    }

    @Test void duplicateCheckpointResultsRejectedBeforeSave() {
        when(results.existsByPlanVersionIdAndCheckpointId(101L, 11L)).thenReturn(true);
        assertThrows(BadRequestException.class, () -> service.create(request(), actor));
        noWrites();
    }

    @Test void foreignPeriodCheckpointRejectedBeforeEligibilityAndSave() {
        var foreign = new AnnualKpiReviewPeriod();
        foreign.setId(2L);
        checkpoint.setReviewPeriod(foreign);
        assertThrows(BadRequestException.class, () -> service.create(request(), actor));
        verify(results, never()).eligibleParticipants(any(), any());
        noWrites();
    }

    @Test void ineligibleFrequencyOrZeroAllocationCannotCreateOrUpdateDraft() {
        service.create(request(), actor);
        clearInvocations(results);
        when(results.eligibleParticipants(101L, 11L)).thenReturn(0L);
        checkpoint.setReviewFrequency(ReviewFrequency.QUARTERLY);
        assertTrue(service.checkpoints(101L, actor).isEmpty());
        assertTrue(service.options(1L, actor).isEmpty());
        assertThrows(BadRequestException.class, () -> service.create(request(), actor));
        assertThrows(BadRequestException.class, () -> service.update(301L, request(), actor));
        noWrites();
    }

    @ParameterizedTest
    @EnumSource(value = KpiVersionStatus.class, names = {"EFFECTIVE", "SUPERSEDED"}, mode = EnumSource.Mode.EXCLUDE)
    void unfinishedVersionsCannotBeOfferedOrWritten(KpiVersionStatus status) {
        version.setStatus(status);
        assertTrue(service.options(1L, actor).isEmpty());
        assertThrows(BadRequestException.class, () -> service.checkpoints(101L, actor));
        assertThrows(BadRequestException.class, () -> service.create(request(), actor));
        noWrites();
    }

    @Test void supersededVersionIsAvailableOnlyWhenEligibilityQueryRequiresIt() {
        version.setStatus(KpiVersionStatus.SUPERSEDED);
        version.getPlan().setCurrentVersion(version(102L, KpiLevel.COMPANY, null));
        assertFalse(service.options(1L, actor).get(0).currentVersion());
        when(results.eligibleParticipants(101L, 11L)).thenReturn(0L);
        assertTrue(service.options(1L, actor).isEmpty());
        assertThrows(BadRequestException.class, () -> service.create(request(), actor));
        noWrites();
    }

    @Test void unapprovedPlanAndIndividualVersionCannotReceiveCentralResults() {
        version.getPlan().setStatus(KpiPlanStatus.DRAFT);
        assertThrows(BadRequestException.class, () -> service.create(request(), actor));
        version.getPlan().setStatus(KpiPlanStatus.APPROVED);
        version.setLevel(KpiLevel.INDIVIDUAL);
        assertTrue(service.options(1L, actor).isEmpty());
        assertThrows(AccessDeniedException.class, () -> service.create(request(), actor));
        noWrites();
    }

    @Test void malformedScopeAndMissingItemCollectionFailBeforeSave() {
        assertThrows(BadRequestException.class, () -> service.create(null, actor));
        var r = request();
        r.setPlanVersionId(null);
        assertThrows(BadRequestException.class, () -> service.create(r, actor));
        r.setPlanVersionId(101L);
        r.setCheckpointId(null);
        assertThrows(BadRequestException.class, () -> service.create(r, actor));
        r.setCheckpointId(999L);
        assertThrows(BadRequestException.class, () -> service.create(r, actor));
        r.setCheckpointId(11L);
        r.setItems(null);
        assertThrows(BadRequestException.class, () -> service.create(r, actor));
        noWrites();
    }

    @Test void missingForeignDuplicateAndNullItemsRejectedWithoutApplyingAnyAnswers() {
        service.create(request(), actor);
        clearInvocations(results);
        var invalidCollections = new ArrayList<List<KpiCentralResultRequest.Answer>>();
        invalidCollections.add(Arrays.asList((KpiCentralResultRequest.Answer) null));
        invalidCollections.add(List.of(answer(null, 4)));
        invalidCollections.add(List.of(answer(999L, 4)));
        invalidCollections.add(List.of(answer(1001L, 4), answer(1001L, 5)));
        invalidCollections.add(List.of(answer(1001L, 4), answer(999L, 3)));
        for (var invalid : invalidCollections) {
            var r = request();
            r.setItems(invalid);
            assertThrows(BadRequestException.class, () -> service.update(301L, r, actor));
            assertNull(saved.getItems().get(0).getPoint());
        }
        noWrites();
    }

    @ParameterizedTest @ValueSource(ints = {-1, 0, 6, Integer.MAX_VALUE})
    void outOfRangePointsRejectedOnCreateAndUpdate(int point) {
        service.create(request(), actor);
        clearInvocations(results);
        var r = request();
        r.setItems(List.of(answer(1001L, point)));
        assertThrows(BadRequestException.class, () -> service.create(r, actor));
        assertThrows(BadRequestException.class, () -> service.update(301L, r, actor));
        assertNull(saved.getItems().get(0).getPoint());
        noWrites();
    }

    @Test void textLengthLimitIsEnforcedButBoundaryValueAndAllFivePointsAreAccepted() {
        var r = request();
        var answer = answer(1001L, 1);
        answer.setActualAchievement("x".repeat(10001));
        r.setItems(List.of(answer));
        assertThrows(BadRequestException.class, () -> service.create(r, actor));
        answer.setActualAchievement("x".repeat(10000));
        answer.setComment("x".repeat(10001));
        assertThrows(BadRequestException.class, () -> service.create(r, actor));
        noWrites();
        answer.setComment("x".repeat(10000));
        service.create(r, actor);
        for (int point = 1; point <= 5; point++) {
            answer.setPoint(point);
            assertEquals(point, service.update(301L, r, actor).getItems().get(0).getPoint());
        }
    }

    @Test void staleAndMissingLockVersionsCannotChangeSavedDraft() {
        service.create(request(), actor);
        saved.setLockVersion(7L);
        clearInvocations(results);
        var r = request();
        r.setItems(List.of(answer(1001L, 5)));
        assertThrows(KpiVersionConflictException.class, () -> service.update(301L, r, actor));
        r.setExpectedLockVersion(null);
        assertThrows(KpiVersionConflictException.class, () -> service.update(301L, r, actor));
        assertNull(saved.getItems().get(0).getPoint());
        noWrites();
        r.setExpectedLockVersion(7L);
        assertEquals(5, service.update(301L, r, actor).getItems().get(0).getPoint());
    }

    @Test void updateCannotChangeItsVersionOrCheckpointAndRechecksConfirmedVersion() {
        service.create(request(), actor);
        clearInvocations(results);
        var r = request();
        r.setPlanVersionId(102L);
        assertThrows(BadRequestException.class, () -> service.update(301L, r, actor));
        r.setPlanVersionId(101L);
        r.setCheckpointId(12L);
        assertThrows(BadRequestException.class, () -> service.update(301L, r, actor));
        assertThrows(BadRequestException.class, () -> service.update(301L, null, actor));
        version.setStatus(KpiVersionStatus.CANCELLED);
        assertThrows(BadRequestException.class, () -> service.update(301L, request(), actor));
        noWrites();
    }

    @ParameterizedTest
    @EnumSource(value = KpiCentralResultStatus.class, names = "DRAFT", mode = EnumSource.Mode.EXCLUDE)
    void publishedAndSupersededResultsAreImmutable(KpiCentralResultStatus status) {
        service.create(request(), actor);
        saved.setStatus(status);
        clearInvocations(results);
        assertEquals(status == KpiCentralResultStatus.PUBLISHED ? List.of("CREATE_CORRECTION") : List.of(),
                service.get(301L, actor).getAllowedActions());
        assertThrows(BadRequestException.class, () -> service.update(301L, request(), actor));
        noWrites();
    }

    @ParameterizedTest
    @EnumSource(value = AnnualKpiReviewPeriodStatus.class, names = "OPEN", mode = EnumSource.Mode.EXCLUDE)
    void nonOpenPeriodsRejectResultWrites(AnnualKpiReviewPeriodStatus status) {
        service.create(request(), actor);
        period.setStatus(status);
        clearInvocations(results);
        assertThrows(BadRequestException.class, () -> service.create(request(), actor));
        assertThrows(BadRequestException.class, () -> service.update(301L, request(), actor));
        if (status != AnnualKpiReviewPeriodStatus.DRAFT) assertTrue(service.get(301L, actor).getAllowedActions().isEmpty());
        noWrites();
    }

    @Test void legacyModelRejectsEveryCentralWriteWithoutChangingExistingData() {
        service.create(request(), actor);
        period.setAssessmentModel(KpiAssessmentModel.LEGACY_ALL_LEVELS);
        clearInvocations(results, delegates);
        assertTrue(service.periods(actor).isEmpty());
        assertThrows(BadRequestException.class, () -> service.create(request(), actor));
        assertThrows(BadRequestException.class, () -> service.update(301L, request(), actor));
        assertThrows(BadRequestException.class, () -> service.assignDelegate(1L, delegateId, actor));
        assertThrows(BadRequestException.class, () -> service.revokeDelegate(1L, 701L, actor));
        noWrites();
    }

    @Test void companyDelegateNeedsBothPermissionAndActiveAssignmentInMatchingPeriod() {
        authenticate("CAN_ENTER_COMPANY_KPI_RESULTS");
        assertThrows(AccessDeniedException.class, () -> service.options(1L, actor));
        when(delegates.existsByReviewPeriodIdAndStaffIdAndRevokedAtIsNull(2L, actor)).thenReturn(true);
        assertThrows(AccessDeniedException.class, () -> service.create(request(), actor));
        when(delegates.existsByReviewPeriodIdAndStaffIdAndRevokedAtIsNull(1L, actor)).thenReturn(true);
        assertEquals(1, service.options(1L, actor).size());
        service.create(request(), actor);
        authenticate("ROLE_USER");
        assertThrows(AccessDeniedException.class, () -> service.get(301L, actor));
        authenticate("CAN_ENTER_COMPANY_KPI_RESULTS");
        when(delegates.existsByReviewPeriodIdAndStaffIdAndRevokedAtIsNull(1L, actor)).thenReturn(false);
        clearInvocations(results);
        assertThrows(AccessDeniedException.class, () -> service.get(301L, actor));
        assertThrows(AccessDeniedException.class, () -> service.update(301L, request(), actor));
        noWrites();
    }

    @Test void roleNamesDoNotConferCompanyAccessAndOwnerDoesNotNeedDelegation() {
        for (var name : List.of("MD", "HR", "Super Admin", "HOD")) {
            employee.getRole().setName(name);
            authenticate("ROLE_USER");
            assertTrue(service.periods(actor).isEmpty());
            assertThrows(AccessDeniedException.class, () -> service.create(request(), actor));
        }
        noWrites();
        authenticate("CAN_MANAGE_COMPANY_KPI");
        assertEquals(1, service.options(1L, actor).size());
        verify(delegates, never()).existsByReviewPeriodIdAndStaffIdAndRevokedAtIsNull(any(), any());
    }

    @Test void departmentScopeRemainsRestrictedWithCombinedCompanyAndDepartmentPermissions() {
        var own = new OrgChart(); own.setId(20L); own.setName("HR");
        var foreign = new OrgChart(); foreign.setId(21L); foreign.setName("Sales");
        var ownVersion = version(102L, KpiLevel.DEPARTMENT, own);
        var foreignVersion = version(103L, KpiLevel.DEPARTMENT, foreign);
        when(departments.resolve(employee.getRole())).thenReturn(own);
        when(results.eligibleParticipants(102L, 11L)).thenReturn(1L);
        when(versions.findAllByReviewPeriodIdOrderByVersionNumberDescIdDesc(1L))
                .thenReturn(List.of(version, ownVersion, foreignVersion));
        authenticate("CAN_MANAGE_DEPARTMENT_KPI");
        assertEquals(List.of(102L), service.options(1L, actor).stream().map(KpiCentralResultOptionDto::planVersionId).toList());
        authenticate("CAN_MANAGE_COMPANY_KPI", "CAN_MANAGE_DEPARTMENT_KPI");
        assertEquals(List.of(101L, 102L), service.options(1L, actor).stream().map(KpiCentralResultOptionDto::planVersionId).toList());
        var r = request(); r.setPlanVersionId(103L);
        assertThrows(AccessDeniedException.class, () -> service.create(r, actor));
        r.setPlanVersionId(102L);
        var dto = service.create(r, actor);
        assertEquals(20L, dto.getDepartmentId());
        assertEquals("HR", dto.getDepartmentName());
        employee.getRole().setPerformanceReviewEligible(false);
        assertThrows(AccessDeniedException.class, () -> service.get(301L, actor));
    }

    @Test void deletedOrInactiveActorCannotReadOrWriteAndMissingIdsGiveControlledErrors() {
        employee.setAccountStatus(StaffAccountStatus.INACTIVE);
        assertThrows(AccessDeniedException.class, () -> service.periods(actor));
        assertThrows(AccessDeniedException.class, () -> service.create(request(), actor));
        employee.setAccountStatus(StaffAccountStatus.ACTIVE);
        employee.setDeleted(true);
        assertThrows(AccessDeniedException.class, () -> service.options(1L, actor));
        employee.setDeleted(false);
        assertThrows(BadRequestException.class, () -> service.options(null, actor));
        assertThrows(BadRequestException.class, () -> service.get(999L, actor));
        assertThrows(BadRequestException.class, () -> service.checkpoints(999L, actor));
        noWrites();
    }

    @Test void ownerAssignmentRevocationHistoryAndReassignmentAreAppendOnly() {
        var history = new ArrayList<CompanyKpiResultDelegate>();
        when(delegates.existsByReviewPeriodIdAndStaffIdAndRevokedAtIsNull(1L, delegateId))
                .thenAnswer(i -> history.stream().anyMatch(d -> d.getRevokedAt() == null));
        when(delegates.saveAndFlush(any())).thenAnswer(i -> {
            CompanyKpiResultDelegate d = i.getArgument(0);
            if (d.getId() == null) { d.setId(701L + history.size()); history.add(d); }
            when(delegates.lockById(d.getId())).thenReturn(Optional.of(d));
            return d;
        });
        when(delegates.findAllByReviewPeriodIdOrderByAssignedAtDescIdDesc(1L)).thenAnswer(i -> new ArrayList<>(history));
        var first = service.assignDelegate(1L, delegateId, actor);
        assertEquals(actor, first.getAssignedBy());
        assertNotNull(first.getAssignedAt());
        assertThrows(BadRequestException.class, () -> service.assignDelegate(1L, delegateId, actor));
        var revoked = service.revokeDelegate(1L, first.getId(), actor);
        assertEquals(first.getAssignedAt(), revoked.getAssignedAt());
        assertEquals(actor, revoked.getRevokedBy());
        assertNotNull(revoked.getRevokedAt());
        assertThrows(BadRequestException.class, () -> service.revokeDelegate(1L, first.getId(), actor));
        var second = service.assignDelegate(1L, delegateId, actor);
        assertNotEquals(first.getId(), second.getId());
        assertNull(second.getRevokedAt());
        assertEquals(2, service.delegates(1L, actor).size());
        verify(staff, never()).save(any());
        verify(delegates, never()).delete(any());
        verify(results, never()).saveAndFlush(any());
    }

    private KpiCentralResultDecisionRequest decision(Long lock, String reason) {
        var request = new KpiCentralResultDecisionRequest();
        request.setExpectedLockVersion(lock);
        request.setReason(reason);
        return request;
    }

    private KpiCentralResult completeDraft() {
        var request = request();
        request.setItems(List.of(answer(1001L, 4), answer(1002L, 3)));
        service.create(request, actor);
        return saved;
    }

    private void editionQueries() {
        when(results.findByPlanVersionIdAndCheckpointIdAndStatus(anyLong(), anyLong(), any()))
                .thenAnswer(invocation -> editions.values().stream().filter(result ->
                        result.getPlanVersion().getId().equals(invocation.getArgument(0))
                        && result.getCheckpoint().getId().equals(invocation.getArgument(1))
                        && result.getStatus().equals(invocation.getArgument(2))).findFirst());
        when(results.findAllByPlanVersionIdAndCheckpointIdOrderByEditionNumberDesc(101L, 11L))
                .thenAnswer(invocation -> editions.values().stream()
                        .sorted(Comparator.comparingInt(KpiCentralResult::getEditionNumber).reversed()).toList());
    }

    private KpiCentralResult published() {
        editionQueries();
        var result = completeDraft();
        service.publish(result.getId(), decision(0L, null), actor);
        return result;
    }

    private KpiCentralResultEvidence attachment(KpiCentralResult result, long id, String key) {
        var evidence = new KpiCentralResultEvidence();
        evidence.setId(id);
        evidence.setItemId(result.getItems().get(0).getId());
        evidence.setStorageKey(key);
        evidence.setOriginalFilename("evidence.png");
        evidence.setContentType("image/png");
        evidence.setSizeBytes(20L);
        evidence.setUploadedBy(delegateId);
        evidence.setUploadedAt(OffsetDateTime.parse("2027-01-31T10:00:00Z"));
        when(files.findById(id)).thenReturn(Optional.of(evidence));
        return evidence;
    }

    private MockMultipartFile uploadFile() {
        return new MockMultipartFile("file", "evidence.png", "image/png", new byte[]{1, 2});
    }

    @Test void publicationRequiresCompleteItemsAndRecordsAuthenticatedAudit() {
        editionQueries();
        service.create(request(), actor);
        clearInvocations(results);
        var error = assertThrows(BadRequestException.class, () -> service.publish(301L, decision(0L, null), actor));
        assertTrue(error.getMessage().contains("Actual Achievement"));
        assertTrue(error.getMessage().contains("Result Point"));
        noWrites();
        var request = request();
        request.setItems(List.of(answer(1001L, 4), answer(1002L, 3)));
        service.update(301L, request, actor);
        var dto = service.publish(301L, decision(0L, null), actor);
        assertEquals(KpiCentralResultStatus.PUBLISHED, dto.getStatus());
        assertEquals(actor, dto.getPublishedBy());
        assertEquals(OffsetDateTime.parse("2027-02-01T12:00:00Z"), dto.getPublishedAt());
        assertEquals(List.of("CREATE_CORRECTION"), dto.getAllowedActions());
        assertThrows(BadRequestException.class, () -> service.publish(301L, decision(0L, null), actor));
        verifyNoInteractions(storage);
    }

    @Test void publicationRejectsMissingForeignDuplicateOrInvalidVersionItems() {
        var draft = completeDraft();
        clearInvocations(results);
        var removed = draft.getItems().remove(1);
        assertThrows(BadRequestException.class, () -> service.publish(301L, decision(0L, null), actor));
        draft.getItems().add(draft.getItems().get(0));
        assertThrows(BadRequestException.class, () -> service.publish(301L, decision(0L, null), actor));
        draft.getItems().set(1, removed);
        removed.getKpi().setId(999L);
        // A foreign item is not part of the confirmed immutable version.
        var expectedKpi = new Kpi(); expectedKpi.setId(1002L);
        version.getItems().set(1, expectedKpi);
        assertThrows(BadRequestException.class, () -> service.publish(301L, decision(0L, null), actor));
        removed.getKpi().setId(1002L);
        removed.setPoint(6);
        assertThrows(BadRequestException.class, () -> service.publish(301L, decision(0L, null), actor));
        noWrites();
    }

    @Test void initialPublicationCannotReplaceAnExistingPublication() {
        completeDraft();
        var current = new KpiCentralResult(); current.setId(399L);
        when(results.findByPlanVersionIdAndCheckpointIdAndStatus(101L, 11L, KpiCentralResultStatus.PUBLISHED))
                .thenReturn(Optional.of(current));
        clearInvocations(results);
        assertThrows(KpiVersionConflictException.class, () -> service.publish(301L, decision(0L, null), actor));
        assertEquals(KpiCentralResultStatus.DRAFT, saved.getStatus());
        noWrites();
    }

    @ParameterizedTest @ValueSource(strings = {"", "   ", "\t\n"})
    void correctionRequiresNonblankReason(String reason) {
        published();
        clearInvocations(results);
        assertThrows(BadRequestException.class, () -> service.correct(301L, decision(0L, reason), actor));
        noWrites();
    }

    @Test void correctionReasonIsTrimmedAndBoundedAndPublishedContentsStayImmutable() {
        var original = published();
        var publishedAt = original.getPublishedAt();
        assertThrows(BadRequestException.class, () -> service.correct(301L, decision(0L, null), actor));
        assertThrows(BadRequestException.class, () -> service.correct(301L, decision(0L, "x".repeat(10001)), actor));
        var dto = service.correct(301L, decision(0L, "  " + "x".repeat(10000) + "  "), actor);
        assertEquals("x".repeat(10000), dto.getCorrectionReason());
        assertEquals(2, dto.getEditionNumber());
        assertEquals(301L, dto.getPreviousEditionId());
        assertEquals(KpiCentralResultStatus.PUBLISHED, original.getStatus());
        assertEquals(publishedAt, original.getPublishedAt());
        assertEquals(actor, original.getPublishedBy());
        assertNull(dto.getPublishedAt());
        assertNull(dto.getPublishedBy());
        assertNotEquals(original.getItems().get(0).getId(), saved.getItems().get(0).getId());
        assertSame(original.getPlanVersion(), saved.getPlanVersion());
        assertSame(original.getCheckpoint(), saved.getCheckpoint());
        assertEquals(original.getReviewFrequency(), saved.getReviewFrequency());
        assertEquals(original.getItems().get(0).getActualAchievement(), saved.getItems().get(0).getActualAchievement());
        assertEquals(original.getItems().get(0).getComment(), saved.getItems().get(0).getComment());
        assertEquals(List.of("CONTINUE_CORRECTION"), service.get(301L, actor).getAllowedActions());
        assertEquals(dto.getId(), service.get(301L, actor).getWorkingCorrectionId());
        assertThrows(BadRequestException.class, () -> service.correct(301L, decision(0L, "Duplicate"), actor));
    }

    @Test void unchangedAndRepeatedCorrectionsCanPublishAndHistoryPreservesEveryEdition() {
        var first = published();
        var audit = first.getPublishedAt();
        var second = service.correct(first.getId(), decision(0L, "Verified the supplied results"), actor);
        service.publish(second.getId(), decision(0L, null), actor);
        assertEquals(KpiCentralResultStatus.SUPERSEDED, first.getStatus());
        assertEquals(audit, first.getPublishedAt());
        assertEquals(4, first.getItems().get(0).getPoint());
        assertTrue(service.get(first.getId(), actor).getAllowedActions().isEmpty());
        var third = service.correct(second.getId(), decision(0L, "A further check"), actor);
        assertEquals(3, third.getEditionNumber());
        assertEquals(second.getId(), third.getPreviousEditionId());
        service.publish(third.getId(), decision(0L, null), actor);
        assertEquals(List.of(3, 2, 1), service.history(first.getId(), actor).stream()
                .map(KpiCentralResultDto::getEditionNumber).toList());
        assertThrows(BadRequestException.class, () -> service.correct(first.getId(), decision(0L, "Old"), actor));
        verify(results, never()).delete(any());
        verify(staff, never()).save(any());
        verifyNoInteractions(storage);
    }

    @Test void correctionPublicationRejectsChangedPredecessorAndForeignVersionScope() {
        var first = published();
        var correction = service.correct(301L, decision(0L, "Fix"), actor);
        var draft = editions.get(correction.getId());
        draft.setPreviousEditionId(999L);
        assertThrows(KpiVersionConflictException.class, () -> service.publish(draft.getId(), decision(0L, null), actor));
        draft.setPreviousEditionId(first.getId());
        var foreign = version(102L, KpiLevel.COMPANY, null);
        foreign.getItems().addAll(version.getItems());
        when(results.findByPlanVersionIdAndCheckpointIdAndStatus(102L, 11L, KpiCentralResultStatus.PUBLISHED))
                .thenReturn(Optional.of(first));
        when(results.eligibleParticipants(102L, 11L)).thenReturn(1L);
        draft.setPlanVersion(foreign);
        assertThrows(KpiVersionConflictException.class, () -> service.publish(draft.getId(), decision(0L, null), actor));
        assertEquals(KpiCentralResultStatus.PUBLISHED, first.getStatus());
    }

    @Test void allMutationTypesRejectStaleOrMissingLocksBeforeStorageOrWrites() {
        var result = completeDraft();
        var evidence = attachment(result, 701L, "original");
        result.setLockVersion(7L);
        clearInvocations(results, files);
        for (Long expected : Arrays.asList(null, 0L)) {
            assertThrows(KpiVersionConflictException.class, () -> service.publish(301L, decision(expected, null), actor));
            assertThrows(KpiVersionConflictException.class, () -> service.upload(evidence.getItemId(), expected, uploadFile(), actor));
            assertThrows(KpiVersionConflictException.class, () -> service.deleteEvidence(701L, expected, actor));
            result.setStatus(KpiCentralResultStatus.PUBLISHED);
            assertThrows(KpiVersionConflictException.class, () -> service.correct(301L, decision(expected, "Fix"), actor));
            result.setStatus(KpiCentralResultStatus.DRAFT);
        }
        noWrites();
        verifyNoInteractions(storage);
        verify(files, never()).delete(any());
        verify(results, times(8)).lockById(301L);
    }

    @Test void closedPeriodBlocksDecisionsAndEvidenceWritesButKeepsScopedHistoryReadable() {
        var result = published();
        var evidence = attachment(result, 701L, "original");
        period.setStatus(AnnualKpiReviewPeriodStatus.CLOSED);
        assertTrue(service.get(301L, actor).getAllowedActions().isEmpty());
        assertEquals(1, service.history(301L, actor).size());
        assertThrows(BadRequestException.class, () -> service.correct(301L, decision(0L, "Fix"), actor));
        result.setStatus(KpiCentralResultStatus.DRAFT);
        assertThrows(BadRequestException.class, () -> service.publish(301L, decision(0L, null), actor));
        assertThrows(BadRequestException.class, () -> service.upload(evidence.getItemId(), 0L, uploadFile(), actor));
        assertThrows(BadRequestException.class, () -> service.deleteEvidence(701L, 0L, actor));
        verifyNoInteractions(storage);
    }

    @Test void revokedDelegateCannotReadHistoryOrEvidenceOrPerformAnyDecision() {
        var result = completeDraft();
        var evidence = attachment(result, 701L, "original");
        authenticate("CAN_ENTER_COMPANY_KPI_RESULTS");
        clearInvocations(results, files);
        assertThrows(AccessDeniedException.class, () -> service.publish(301L, decision(0L, null), actor));
        assertThrows(AccessDeniedException.class, () -> service.correct(301L, decision(0L, "Fix"), actor));
        assertThrows(AccessDeniedException.class, () -> service.history(301L, actor));
        assertThrows(AccessDeniedException.class, () -> service.evidence(evidence.getItemId(), actor));
        assertThrows(AccessDeniedException.class, () -> service.download(701L, actor));
        assertThrows(AccessDeniedException.class, () -> service.upload(evidence.getItemId(), 0L, uploadFile(), actor));
        assertThrows(AccessDeniedException.class, () -> service.deleteEvidence(701L, 0L, actor));
        noWrites();
        verifyNoInteractions(storage);
    }

    @Test void departmentPermissionCannotReadOrPublishCompanyEvidence() {
        var result = completeDraft();
        var evidence = attachment(result, 701L, "original");
        authenticate("CAN_MANAGE_DEPARTMENT_KPI");
        assertThrows(AccessDeniedException.class, () -> service.history(301L, actor));
        assertThrows(AccessDeniedException.class, () -> service.publish(301L, decision(0L, null), actor));
        assertThrows(AccessDeniedException.class, () -> service.download(evidence.getId(), actor));
        verifyNoInteractions(storage);
    }

    @Test void correctionCopiesEvidenceIndependentlyWithOriginalUploaderAndTimestamp() {
        var original = published();
        var evidence = attachment(original, 701L, "original");
        when(files.findAllByItemIdInOrderByUploadedAtAscIdAsc(original.getItems().stream().map(KpiCentralResultItem::getId).toList()))
                .thenReturn(List.of(evidence));
        when(storage.copy("original", "evidence.png"))
                .thenReturn(new KpiAssessmentEvidenceStorage.Stored("copy", "evidence.png", "image/png", 20));
        service.correct(301L, decision(0L, "Fix"), actor);
        var captor = ArgumentCaptor.forClass(KpiCentralResultEvidence.class);
        verify(files).saveAndFlush(captor.capture());
        var copied = captor.getValue();
        assertNull(copied.getId());
        assertEquals("copy", copied.getStorageKey());
        assertNotEquals(evidence.getItemId(), copied.getItemId());
        assertEquals(saved.getItems().get(0).getId(), copied.getItemId());
        assertEquals(evidence.getUploadedBy(), copied.getUploadedBy());
        assertEquals(evidence.getUploadedAt(), copied.getUploadedAt());
        assertEquals(evidence.getOriginalFilename(), copied.getOriginalFilename());
        assertEquals(evidence.getContentType(), copied.getContentType());
        assertEquals(evidence.getSizeBytes(), copied.getSizeBytes());
        verify(storage, never()).delete(any());
    }

    @Test void failedSecondEvidenceCopyCleansOnlyNewFiles() {
        var original = published();
        var first = attachment(original, 701L, "original1");
        var second = attachment(original, 702L, "original2");
        when(files.findAllByItemIdInOrderByUploadedAtAscIdAsc(original.getItems().stream().map(KpiCentralResultItem::getId).toList()))
                .thenReturn(List.of(first, second));
        when(storage.copy("original1", "evidence.png"))
                .thenReturn(new KpiAssessmentEvidenceStorage.Stored("new1", "evidence.png", "image/png", 20));
        when(storage.copy("original2", "evidence.png")).thenThrow(new IllegalStateException("Copy failed"));
        assertThrows(IllegalStateException.class, () -> service.correct(301L, decision(0L, "Fix"), actor));
        verify(storage).delete("new1");
        verify(storage, never()).delete("original1");
        verify(storage, never()).delete("original2");
        assertEquals(KpiCentralResultStatus.PUBLISHED, original.getStatus());
    }

    @Test void copiedEvidenceMetadataFailureCleansCreatedFileAndPreservesPublication() {
        var original = published();
        var evidence = attachment(original, 701L, "original");
        when(files.findAllByItemIdInOrderByUploadedAtAscIdAsc(original.getItems().stream().map(KpiCentralResultItem::getId).toList()))
                .thenReturn(List.of(evidence));
        when(storage.copy("original", "evidence.png"))
                .thenReturn(new KpiAssessmentEvidenceStorage.Stored("new", "evidence.png", "image/png", 20));
        when(files.saveAndFlush(any())).thenThrow(new IllegalStateException("Metadata constraint"));
        assertThrows(IllegalStateException.class, () -> service.correct(301L, decision(0L, "Fix"), actor));
        verify(storage).delete("new");
        verify(storage, never()).delete("original");
        assertEquals(KpiCentralResultStatus.PUBLISHED, original.getStatus());
    }

    @Test void uploadReturnsMetadataAndLockAndCleansFileOnMetadataFailure() {
        var draft = completeDraft();
        var input = uploadFile();
        when(storage.store(input)).thenReturn(new KpiAssessmentEvidenceStorage.Stored("new", "evidence.png", "image/png", 20));
        when(files.saveAndFlush(any())).thenAnswer(invocation -> {
            KpiCentralResultEvidence file = invocation.getArgument(0); file.setId(701L); return file;
        });
        var dto = service.upload(draft.getItems().get(0).getId(), 0L, input, actor);
        assertEquals(701L, dto.evidence().getId());
        assertEquals(actor, dto.evidence().getUploadedBy());
        assertEquals(0, dto.resultLockVersion());
        doThrow(new IllegalStateException("DB failed")).when(files).saveAndFlush(any());
        assertThrows(IllegalStateException.class, () -> service.upload(draft.getItems().get(0).getId(), 0L, input, actor));
        verify(storage).delete("new");
    }

    @Test void uploadRollbackRemovesNewFileButCommittedUploadDoesNot() {
        var draft = completeDraft();
        var input = uploadFile();
        when(storage.store(input)).thenReturn(new KpiAssessmentEvidenceStorage.Stored("new", "evidence.png", "image/png", 20));
        TransactionSynchronizationManager.initSynchronization();
        service.upload(draft.getItems().get(0).getId(), 0L, input, actor);
        var hooks = TransactionSynchronizationManager.getSynchronizations();
        hooks.forEach(hook -> hook.afterCompletion(TransactionSynchronization.STATUS_COMMITTED));
        verify(storage, never()).delete(any());
        hooks.forEach(hook -> hook.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK));
        verify(storage).delete("new");
    }

    @Test void correctionRollbackRemovesCopiedEvidenceAndNotOriginal() {
        var original = published();
        var evidence = attachment(original, 701L, "original");
        when(files.findAllByItemIdInOrderByUploadedAtAscIdAsc(original.getItems().stream().map(KpiCentralResultItem::getId).toList()))
                .thenReturn(List.of(evidence));
        when(storage.copy("original", "evidence.png"))
                .thenReturn(new KpiAssessmentEvidenceStorage.Stored("copy", "evidence.png", "image/png", 20));
        TransactionSynchronizationManager.initSynchronization();
        service.correct(301L, decision(0L, "Fix"), actor);
        TransactionSynchronizationManager.getSynchronizations().forEach(hook -> hook.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK));
        verify(storage).delete("copy");
        verify(storage, never()).delete("original");
    }

    @Test void evidenceDeleteWaitsForCommitAndRollbackDoesNotDeletePhysicalFile() {
        var draft = completeDraft();
        var evidence = attachment(draft, 701L, "original");
        TransactionSynchronizationManager.initSynchronization();
        var dto = service.deleteEvidence(701L, 0L, actor);
        assertEquals(evidence.getId(), dto.evidence().getId());
        verify(files).delete(evidence);
        verify(files).flush();
        verify(storage, never()).delete(any());
        TransactionSynchronizationManager.getSynchronizations().forEach(hook -> hook.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK));
        verify(storage, never()).delete(any());
        TransactionSynchronizationManager.getSynchronizations().forEach(TransactionSynchronization::afterCommit);
        verify(storage).delete("original");
    }

    @ParameterizedTest @EnumSource(value = KpiCentralResultStatus.class, names = "DRAFT", mode = EnumSource.Mode.EXCLUDE)
    void historicalEvidenceIsReadableButCannotBeChanged(KpiCentralResultStatus status) {
        var result = completeDraft(); result.setStatus(status);
        var evidence = attachment(result, 701L, "original");
        when(files.findAllByItemIdOrderByUploadedAtAscIdAsc(evidence.getItemId())).thenReturn(List.of(evidence));
        var resource = new ByteArrayResource(new byte[]{1});
        when(storage.resource("original")).thenReturn(resource);
        assertEquals(701L, service.evidence(evidence.getItemId(), actor).get(0).getId());
        assertSame(resource, service.download(701L, actor).resource());
        assertThrows(BadRequestException.class, () -> service.upload(evidence.getItemId(), 0L, uploadFile(), actor));
        assertThrows(BadRequestException.class, () -> service.deleteEvidence(701L, 0L, actor));
        verify(storage, never()).store(any());
        verify(storage, never()).delete(any());
        verify(files, never()).delete(any());
    }

    @Test void delegatesCannotManageAssignmentsAndOwnersCannotRevokeForeignOrClosedAssignments() {
        authenticate("CAN_ENTER_COMPANY_KPI_RESULTS");
        assertThrows(AccessDeniedException.class, () -> service.delegates(1L, actor));
        assertThrows(AccessDeniedException.class, () -> service.assignDelegate(1L, delegateId, actor));
        assertThrows(AccessDeniedException.class, () -> service.revokeDelegate(1L, 701L, actor));
        authenticate("CAN_MANAGE_COMPANY_KPI");
        var foreign = new CompanyKpiResultDelegate(); foreign.setId(701L); foreign.setReviewPeriodId(2L);
        when(delegates.lockById(701L)).thenReturn(Optional.of(foreign));
        assertThrows(AccessDeniedException.class, () -> service.revokeDelegate(1L, 701L, actor));
        assertNull(foreign.getRevokedAt());
        period.setStatus(AnnualKpiReviewPeriodStatus.CLOSED);
        assertThrows(BadRequestException.class, () -> service.assignDelegate(1L, delegateId, actor));
        assertThrows(BadRequestException.class, () -> service.revokeDelegate(1L, 701L, actor));
        noWrites();
    }
}
