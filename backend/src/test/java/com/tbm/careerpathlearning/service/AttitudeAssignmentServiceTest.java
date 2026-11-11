package com.tbm.careerpathlearning.service;

import com.tbm.careerpathlearning.dto.*;
import com.tbm.careerpathlearning.enums.*;
import com.tbm.careerpathlearning.exception.*;
import com.tbm.careerpathlearning.model.*;
import com.tbm.careerpathlearning.repository.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.security.access.AccessDeniedException;
import java.time.*;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class AttitudeAssignmentServiceTest {
    final AnnualKpiReviewPeriodRepository periods=mock(AnnualKpiReviewPeriodRepository.class);
    final ReviewPeriodRoleConfigurationRepository roles=mock(ReviewPeriodRoleConfigurationRepository.class);
    final ReviewPeriodParticipantRepository participants=mock(ReviewPeriodParticipantRepository.class);
    final AttitudeAssessmentRepository assessments=mock(AttitudeAssessmentRepository.class);
    final AnnualAppraisalRepository appraisals=mock(AnnualAppraisalRepository.class);
    final EmployeeInsightScopeRepository scopes=mock(EmployeeInsightScopeRepository.class);
    final AttitudeConfigurationAssignmentActivityRepository audit=mock(AttitudeConfigurationAssignmentActivityRepository.class);
    final AttitudeConfigurationRepository editions=mock(AttitudeConfigurationRepository.class);
    final AttitudeConfigurationService configurations=mock(AttitudeConfigurationService.class);
    final StaffRepository staff=mock(StaffRepository.class);
    final UUID actor=UUID.randomUUID();
    final List<AttitudeConfigurationAssignmentActivity> history=new ArrayList<>();
    AnnualKpiReviewPeriod period; AttitudeConfiguration old,target; Staff admin; Role role; ReviewPeriodParticipant participant;
    AttitudeAssignmentService service;

    @BeforeEach void setup() {
        role=new Role();role.setId(7L);role.setName("Engineer");
        old=configuration(1L, false);target=configuration(2L,true);
        period=new AnnualKpiReviewPeriod();period.setId(5L);period.setName("Annual Review");period.setStatus(AnnualKpiReviewPeriodStatus.OPEN);
        period.setAttitudeConfiguration(old);period.setParticipantsSnapshottedAt(OffsetDateTime.now());
        participant=new ReviewPeriodParticipant();participant.setId(10L);participant.setReviewPeriod(period);participant.setRole(role);
        participant.setStaffName("Fictional Employee");participant.setRoleName("Published Engineer");participant.setDepartmentName("IT");
        admin=new Staff();admin.setId(actor);admin.setName("Setup Administrator");admin.setAccountStatus(StaffAccountStatus.ACTIVE);
        when(staff.findById(actor)).thenReturn(Optional.of(admin));when(periods.findById(5L)).thenReturn(Optional.of(period));
        var selected=new ReviewPeriodRoleConfiguration();selected.setRole(role);
        when(roles.findAllByReviewPeriodIdOrderByIdAsc(5L)).thenReturn(List.of(selected));
        when(participants.findAllByReviewPeriodId(5L)).thenReturn(List.of(participant));
        when(editions.findAllByOrderByCreatedAtDescIdDesc()).thenReturn(List.of(old,target));
        when(editions.findById(2L)).thenReturn(Optional.of(target));
        when(configurations.period(eq(5L),eq(actor))).thenAnswer(i->context());
        when(audit.findAllByReviewPeriodIdOrderByIdAsc(5L)).thenAnswer(i->List.copyOf(history));
        when(audit.findByRequestId(any())).thenAnswer(i->history.stream().filter(a->a.getRequestId().equals(i.getArgument(0))).findFirst());
        when(audit.saveAndFlush(any())).thenAnswer(i->{history.add(i.getArgument(0));return i.getArgument(0);});
        service=createService(true);
    }
    AttitudeAssignmentService createService(boolean enabled) {
        return new AttitudeAssignmentService(periods,roles,participants,assessments,appraisals,scopes,audit,staff,
                configurations,new AttitudeConfigurationBinding(editions),Clock.fixed(Instant.parse("2026-11-09T04:00:00Z"),ZoneId.of("Asia/Singapore")),enabled);
    }
    @Test void disabledDirectCallsRejectBeforeConfigurationReadsLocksOrWritesIncludingReplay() {
        service=createService(false);
        for(var status:AnnualKpiReviewPeriodStatus.values()) {
            period.setStatus(status);
            assertThatThrownBy(()->service.state(5L,actor)).isInstanceOf(BadRequestException.class).hasMessageContaining("currently unavailable");
            assertThatThrownBy(()->service.update(5L,request(),actor)).isInstanceOf(BadRequestException.class).hasMessageContaining("currently unavailable");
        }
        verifyNoInteractions(periods,roles,participants,assessments,appraisals,scopes,audit,configurations,editions);
        assertThat(period.getAttitudeConfiguration()).isSameAs(old);assertThat(history).isEmpty();
    }
    @Test void disabledFeatureDoesNotBypassInactiveAccountChecks() {
        service=createService(false);admin.setDeleted(true);
        assertThatThrownBy(()->service.state(5L,actor)).isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(()->service.update(5L,request(),actor)).isInstanceOf(AccessDeniedException.class);
        verifyNoInteractions(periods,audit);
    }
    AttitudeConfiguration configuration(long id,boolean complete) {
        var c=new AttitudeConfiguration();c.setId(id);c.setName("Configuration "+id);c.setStatus(AttitudeConfigurationStatus.PUBLISHED);
        var criterion=new AttitudeCriterion();criterion.setId(id*10);criterion.setActive(true);criterion.setCriterionType(AttitudeCriterionType.SHARED_CORE_VALUE);c.getCriteria().add(criterion);
        if(complete) {var m=new AttitudeRoleFormatMapping();m.setRole(role);m.setEvaluationFormat(AttitudeEvaluationFormat.OTHERS);c.getRoleMappings().add(m);}
        return c;
    }
    AttitudePeriodConfigurationDto context() {
        var dto=new AttitudePeriodConfigurationDto();dto.setReviewPeriodId(5L);dto.setReviewPeriodName(period.getName());dto.setReviewPeriodStatus(period.getStatus());
        var c=new AttitudeConfigurationDto();c.setId(period.getAttitudeConfiguration().getId());c.setName(period.getAttitudeConfiguration().getName());dto.setConfiguration(c);dto.setUnmappedRoleNames(List.of());return dto;
    }
    AttitudeAssignmentRequest request() {return new AttitudeAssignmentRequest(UUID.randomUUID(),1L,2L,"Cover all participating Job Roles");}
    @Test void readOnlyStateReportsCoverageWithoutBindingOrSaving() {
        var state=service.state(5L,actor);assertThat(state.canUpdate()).isTrue();
        assertThat(state.choices().get(0).missingRoleNames()).containsExactly("Engineer");
        verify(periods,never()).lockConfiguration();verify(periods,never()).saveAndFlush(any());verify(audit,never()).saveAndFlush(any());
    }
    @Test void correctionPinsPublishedTargetAndRecordsExactOldNewActorReasonAndBusinessTime() {
        var request=request();var state=service.update(5L,request,actor);
        assertThat(period.getAttitudeConfiguration()).isSameAs(target);assertThat(period.getUpdatedBy()).isEqualTo(actor);
        assertThat(state.period().getConfiguration().getId()).isEqualTo(2L);assertThat(history).hasSize(1);
        assertThat(state.blockers()).isEmpty();
        var saved=history.get(0);assertThat(saved.getPreviousConfiguration()).isSameAs(old);
        assertThat(saved.getActor()).isSameAs(admin);assertThat(saved.getRequestId()).isEqualTo(request.requestId());
        assertThat(saved.getReason()).isEqualTo(request.reason());assertThat(saved.getRecordedAt()).isEqualTo(OffsetDateTime.parse("2026-11-09T12:00:00+08:00"));
        var order=inOrder(periods,audit);order.verify(periods).lockConfiguration();order.verify(periods).saveAndFlush(period);order.verify(audit).saveAndFlush(any());
        verify(assessments,never()).save(any());verify(participants,never()).save(any());verify(editions,never()).save(any());
    }
    @ParameterizedTest @EnumSource(value=AnnualKpiReviewPeriodStatus.class,names={"DRAFT","UPCOMING","CLOSED"})
    void noNonOpenPeriodCanBeChanged(AnnualKpiReviewPeriodStatus status) {
        period.setStatus(status);assertBlocked("Only an Open");
    }
    @ParameterizedTest @EnumSource(AttitudeAssessmentStatus.class)
    void anySavedAssessmentBlocksCorrectionAndReportsReferencesWithoutAnswers(AttitudeAssessmentStatus status) {
        var a=new AttitudeAssessment();a.setId(21L);a.setParticipant(participant);a.setStatus(status);a.setConfiguration(old);
        when(assessments.findAllByReviewPeriodIdOrderByIdAsc(5L)).thenReturn(List.of(a));
        var error=catchThrowableOfType(()->service.update(5L,request(),actor),AttitudeAssignmentBlockedException.class);
        assertThat(error.getState().assessments()).containsExactly(new AttitudeAssignmentState.Assessment(21L,10L,"Fictional Employee","Published Engineer","IT",status,1L));
        assertThat(error.getState().canUpdate()).isFalse();assertThat(period.getAttitudeConfiguration()).isSameAs(old);assertThat(history).isEmpty();
    }
    @Test void coversFrozenRosterRolesEvenWhenNotPresentInCurrentRoleConfiguration() {
        var frozen=new Role();frozen.setId(8L);frozen.setName("Leaver Role");participant.setRole(frozen);participant.setRoleName("Frozen Leaver Role");
        assertThat(service.state(5L,actor).choices().get(1).missingRoleNames()).containsExactly("Frozen Leaver Role");assertBlocked("No different published");
    }
    @Test void publishedSelectionWithNoApplicableActiveCriteriaIsNotUsable() {
        target.getCriteria().get(0).setActive(false);assertBlocked("No different published");
    }
    @Test void draftsUnknownChoicesAndMissingFormatsCannotBeAssigned() {
        var draft=configuration(3L,true);draft.setStatus(AttitudeConfigurationStatus.DRAFT);
        when(editions.findAllByOrderByCreatedAtDescIdDesc()).thenReturn(List.of(old,target,draft));
        assertThat(service.state(5L,actor).choices()).hasSize(2);
        for(long id:List.of(3L,999L)) assertThatThrownBy(()->service.update(5L,new AttitudeAssignmentRequest(UUID.randomUUID(),1L,id,"Correction"),actor)).isInstanceOf(AttitudeAssignmentBlockedException.class);
        assertThat(history).isEmpty();
    }
    @Test void staleExpectedAssignmentAndSameTargetNeverCreateAnotherAudit() {
        assertThatThrownBy(()->service.update(5L,new AttitudeAssignmentRequest(UUID.randomUUID(),9L,2L,"Correction"),actor)).isInstanceOf(AttitudeAssignmentBlockedException.class);
        assertThatThrownBy(()->service.update(5L,new AttitudeAssignmentRequest(UUID.randomUUID(),1L,1L,"Correction"),actor)).isInstanceOf(AttitudeAssignmentBlockedException.class);
        assertThat(history).isEmpty();assertThat(period.getAttitudeConfiguration()).isSameAs(old);
    }
    @Test void successfulRequestReplaysWithoutWriteEvenAfterClosureButKeyReuseForOtherMeaningConflicts() {
        var request=request();service.update(5L,request,actor);period.setStatus(AnnualKpiReviewPeriodStatus.CLOSED);
        assertThat(service.update(5L,request,actor).replayed()).isTrue();assertThat(history).hasSize(1);
        assertThatThrownBy(()->service.update(5L,new AttitudeAssignmentRequest(request.requestId(),1L,2L,"Another reason"),actor)).isInstanceOf(AttitudeAssignmentBlockedException.class);
        verify(periods,times(1)).saveAndFlush(any());
    }
    @Test void revokedActiveAccountFailsBeforeReadOrReplay() {
        admin.setDeleted(true);assertThatThrownBy(()->service.state(5L,actor)).isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(()->service.update(5L,request(),actor)).isInstanceOf(AccessDeniedException.class);assertThat(history).isEmpty();
    }
    @Test void fixtureAndAppraisalReferencesFailClosedWithoutRewritingAnyDependency() {
        when(scopes.referencesReviewPeriod(5L)).thenReturn(true);assertBlocked("protected fictional AI fixture");
        when(scopes.referencesReviewPeriod(5L)).thenReturn(false);when(appraisals.existsByReviewPeriodId(5L)).thenReturn(true);assertBlocked("Recorded appraisals");
        verify(scopes,never()).save(any());verify(appraisals,never()).save(any());
    }
    @Test void missingRosterRoleAndUnboundPeriodsRequireManualReview() {
        period.setParticipantsSnapshottedAt(null);assertBlocked("published employee roster");
        period.setParticipantsSnapshottedAt(OffsetDateTime.now());participant.setRole(null);assertBlocked("published employee roster");
    }
    @Test void blankAndOversizeReasonsAreRejectedWithoutWrites() {
        for(var reason:List.of(" ","x".repeat(1001))) assertThatThrownBy(()->service.update(5L,new AttitudeAssignmentRequest(UUID.randomUUID(),1L,2L,reason),actor)).isInstanceOf(BadRequestException.class);
        assertThat(history).isEmpty();
    }
    void assertBlocked(String message) {
        var e=catchThrowableOfType(()->service.update(5L,request(),actor),AttitudeAssignmentBlockedException.class);
        assertThat(e.getState().blockers()).anyMatch(s->s.contains(message));assertThat(history).isEmpty();assertThat(period.getAttitudeConfiguration()).isSameAs(old);
    }
}
