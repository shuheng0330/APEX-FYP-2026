package com.tbm.careerpathlearning.service.impl;

import com.tbm.careerpathlearning.dto.*;
import com.tbm.careerpathlearning.enums.*;
import com.tbm.careerpathlearning.mapper.AttitudeConfigurationMapper;
import com.tbm.careerpathlearning.model.*;
import com.tbm.careerpathlearning.repository.*;
import com.tbm.careerpathlearning.service.AttitudeConfigurationBinding;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mapstruct.factory.Mappers;
import java.time.*;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class AttitudeConfigurationServiceTest {
    final AttitudeConfigurationRepository configurations=mock(AttitudeConfigurationRepository.class);
    final AnnualKpiReviewPeriodRepository periods=mock(AnnualKpiReviewPeriodRepository.class);
    final ReviewPeriodRoleConfigurationRepository periodRoles=mock(ReviewPeriodRoleConfigurationRepository.class);
    final RoleRepository roles=mock(RoleRepository.class);
    final StaffRepository staff=mock(StaffRepository.class);
    final UUID actor=UUID.randomUUID();
    final Clock clock=Clock.fixed(Instant.parse("2026-10-09T00:00:00Z"),ZoneId.of("Asia/Kuala_Lumpur"));
    final Map<Long,AttitudeConfiguration> saved=new HashMap<>();
    final AttitudeConfigurationServiceImpl service=new AttitudeConfigurationServiceImpl(configurations,periods,periodRoles,roles,staff,
            Mappers.getMapper(AttitudeConfigurationMapper.class),clock);
    Role role;Staff account;
    @BeforeEach void setup() {
        account=new Staff();account.setId(actor);account.setAccountStatus(StaffAccountStatus.ACTIVE);when(staff.findById(actor)).thenReturn(Optional.of(account));
        role=new Role();role.setId(1L);role.setName("Example employee Role");when(roles.findById(1L)).thenReturn(Optional.of(role));
        when(roles.findAllByIsDeletedIsFalse()).thenReturn(List.of(role));
        when(configurations.findById(anyLong())).thenAnswer(i->Optional.ofNullable(saved.get(i.getArgument(0))));
        when(configurations.saveAndFlush(any())).thenAnswer(i->{AttitudeConfiguration c=i.getArgument(0);if(c.getId()==null)c.setId((long)saved.size()+1);
            saved.put(c.getId(),c);long next=1;for(var criterion:c.getCriteria())if(criterion.getId()==null)criterion.setId(c.getId()*100+next++);return c;});
    }
    @Test void incompleteDraftAndEmptyCollectionsAreAllowedAndTrimmed() {
        var request=new AttitudeConfigurationRequest();request.setName("  Working draft  ");var dto=service.create(request,actor);
        assertThat(dto.getName()).isEqualTo("Working draft");assertThat(dto.getStatus()).isEqualTo(AttitudeConfigurationStatus.DRAFT);
        assertThat(dto.getCriteria()).isEmpty();assertThat(dto.getCreatedAt()).isEqualTo(OffsetDateTime.now(clock));
        assertThatThrownBy(()->service.publish(dto.getId(),actor)).hasMessageContaining("five rating");
    }
    @Test void publishPreservesConfigurationAndDoesNotBindExistingPeriods() {
        var dto=service.create(complete(),actor);var published=service.publish(dto.getId(),actor);
        assertThat(published.getStatus()).isEqualTo(AttitudeConfigurationStatus.PUBLISHED);assertThat(published.getPublishedBy()).isEqualTo(actor);
        assertThat(published.getRatingDefinitions()).hasSize(5);assertThat(published.getRoleMappings().get(0).getEvaluationFormat()).isEqualTo(AttitudeEvaluationFormat.SALES);
        verify(periods,never()).saveAndFlush(any());
        assertThatThrownBy(()->service.update(dto.getId(),complete(),actor)).hasMessageContaining("read-only");
        assertThatThrownBy(()->service.publish(dto.getId(),actor)).hasMessageContaining("read-only");
    }
    @Test void copyingPublishedConfigurationCreatesIndependentDraftChildren() {
        var published=service.create(complete(),actor);service.publish(published.getId(),actor);
        var copy=service.copy(published.getId(),actor);var original=saved.get(published.getId());var draft=saved.get(copy.getId());
        assertThat(copy.getId()).isNotEqualTo(published.getId());assertThat(copy.getStatus()).isEqualTo(AttitudeConfigurationStatus.DRAFT);
        assertThat(copy.getPublishedAt()).isNull();assertThat(draft.getCriteria().get(0)).isNotSameAs(original.getCriteria().get(0));
        draft.getCriteria().get(0).setName("Different criterion");draft.getRatingDefinitions().get(0).setLabel("Different label");
        assertThat(original.getCriteria().get(0).getName()).isEqualTo("Integrity");assertThat(original.getRatingDefinitions().get(0).getLabel()).isEqualTo("Point 1");
    }
    @Test void criterionInactiveAffectsNewEditionOnlyAndMappingsAreIndependentOfEmployeeLevel() {
        var request=complete();var extra=new AttitudeConfigurationRequest.Criterion();extra.setCriterionType(AttitudeCriterionType.FORMAT_SPECIFIC);
        extra.setEvaluationFormat(AttitudeEvaluationFormat.MANAGER);extra.setActive(false);request.getCriteria().add(extra);
        var dto=service.create(request,actor);service.publish(dto.getId(),actor);
        assertThat(dto.getCriteria()).hasSize(2);assertThat(dto.getCriteria().get(1).isActive()).isFalse();assertThat(role.getEmployeeLevel()).isNull();
    }
    @Test void firstPublishedConfigurationMayLeaveRolesUnmappedButReportsPeriodReadiness() {
        var request=complete();request.setRoleMappings(List.of());var dto=service.create(request,actor);service.publish(dto.getId(),actor);
        var period=period(1L);period.setAttitudeConfiguration(saved.get(dto.getId()));
        var selected=new ReviewPeriodRoleConfiguration();selected.setRole(role);when(periodRoles.findAllByReviewPeriodIdOrderByIdAsc(1L)).thenReturn(List.of(selected));
        assertThat(service.period(1L,actor).getUnmappedRoleNames()).containsExactly(role.getName());
    }
    @Test void duplicateRoleMappingsUnknownAndSystemRolesAreRejected() {
        var request=complete();request.getRoleMappings().add(request.getRoleMappings().get(0));
        assertThatThrownBy(()->service.create(request,actor)).hasMessageContaining("duplicated");
        var unknown=complete();unknown.getRoleMappings().get(0).setRoleId(99L);
        assertThatThrownBy(()->service.create(unknown,actor)).hasMessageContaining("eligible");
        role.setPerformanceReviewEligible(false);assertThatThrownBy(()->service.create(complete(),actor)).hasMessageContaining("eligible");
    }
    @ParameterizedTest @ValueSource(ints={0,6}) void invalidPointsCannotBeSavedEvenInDraft(int point) {
        var request=complete();request.getRatingDefinitions().get(0).setPoint(point);
        assertThatThrownBy(()->service.create(request,actor)).hasMessageContaining("1 to 5");
    }
    @Test void duplicatePointsAndScopeMismatchRejected() {
        var request=complete();request.getRatingDefinitions().add(request.getRatingDefinitions().get(0));
        assertThatThrownBy(()->service.create(request,actor)).hasMessageContaining("distinct");
        var wrong=complete();wrong.getCriteria().get(0).setEvaluationFormat(AttitudeEvaluationFormat.SALES);
        assertThatThrownBy(()->service.create(wrong,actor)).hasMessageContaining("Shared Core Value");
    }
    @Test void suppliedTextLimitsAndDuplicateNamesRejected() {
        var request=complete();request.setName("x".repeat(256));assertThatThrownBy(()->service.create(request,actor)).hasMessageContaining("255");
        var duplicated=complete();duplicated.getCriteria().add(duplicated.getCriteria().get(0));
        assertThatThrownBy(()->service.create(duplicated,actor)).hasMessageContaining("unique");
    }
    @Test void foreignAndDuplicatedCriterionIdsCannotReplaceAnotherDraft() {
        var a=service.create(complete(),actor);var b=service.create(complete(),actor);var input=complete();input.getCriteria().get(0).setId(a.getCriteria().get(0).getId());
        assertThatThrownBy(()->service.update(b.getId(),input,actor)).hasMessageContaining("does not belong");
        input.getCriteria().add(input.getCriteria().get(0));assertThatThrownBy(()->service.update(a.getId(),input,actor)).hasMessageContaining("duplicated");
    }
    @Test void draftReplacementPreservesMatchingIdsAndAllowsRemoval() {
        var dto=service.create(complete(),actor);var request=complete();request.getCriteria().get(0).setId(dto.getCriteria().get(0).getId());
        request.getCriteria().get(0).setName("Updated");var updated=service.update(dto.getId(),request,actor);
        assertThat(updated.getCriteria().get(0).getId()).isEqualTo(dto.getCriteria().get(0).getId());
        service.update(dto.getId(),new AttitudeConfigurationRequest(),actor);assertThat(saved.get(dto.getId()).getCriteria()).isEmpty();
    }
    @Test void incompleteActiveCriteriaAndRatingDescriptionsBlockPublication() {
        var request=complete();request.getCriteria().get(0).setDescription(" ");var dto=service.create(request,actor);
        assertThatThrownBy(()->service.publish(dto.getId(),actor)).hasMessageContaining("Active criterion description");
        var rating=complete();rating.getRatingDefinitions().get(4).setLabel(null);var other=service.create(rating,actor);
        assertThatThrownBy(()->service.publish(other.getId(),actor)).hasMessageContaining("Rating label");
    }
    @Test void everyFormatNeedsActiveApplicableCriteria() {
        var request=complete();request.getCriteria().get(0).setCriterionType(AttitudeCriterionType.FORMAT_SPECIFIC);
        request.getCriteria().get(0).setEvaluationFormat(AttitudeEvaluationFormat.SALES);var dto=service.create(request,actor);
        assertThatThrownBy(()->service.publish(dto.getId(),actor)).hasMessageContaining("MANAGER");
    }
    @Test void eligibilityRecheckedOnPublicationAfterRoleChanges() {
        var dto=service.create(complete(),actor);role.setDeleted(true);
        assertThatThrownBy(()->service.publish(dto.getId(),actor)).hasMessageContaining("ineligible");
    }
    @Test void explicitInitialBindingOnlyOpenUnboundAndPublished() {
        var period=period(1L);var dto=service.create(complete(),actor);
        assertThatThrownBy(()->service.bindInitially(1L,dto.getId(),actor)).hasMessageContaining("published");
        service.publish(dto.getId(),actor);var bound=service.bindInitially(1L,dto.getId(),actor);
        assertThat(bound.getConfiguration().getId()).isEqualTo(dto.getId());assertThat(bound.isCanBindInitially()).isFalse();
        assertThatThrownBy(()->service.bindInitially(1L,dto.getId(),actor)).hasMessageContaining("without");
        assertThat(period.getUpdatedBy()).isEqualTo(actor);
    }
    @Test void closedDraftAndUpcomingPeriodsCannotBeManuallyBound() {
        for(var status:List.of(AnnualKpiReviewPeriodStatus.DRAFT,AnnualKpiReviewPeriodStatus.UPCOMING,AnnualKpiReviewPeriodStatus.CLOSED)) {
            period(1L).setStatus(status);assertThatThrownBy(()->service.bindInitially(1L,1L,actor)).hasMessageContaining("Open");
        }
    }
    @Test void automaticBindingUsesLatestPublishedButNeverReplacesHistoryAndMissingConfigDoesNotBlockOpening() {
        var binder=new AttitudeConfigurationBinding(configurations);var period=period(1L);binder.bindOnOpening(period);assertThat(period.getAttitudeConfiguration()).isNull();
        var first=new AttitudeConfiguration();first.setId(1L);when(configurations.findFirstByStatusOrderByPublishedAtDescIdDesc(AttitudeConfigurationStatus.PUBLISHED)).thenReturn(Optional.of(first));
        binder.bindOnOpening(period);assertThat(period.getAttitudeConfiguration()).isSameAs(first);
        var next=new AttitudeConfiguration();next.setId(2L);when(configurations.findFirstByStatusOrderByPublishedAtDescIdDesc(AttitudeConfigurationStatus.PUBLISHED)).thenReturn(Optional.of(next));
        binder.bindOnOpening(period);assertThat(period.getAttitudeConfiguration()).isSameAs(first);
        period(2L).setStatus(AnnualKpiReviewPeriodStatus.UPCOMING);binder.bindOnOpening(periods.findById(2L).orElseThrow());
        assertThat(periods.findById(2L).orElseThrow().getAttitudeConfiguration()).isNull();
    }
    @Test void readsDoNotBindOrWriteAndSystemRolesExcludedFromOptions() {
        period(1L);service.period(1L,actor);assertThat(service.current(actor)).isNull();service.list(actor);
        role.setPerformanceReviewEligible(false);assertThat(service.options(actor).getRoles()).isEmpty();
        verify(configurations,never()).saveAndFlush(any());verify(periods,never()).saveAndFlush(any());
    }
    @Test void inactiveAndDeletedActorsDeniedRegardlessOfRoleName() {
        account.setAccountStatus(StaffAccountStatus.INACTIVE);assertThatThrownBy(()->service.create(complete(),actor)).isInstanceOf(org.springframework.security.access.AccessDeniedException.class);
        account.setAccountStatus(StaffAccountStatus.ACTIVE);account.setDeleted(true);assertThatThrownBy(()->service.list(actor)).isInstanceOf(org.springframework.security.access.AccessDeniedException.class);
    }
    AnnualKpiReviewPeriod period(Long id) {var period=new AnnualKpiReviewPeriod();period.setId(id);period.setStatus(AnnualKpiReviewPeriodStatus.OPEN);
        when(periods.findById(id)).thenReturn(Optional.of(period));return period;}
    static AttitudeConfigurationRequest complete() {
        var request=new AttitudeConfigurationRequest();request.setName("Attitude configuration");
        var criterion=new AttitudeConfigurationRequest.Criterion();criterion.setName("Integrity");criterion.setDescription("Acts honestly");
        criterion.setCriterionType(AttitudeCriterionType.SHARED_CORE_VALUE);request.getCriteria().add(criterion);
        for(int point=1;point<=5;point++) {var rating=new AttitudeConfigurationRequest.Rating();rating.setPoint(point);rating.setLabel("Point "+point);
            rating.setDescription("Criterion expectations at point "+point);request.getRatingDefinitions().add(rating);}
        var role=new AttitudeConfigurationRequest.RoleMapping();role.setRoleId(1L);role.setEvaluationFormat(AttitudeEvaluationFormat.SALES);request.getRoleMappings().add(role);return request;
    }
}
