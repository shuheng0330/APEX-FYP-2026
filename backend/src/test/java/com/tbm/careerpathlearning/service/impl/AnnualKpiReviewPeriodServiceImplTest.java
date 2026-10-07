package com.tbm.careerpathlearning.service.impl;

import com.tbm.careerpathlearning.dto.*;
import com.tbm.careerpathlearning.enums.*;
import com.tbm.careerpathlearning.exception.BadRequestException;
import com.tbm.careerpathlearning.mapper.AnnualKpiReviewPeriodMapper;
import com.tbm.careerpathlearning.model.*;
import com.tbm.careerpathlearning.repository.*;
import com.tbm.careerpathlearning.service.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mapstruct.factory.Mappers;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import java.util.function.Consumer;
import java.util.stream.Stream;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class AnnualKpiReviewPeriodServiceImplTest {
    private final AnnualKpiReviewPeriodRepository periods = mock(AnnualKpiReviewPeriodRepository.class);
    private final ReviewPeriodRoleConfigurationRepository configurations = mock(ReviewPeriodRoleConfigurationRepository.class);
    private final ReviewCheckpointRepository checkpoints = mock(ReviewCheckpointRepository.class);
    private final ReviewPeriodParticipantRepository participants = mock(ReviewPeriodParticipantRepository.class);
    private final RoleRepository roles = mock(RoleRepository.class);
    private final EmployeeLevelRepository levels = mock(EmployeeLevelRepository.class);
    private final ReviewPeriodEmployeeLevelConfigurationRepository weights = mock(ReviewPeriodEmployeeLevelConfigurationRepository.class);
    private final Map<Long, List<ReviewPeriodEmployeeLevelConfiguration>> storedWeights = new HashMap<>();
    private final Map<Long, AnnualKpiReviewPeriod> stored = new HashMap<>();
    private final Map<Long, List<ReviewPeriodRoleConfiguration>> storedRoles = new HashMap<>();
    private final Map<Long, List<ReviewCheckpoint>> storedCheckpoints = new HashMap<>();
    private final UUID actor = UUID.randomUUID();
    private final Clock clock = Clock.fixed(Instant.parse("2026-10-06T00:00:00Z"), ZoneId.of("Asia/Kuala_Lumpur"));
    private AnnualKpiReviewPeriodServiceImpl service;
    private Role sales;
    private Role manager;

    @BeforeEach
    void setup() {
        service = new AnnualKpiReviewPeriodServiceImpl(periods, configurations, checkpoints, participants, roles,
                Mappers.getMapper(AnnualKpiReviewPeriodMapper.class), new AnnualReviewPeriodConfigurationValidator(),
                new ReviewCheckpointGenerator(), clock, levels, weights);
        when(levels.findAllByOrderByDisplayOrderAsc()).thenReturn(EmployeeLevelFixtures.levels());
        when(weights.saveAllAndFlush(any())).thenAnswer(i -> {
            List<ReviewPeriodEmployeeLevelConfiguration> items = i.getArgument(0);
            for (int n = 0; n < items.size(); n++) items.get(n).setId(items.get(n).getReviewPeriod().getId() * 10 + n);
            if (!items.isEmpty()) storedWeights.put(items.get(0).getReviewPeriod().getId(), items);
            return items;
        });
        when(weights.findAllByReviewPeriodIdOrderByEmployeeLevelDisplayOrderAsc(anyLong()))
                .thenAnswer(i -> storedWeights.getOrDefault(i.getArgument(0), List.of()));
        doAnswer(i -> { storedWeights.remove(i.getArgument(0)); return null; }).when(weights).deleteAllByReviewPeriodId(anyLong());
        sales = role(1L, "Retail Sales", null);
        manager = role(2L, "Manager", ReviewFrequency.QUARTERLY);
        sales.setEmployeeLevel(EmployeeLevelFixtures.levels().get(3));
        manager.setEmployeeLevel(EmployeeLevelFixtures.levels().get(1));
        when(roles.findAllByIdInAndIsDeletedIsFalse(anySet())).thenAnswer(invocation -> {
            Set<Long> ids = invocation.getArgument(0);
            return Stream.of(sales, manager).filter(r -> ids.contains(r.getId()) && !r.isDeleted()).toList();
        });
        when(periods.findById(anyLong())).thenAnswer(i -> Optional.ofNullable(stored.get(i.getArgument(0))));
        when(periods.saveAndFlush(any())).thenAnswer(i -> {
            AnnualKpiReviewPeriod period = i.getArgument(0);
            if (period.getId() == null) period.setId((long) stored.size() + 1);
            stored.put(period.getId(), period);
            return period;
        });
        when(configurations.saveAllAndFlush(any())).thenAnswer(i -> {
            List<ReviewPeriodRoleConfiguration> items = i.getArgument(0);
            if (!items.isEmpty()) storedRoles.put(items.get(0).getReviewPeriod().getId(), items);
            return items;
        });
        when(checkpoints.saveAllAndFlush(any())).thenAnswer(i -> {
            List<ReviewCheckpoint> items = i.getArgument(0);
            if (!items.isEmpty()) storedCheckpoints.put(items.get(0).getReviewPeriod().getId(), items);
            return items;
        });
        when(configurations.findAllByReviewPeriodIdOrderByIdAsc(anyLong()))
                .thenAnswer(i -> storedRoles.getOrDefault(i.getArgument(0), List.of()));
        when(configurations.findPublishedConfigurationsBefore(any(), any())).thenAnswer(i -> {
            LocalDate startDate = i.getArgument(0);
            Collection<AnnualKpiReviewPeriodStatus> statuses = i.getArgument(1);
            return storedRoles.values().stream().flatMap(Collection::stream)
                    .filter(c -> statuses.contains(c.getReviewPeriod().getStatus())
                            && c.getReviewPeriod().getEndDate().isBefore(startDate))
                    .sorted(Comparator.comparing((ReviewPeriodRoleConfiguration c) -> c.getReviewPeriod().getEndDate())
                            .thenComparing(c -> c.getReviewPeriod().getStartDate())
                            .thenComparing(c -> c.getReviewPeriod().getId()).reversed())
                    .toList();
        });
        when(checkpoints.findAllByReviewPeriodIdOrderByReviewFrequencyAscSequenceNumberAsc(anyLong()))
                .thenAnswer(i -> storedCheckpoints.getOrDefault(i.getArgument(0), List.of()));
        doAnswer(i -> { storedRoles.remove(i.getArgument(0)); return null; }).when(configurations).deleteAllByReviewPeriodId(anyLong());
        doAnswer(i -> { storedCheckpoints.remove(i.getArgument(0)); return null; }).when(checkpoints).deleteAllByReviewPeriodId(anyLong());
    }

    @Test
    void incompleteDraftKeepsDefaultWeightsAndDoesNotGenerateCheckpoints() {
        var dto = service.create(new AnnualKpiReviewPeriodRequest(), false, actor);
        assertThat(dto.getStatus()).isEqualTo(AnnualKpiReviewPeriodStatus.DRAFT);
        assertThat(dto.getReferenceNumber()).isNotBlank();
        assertThat(dto.getKpiPerformanceWeight()).isEqualByComparingTo("50");
        assertThat(dto.getCreatedBy()).isEqualTo(actor);
        assertThat(dto.getCheckpoints()).isEmpty();
        assertThat(dto.getEmployeeLevelConfigurations()).isEmpty();
        verify(periods, never()).existsOverlappingPeriod(any(), any(), any(), any());
    }

    @Test
    void datedCreationLoadsEditableLookupDefaultsAndFrozenRoleLevel() {
        var request = validRequest(); request.setEmployeeLevelConfigurations(null);
        var dto = service.create(request, true, actor);
        assertThat(dto.getEmployeeLevelConfigurations()).hasSize(6);
        assertThat(dto.getEmployeeLevelConfigurations().get(3).getIndividualKpiWeight()).isEqualByComparingTo("60");
        assertThat(dto.getRoleConfigurations().get(0).getEmployeeLevelId()).isEqualTo(4L);
        sales.setEmployeeLevel(EmployeeLevelFixtures.levels().get(5));
        assertThat(service.get(dto.getId()).getRoleConfigurations().get(0).getEmployeeLevelId()).isEqualTo(4L);
        request.setName("Updated name");
        assertThat(service.update(dto.getId(), request, actor).getRoleConfigurations().get(0).getEmployeeLevelId()).isEqualTo(4L);
    }

    @Test
    void draftPublicationResolvesCurrentRoleClassification() {
        var draft = service.create(validRequest(), false, actor);
        sales.setEmployeeLevel(EmployeeLevelFixtures.levels().get(5));
        assertThat(service.publish(draft.getId(), actor).getRoleConfigurations().get(0).getEmployeeLevelId()).isEqualTo(6L);
    }

    @Test
    void unmappedRoleAllowedInDraftButNotPublication() {
        sales.setEmployeeLevel(null);
        var draft = service.create(validRequest(), false, actor);
        assertThatThrownBy(() -> service.publish(draft.getId(), actor)).hasMessageContaining("Employee Level");
    }

    @ParameterizedTest
    @ValueSource(strings = {"-0.01", "100.01", "15.001"})
    void invalidLevelWeightsFailEvenForDrafts(String value) {
        var request = validRequest();
        request.getEmployeeLevelConfigurations().get(0).setCompanyKpiWeight(new BigDecimal(value));
        assertThatThrownBy(() -> service.create(request, false, actor)).isInstanceOf(BadRequestException.class);
    }

    @Test
    void duplicateUnknownAndMissingLevelsCannotPublish() {
        var duplicate = validRequest();
        duplicate.getEmployeeLevelConfigurations().get(1).setEmployeeLevelId(1L);
        assertThatThrownBy(() -> service.create(duplicate, true, actor)).hasMessageContaining("unique");
        var unknown = validRequest(); unknown.getEmployeeLevelConfigurations().get(0).setEmployeeLevelId(999L);
        assertThatThrownBy(() -> service.create(unknown, false, actor)).hasMessageContaining("valid");
        var missing = validRequest(); missing.getEmployeeLevelConfigurations().remove(0);
        var draft = service.create(missing, false, actor);
        assertThatThrownBy(() -> service.publish(draft.getId(), actor)).hasMessageContaining("Every Employee Level");
    }

    @Test
    void incompleteDraftLevelWeightsMayBeSavedButMustBeCompletedForPublication() {
        var request = validRequest(); request.getEmployeeLevelConfigurations().get(0).setCompanyKpiWeight(null);
        var draft = service.create(request, false, actor);
        assertThat(draft.getEmployeeLevelConfigurations().get(0).getCompanyKpiWeight()).isNull();
        assertThatThrownBy(() -> service.publish(draft.getId(), actor)).isInstanceOf(BadRequestException.class);
    }

    @Test
    void previousPublishedValuesAndFrequenciesAreCopiedIndependently() {
        var source = service.create(validRequest(), true, actor);
        storedWeights.get(source.getId()).get(3).setCompanyKpiWeight(new BigDecimal("20"));
        storedWeights.get(source.getId()).get(3).setIndividualKpiWeight(new BigDecimal("55"));
        when(periods.findFirstByStatusInAndEndDateBeforeOrderByEndDateDescStartDateDescIdDesc(any(), eq(LocalDate.of(2028, 1, 1))))
                .thenReturn(Optional.of(stored.get(source.getId())));
        when(roles.findAllByIsDeletedIsFalse()).thenReturn(List.of(sales, manager));
        sales.setEmployeeLevel(EmployeeLevelFixtures.levels().get(5));
        var defaults = service.creationDefaults(LocalDate.of(2028, 1, 1));
        assertThat(defaults.getSourceReviewPeriodId()).isEqualTo(source.getId());
        assertThat(defaults.getEmployeeLevelConfigurations()).allMatch(c -> c.getId() == null);
        assertThat(defaults.getRoleConfigurations().get(0).getReviewFrequency()).isEqualTo(ReviewFrequency.MONTHLY);
        assertThat(defaults.getRoleConfigurations().get(0).getEmployeeLevelId()).isEqualTo(6L);
        assertThat(defaults.getRoleConfigurations().get(1).getReviewFrequency()).isEqualTo(ReviewFrequency.QUARTERLY);
        var next = validRequest(); next.setName("2028 Annual Review"); next.setStartDate(LocalDate.of(2028, 1, 1));
        next.setEndDate(LocalDate.of(2028, 12, 31)); setSetup(next, next.getStartDate());
        next.setEmployeeLevelConfigurations(null); next.getRoleConfigurations().get(0).setReviewFrequency(null);
        next.setAttitudeSelfAssessmentDeadline(null); next.setSuperiorAttitudeEvaluationDeadline(null);
        next.setAppraisalRecommendationDeadline(null); next.setHrFinalisationDeadline(null);
        var created = service.create(next, false, actor);
        assertThat(created.getEmployeeLevelConfigurations().get(3).getCompanyKpiWeight()).isEqualByComparingTo("20");
        assertThat(created.getRoleConfigurations().get(0).getReviewFrequency()).isEqualTo(ReviewFrequency.MONTHLY);
        storedWeights.get(created.getId()).get(3).setCompanyKpiWeight(new BigDecimal("30"));
        assertThat(storedWeights.get(source.getId()).get(3).getCompanyKpiWeight()).isEqualByComparingTo("20");
        next.getRoleConfigurations().get(0).setReviewFrequency(ReviewFrequency.QUARTERLY);
        var updated = service.update(created.getId(), next, actor);
        assertThat(updated.getRoleConfigurations().get(0).getReviewFrequency()).isEqualTo(ReviewFrequency.QUARTERLY);
        assertThat(storedRoles.get(source.getId()).get(0).getReviewFrequency()).isEqualTo(ReviewFrequency.MONTHLY);
        assertThat(sales.getDefaultReviewFrequency()).isNull();
        verify(periods, atLeastOnce()).findFirstByStatusInAndEndDateBeforeOrderByEndDateDescStartDateDescIdDesc(
                List.of(AnnualKpiReviewPeriodStatus.UPCOMING, AnnualKpiReviewPeriodStatus.OPEN, AnnualKpiReviewPeriodStatus.CLOSED),
                LocalDate.of(2028, 1, 1));
    }

    @Test
    void eachRoleReusesItsLatestSavedFrequencyEvenWhenMissingFromNewestPeriod() {
        var older = savedFrequency(10L, 2025, sales, ReviewFrequency.MONTHLY);
        var oldManager = savedFrequency(10L, 2025, manager, ReviewFrequency.MONTHLY);
        var newerManager = savedFrequency(11L, 2026, manager, ReviewFrequency.ANNUALLY);
        storedRoles.put(10L, List.of(older, oldManager));
        storedRoles.put(11L, List.of(newerManager));
        when(roles.findAllByIsDeletedIsFalse()).thenReturn(List.of(sales, manager));

        var defaults = service.creationDefaults(LocalDate.of(2027, 1, 1));
        assertThat(defaults.getRoleConfigurations()).extracting(AnnualKpiReviewPeriodDto.RoleConfiguration::getReviewFrequency)
                .containsExactly(ReviewFrequency.MONTHLY, ReviewFrequency.ANNUALLY);
        // Employee Level weightages still use lookup defaults when no weightage source is available.
        assertThat(defaults.getEmployeeLevelConfigurations()).hasSize(6);
        var request = validRequest();
        request.setRoleConfigurations(List.of(frequency(1L, null), frequency(2L, null)));
        var created = service.create(request, true, actor);
        assertThat(created.getRoleConfigurations()).extracting(AnnualKpiReviewPeriodDto.RoleConfiguration::getReviewFrequency)
                .containsExactly(ReviewFrequency.MONTHLY, ReviewFrequency.ANNUALLY);
        assertThat(created.getCheckpoints()).hasSize(13);
        verify(configurations, atLeastOnce()).findPublishedConfigurationsBefore(LocalDate.of(2027, 1, 1),
                List.of(AnnualKpiReviewPeriodStatus.UPCOMING, AnnualKpiReviewPeriodStatus.OPEN, AnnualKpiReviewPeriodStatus.CLOSED));
    }

    @Test
    void creationDefaultsUseRoleDefaultsAndAnnualFallbackWhenNoHistoryExists() {
        when(roles.findAllByIsDeletedIsFalse()).thenReturn(List.of(sales, manager));
        var defaults = service.creationDefaults(LocalDate.of(2027, 1, 1));
        assertThat(defaults.getRoleConfigurations()).extracting(AnnualKpiReviewPeriodDto.RoleConfiguration::getReviewFrequency)
                .containsExactly(ReviewFrequency.ANNUALLY, ReviewFrequency.QUARTERLY);
    }

    @Test
    void explicitCreationAndPreviewFrequenciesOverrideHistory() {
        storedRoles.put(10L, List.of(savedFrequency(10L, 2026, sales, ReviewFrequency.QUARTERLY)));
        var request = validRequest();
        assertThat(service.preview(request, null).getRoleConfigurations().get(0).getReviewFrequency())
                .isEqualTo(ReviewFrequency.MONTHLY);
        var created = service.create(request, false, actor);
        assertThat(created.getRoleConfigurations().get(0).getReviewFrequency()).isEqualTo(ReviewFrequency.MONTHLY);
        request.getRoleConfigurations().get(0).setReviewFrequency(null);
        clearInvocations(configurations);
        assertThat(service.update(created.getId(), request, actor).getRoleConfigurations().get(0).getReviewFrequency())
                .isEqualTo(ReviewFrequency.MONTHLY);
        verify(configurations, never()).findPublishedConfigurationsBefore(any(), any());
    }

    private ReviewPeriodRoleConfiguration savedFrequency(Long id, int year, Role role, ReviewFrequency frequency) {
        var period = new AnnualKpiReviewPeriod();
        period.setId(id);
        period.setStartDate(LocalDate.of(year, 1, 1));
        period.setEndDate(LocalDate.of(year, 12, 31));
        period.setStatus(AnnualKpiReviewPeriodStatus.CLOSED);
        var configuration = new ReviewPeriodRoleConfiguration();
        configuration.setReviewPeriod(period);
        configuration.setRole(role);
        configuration.setReviewFrequency(frequency);
        return configuration;
    }

    @Test
    void explicitValuesOverrideDefaultsAndUpdatesNeverReloadThem() {
        var request = validRequest();
        request.getEmployeeLevelConfigurations().get(3).setCompanyKpiWeight(new BigDecimal("20"));
        request.getEmployeeLevelConfigurations().get(3).setIndividualKpiWeight(new BigDecimal("55"));
        var draft = service.create(request, false, actor);
        request.setEmployeeLevelConfigurations(null);
        clearInvocations(periods);
        var updated = service.update(draft.getId(), request, actor);
        assertThat(updated.getEmployeeLevelConfigurations().get(3).getIndividualKpiWeight()).isEqualByComparingTo("55");
        verify(periods, never()).findFirstByStatusInAndEndDateBeforeOrderByEndDateDescStartDateDescIdDesc(any(), any());
    }

    @Test
    void publicationStoresConfigurationAndGeneratesOnlyDistinctApplicableFrequencies() {
        var request = validRequest();
        request.getRoleConfigurations().add(frequency(2L, ReviewFrequency.MONTHLY));
        var dto = service.create(request, true, actor);
        assertThat(dto.getStatus()).isEqualTo(AnnualKpiReviewPeriodStatus.UPCOMING);
        assertThat(dto.getRoleConfigurations()).hasSize(2);
        assertThat(dto.getCheckpoints()).hasSize(12);
        var last = dto.getCheckpoints().get(11);
        assertThat(last.getSelfAssessmentDeadline()).isEqualTo(LocalDate.of(2028, 1, 5));
        assertThat(last.getSuperiorAssessmentDeadline()).isEqualTo(LocalDate.of(2028, 1, 10));
        assertThat(dto.getHrFinalisationDeadline()).isEqualTo(request.getHrFinalisationDeadline());
        assertThat(dto.getAnnualKpiConsolidationMethod()).isEqualTo(AnnualKpiConsolidationMethod.FINAL_CHECKPOINT);
        var order = inOrder(periods);
        order.verify(periods).lockConfiguration();
        order.verify(periods).existsByName(request.getName());
        order.verify(periods).existsOverlappingPeriod(request.getStartDate(), request.getEndDate(), null,
                List.of(AnnualKpiReviewPeriodStatus.UPCOMING, AnnualKpiReviewPeriodStatus.OPEN));
        order.verify(periods).saveAndFlush(any());
    }

    @Test
    void defaultsAndOverridesArePeriodLocalAndDoNotChangeRoleDefaults() {
        var request = validRequest();
        request.setRoleConfigurations(new ArrayList<>(List.of(frequency(1L, null), frequency(2L, null))));
        var dto = service.create(request, true, actor);
        assertThat(dto.getRoleConfigurations()).extracting(AnnualKpiReviewPeriodDto.RoleConfiguration::getReviewFrequency)
                .containsExactly(ReviewFrequency.ANNUALLY, ReviewFrequency.QUARTERLY);
        assertThat(dto.getCheckpoints()).hasSize(5);
        assertThat(sales.getDefaultReviewFrequency()).isNull();
        assertThat(manager.getDefaultReviewFrequency()).isEqualTo(ReviewFrequency.QUARTERLY);
        request.getRoleConfigurations().get(1).setReviewFrequency(ReviewFrequency.MONTHLY);
        dto = service.update(dto.getId(), request, actor);
        assertThat(dto.getCheckpoints()).hasSize(13);
        assertThat(manager.getDefaultReviewFrequency()).isEqualTo(ReviewFrequency.QUARTERLY);
    }

    @Test
    void updateRegeneratesUpcomingScheduleAndPreservesSnapshotIfFrequencyIsOmitted() {
        var request = validRequest();
        Long id = service.create(request, true, actor).getId();
        sales.setDefaultReviewFrequency(ReviewFrequency.QUARTERLY);
        request.getRoleConfigurations().get(0).setReviewFrequency(null);
        request.setEndDate(LocalDate.of(2027, 6, 30));
        request.setAnnualKpiConsolidationMethod(AnnualKpiConsolidationMethod.AVERAGE);
        var dto = service.update(id, request, actor);
        assertThat(dto.getCheckpoints()).hasSize(6);
        assertThat(dto.getRoleConfigurations().get(0).getReviewFrequency()).isEqualTo(ReviewFrequency.MONTHLY);
        assertThat(dto.getAnnualKpiConsolidationMethod()).isEqualTo(AnnualKpiConsolidationMethod.AVERAGE);
        verify(checkpoints).deleteAllByReviewPeriodId(id);
        verify(configurations).deleteAllByReviewPeriodId(id);
        verify(periods).existsOverlappingPeriod(request.getStartDate(), request.getEndDate(), id,
                List.of(AnnualKpiReviewPeriodStatus.UPCOMING, AnnualKpiReviewPeriodStatus.OPEN));
    }

    @Test
    void draftCanBeModifiedWithoutPublishingThenPublishedSeparately() {
        var draft = service.create(new AnnualKpiReviewPeriodRequest(), false, actor);
        var updated = service.update(draft.getId(), validRequest(), actor);
        assertThat(updated.getStatus()).isEqualTo(AnnualKpiReviewPeriodStatus.DRAFT);
        assertThat(updated.getCheckpoints()).isEmpty();
        var published = service.publish(draft.getId(), actor);
        assertThat(published.getStatus()).isEqualTo(AnnualKpiReviewPeriodStatus.UPCOMING);
        assertThat(published.getCheckpoints()).hasSize(12);
        assertThat(published.getReferenceNumber()).isEqualTo(draft.getReferenceNumber());
    }

    @Test
    void publishingWithStartDateReachedOpensImmediatelyWithoutAutoClosing() {
        var request = validRequest();
        request.setStartDate(LocalDate.of(2025, 1, 1));
        request.setEndDate(LocalDate.of(2025, 12, 31));
        setSetup(request, request.getStartDate());
        var dto = service.create(request, true, actor);
        assertThat(dto.getStatus()).isEqualTo(AnnualKpiReviewPeriodStatus.OPEN);
        assertThat(dto.getOpenedAt()).isEqualTo(OffsetDateTime.now(clock));
        assertThat(dto.getClosedAt()).isNull();
    }

    @Test
    void upcomingCanBeRescheduledToOpenToday() {
        var request = validRequest();
        Long id = service.create(request, true, actor).getId();
        request.setStartDate(LocalDate.now(clock));
        setSetup(request, request.getStartDate());
        assertThat(service.update(id, request, actor).getStatus()).isEqualTo(AnnualKpiReviewPeriodStatus.OPEN);
    }

    @ParameterizedTest
    @MethodSource("invalidConfigurations")
    void invalidPublicationIsRejectedBeforePersistence(Consumer<AnnualKpiReviewPeriodRequest> change) {
        var request = validRequest();
        change.accept(request);
        assertThatThrownBy(() -> service.create(request, true, actor)).isInstanceOf(BadRequestException.class);
        verify(periods, never()).saveAndFlush(any());
        verify(configurations, never()).saveAllAndFlush(any());
        verify(checkpoints, never()).saveAllAndFlush(any());
    }

    static Stream<Consumer<AnnualKpiReviewPeriodRequest>> invalidConfigurations() {
        return Stream.of(
                r -> r.setName(null), r -> r.setName(" "), r -> r.setName("x".repeat(256)),
                r -> r.setStartDate(null), r -> r.setEndDate(null), r -> r.setEndDate(r.getStartDate()),
                r -> r.setAnnualKpiConsolidationMethod(null),
                r -> r.getEmployeeLevelConfigurations().get(0).setCompanyKpiWeight(null),
                r -> r.getEmployeeLevelConfigurations().get(0).setIndividualKpiWeight(new BigDecimal("59")),
                r -> r.setKpiPerformanceWeight(new BigDecimal("49")),
                r -> r.setAttitudeEvaluationWeight(null),
                r -> r.setCompanyKpiWeight(new BigDecimal("-1")),
                r -> r.setCompanyKpiWeight(new BigDecimal("15.001")),
                r -> r.setSelfAssessmentDaysAfterCheckpoint(null),
                r -> r.setSelfAssessmentDaysAfterCheckpoint(0),
                r -> r.setSuperiorAssessmentDaysAfterSelfDeadline(-1),
                r -> r.setCompanyKpiCreationDeadline(null), r -> r.setDepartmentKpiCreationDeadline(null),
                r -> r.setIndividualKpiSubmissionDeadline(null), r -> r.setIndividualKpiApprovalDeadline(null),
                r -> r.setAttitudeSelfAssessmentDeadline(null), r -> r.setSuperiorAttitudeEvaluationDeadline(null),
                r -> r.setAppraisalRecommendationDeadline(null), r -> r.setHrFinalisationDeadline(null),
                r -> r.setCompanyKpiCreationDeadline(r.getStartDate().plusDays(1)),
                r -> r.setSuperiorAttitudeEvaluationDeadline(r.getAttitudeSelfAssessmentDeadline()),
                r -> r.setAttitudeSelfAssessmentDeadline(r.getStartDate().minusDays(1)),
                r -> r.setAppraisalRecommendationDeadline(r.getEndDate().plusDays(9)),
                r -> r.setSuperiorAttitudeEvaluationDeadline(r.getAppraisalRecommendationDeadline().plusDays(1)),
                r -> r.setHrFinalisationDeadline(r.getAppraisalRecommendationDeadline()),
                r -> r.setRoleConfigurations(List.of()), r -> r.setRoleConfigurations(null),
                r -> r.setRoleConfigurations(Arrays.asList((AnnualKpiReviewPeriodRequest.RoleFrequency) null)),
                r -> r.setRoleConfigurations(List.of(frequency(99L, null))),
                r -> r.setRoleConfigurations(List.of(frequency(1L, null), frequency(1L, ReviewFrequency.QUARTERLY))));
    }

    @Test
    void overlappingPublishedPeriodIsRejectedButDraftCanOverlap() {
        var request = validRequest();
        when(periods.existsOverlappingPeriod(any(), any(), any(), any())).thenReturn(true);
        assertThatThrownBy(() -> service.create(request, true, actor)).isInstanceOf(BadRequestException.class)
                .hasMessageContaining("overlap");
        assertThat(service.create(request, false, actor).getStatus()).isEqualTo(AnnualKpiReviewPeriodStatus.DRAFT);
    }

    @Test
    void overlappingUpcomingUpdateDoesNotReplaceChildren() {
        var request = validRequest();
        Long id = service.create(request, true, actor).getId();
        when(periods.existsOverlappingPeriod(any(), any(), any(), any())).thenReturn(true);
        assertThatThrownBy(() -> service.update(id, request, actor)).isInstanceOf(BadRequestException.class);
        verify(checkpoints, never()).deleteAllByReviewPeriodId(anyLong());
    }

    @Test
    void duplicateNameIncludingWhitespaceIsRejectedAndSelfNameIsAllowedOnUpdate() {
        var request = validRequest();
        request.setName("  2027 Annual KPI Review  ");
        when(periods.existsByName("2027 Annual KPI Review")).thenReturn(true);
        assertThatThrownBy(() -> service.create(request, false, actor)).hasMessageContaining("already exists");
        when(periods.existsByName("2027 Annual KPI Review")).thenReturn(false);
        Long id = service.create(request, true, actor).getId();
        service.update(id, request, actor);
        when(periods.existsByNameAndIdNot("2027 Annual KPI Review", id)).thenReturn(true);
        assertThatThrownBy(() -> service.update(id, request, actor)).hasMessageContaining("already exists");
    }

    @ParameterizedTest
    @EnumSource(value = AnnualKpiReviewPeriodStatus.class, names = {"OPEN", "CLOSED"})
    void openAndClosedPeriodsAreReadOnly(AnnualKpiReviewPeriodStatus status) {
        Long id = service.create(validRequest(), true, actor).getId();
        stored.get(id).setStatus(status);
        assertThatThrownBy(() -> service.update(id, validRequest(), actor)).isInstanceOf(BadRequestException.class);
        assertThatThrownBy(() -> service.delete(id)).isInstanceOf(BadRequestException.class);
        assertThatThrownBy(() -> service.publish(id, actor)).isInstanceOf(BadRequestException.class);
        assertThat(service.get(id).getStatus()).isEqualTo(status);
    }

    @Test
    void schedulerLagCannotBeUsedToModifyOrDeletePeriodWhoseStartHasArrived() {
        Long id = service.create(validRequest(), true, actor).getId();
        stored.get(id).setStartDate(LocalDate.now(clock));
        assertThatThrownBy(() -> service.update(id, validRequest(), actor)).isInstanceOf(BadRequestException.class);
        assertThatThrownBy(() -> service.delete(id)).isInstanceOf(BadRequestException.class);
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void draftAndUpcomingCanBeDeletedWithTheirChildren(boolean publish) {
        Long id = service.create(validRequest(), publish, actor).getId();
        service.delete(id);
        verify(checkpoints).deleteAllByReviewPeriodId(id);
        verify(configurations).deleteAllByReviewPeriodId(id);
        verify(periods).delete(stored.get(id));
    }

    @Test
    void participantSnapshotsAreNotSilentlyErasedOrChanged() {
        Long id = service.create(validRequest(), true, actor).getId();
        when(participants.existsByReviewPeriodId(id)).thenReturn(true);
        assertThatThrownBy(() -> service.delete(id)).hasMessageContaining("participant records");
        assertThatThrownBy(() -> service.update(id, validRequest(), actor)).hasMessageContaining("participant records");
    }

    @Test
    void schedulePreviewDoesNotPersistOrClaimASavedReference() {
        var dto = service.preview(validRequest(), null);
        assertThat(dto.getCheckpoints()).hasSize(12);
        assertThat(dto.getId()).isNull();
        assertThat(dto.getReferenceNumber()).isNull();
        verify(periods, never()).saveAndFlush(any());
        verify(checkpoints, never()).saveAllAndFlush(any());
        verify(periods, never()).lockConfiguration();
    }

    @Test
    void schedulerOpensDuePeriodWithoutChangingConfigurationOrClosingAnything() {
        Long id = service.create(validRequest(), true, actor).getId();
        var due = stored.get(id);
        due.setStartDate(LocalDate.of(2025, 1, 1));
        due.setEndDate(LocalDate.of(2025, 12, 31));
        when(periods.findAllByStatusAndStartDateLessThanEqual(AnnualKpiReviewPeriodStatus.UPCOMING, LocalDate.now(clock)))
                .thenReturn(List.of(due));
        service.openDuePeriods();
        assertThat(due.getStatus()).isEqualTo(AnnualKpiReviewPeriodStatus.OPEN);
        assertThat(due.getClosedAt()).isNull();
        verify(checkpoints, never()).deleteAllByReviewPeriodId(anyLong());
        service.openDuePeriods();
        verify(periods, never()).findAllByStatus(AnnualKpiReviewPeriodStatus.OPEN);
    }

    @Test
    void schedulerDoesNotOpenConflictingRows() {
        Long id = service.create(validRequest(), true, actor).getId();
        var due = stored.get(id);
        due.setStartDate(LocalDate.now(clock));
        when(periods.findAllByStatusAndStartDateLessThanEqual(any(), any())).thenReturn(List.of(due));
        when(periods.existsOverlappingPeriod(any(), any(), any(), any())).thenReturn(true);
        service.openDuePeriods();
        assertThat(due.getStatus()).isEqualTo(AnnualKpiReviewPeriodStatus.UPCOMING);
    }

    @Test
    void missingPeriodAndDeletedRoleAreRejected() {
        assertThatThrownBy(() -> service.get(99L)).hasMessageContaining("not found");
        assertThatThrownBy(() -> service.update(99L, validRequest(), actor)).hasMessageContaining("not found");
        assertThatThrownBy(() -> service.delete(99L)).hasMessageContaining("not found");
        sales.setDeleted(true);
        assertThatThrownBy(() -> service.create(validRequest(), true, actor)).hasMessageContaining("deleted");
    }

    @Test
    void publishingRejectsARoleDeletedAfterDraftWasSaved() {
        Long id = service.create(validRequest(), false, actor).getId();
        sales.setDeleted(true);
        assertThatThrownBy(() -> service.publish(id, actor)).hasMessageContaining("deleted");
    }

    @Test
    void listAndRoleOptionsUseDtosAndDoNotWriteDefaults() {
        var dto = service.create(validRequest(), true, actor);
        when(periods.findAllByOrderByStartDateDescIdDesc()).thenReturn(List.of(stored.get(dto.getId())));
        assertThat(service.list()).hasSize(1);
        when(roles.findAllByIsDeletedIsFalse()).thenReturn(List.of(sales, manager));
        assertThat(service.availableRoles()).extracting(AnnualKpiReviewPeriodDto.RoleConfiguration::getReviewFrequency)
                .containsExactly(ReviewFrequency.ANNUALLY, ReviewFrequency.QUARTERLY);
        verify(roles, never()).save(any());
    }

    static AnnualKpiReviewPeriodRequest validRequest() {
        var request = new AnnualKpiReviewPeriodRequest();
        request.setName("2027 Annual KPI Review");
        request.setStartDate(LocalDate.of(2027, 1, 1));
        request.setEndDate(LocalDate.of(2027, 12, 31));
        request.setEmployeeLevelConfigurations(EmployeeLevelFixtures.weights());
        request.setAnnualKpiConsolidationMethod(AnnualKpiConsolidationMethod.FINAL_CHECKPOINT);
        setSetup(request, request.getStartDate());
        request.setSelfAssessmentDaysAfterCheckpoint(5);
        request.setSuperiorAssessmentDaysAfterSelfDeadline(5);
        request.setAttitudeSelfAssessmentDeadline(LocalDate.of(2027, 12, 5));
        request.setSuperiorAttitudeEvaluationDeadline(LocalDate.of(2027, 12, 15));
        request.setAppraisalRecommendationDeadline(LocalDate.of(2028, 1, 15));
        request.setHrFinalisationDeadline(LocalDate.of(2028, 1, 25));
        request.setRoleConfigurations(new ArrayList<>(List.of(frequency(1L, ReviewFrequency.MONTHLY))));
        return request;
    }

    private static void setSetup(AnnualKpiReviewPeriodRequest request, LocalDate deadline) {
        request.setCompanyKpiCreationDeadline(deadline);
        request.setDepartmentKpiCreationDeadline(deadline);
        request.setIndividualKpiSubmissionDeadline(deadline);
        request.setIndividualKpiApprovalDeadline(deadline);
    }

    static AnnualKpiReviewPeriodRequest.RoleFrequency frequency(Long id, ReviewFrequency frequency) {
        var item = new AnnualKpiReviewPeriodRequest.RoleFrequency();
        item.setRoleId(id);
        item.setReviewFrequency(frequency);
        return item;
    }

    private Role role(Long id, String name, ReviewFrequency frequency) {
        var role = new Role();
        role.setId(id); role.setName(name); role.setDefaultReviewFrequency(frequency);
        return role;
    }
}
