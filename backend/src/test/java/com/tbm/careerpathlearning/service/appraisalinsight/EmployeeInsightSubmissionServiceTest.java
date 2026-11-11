package com.tbm.careerpathlearning.service.appraisalinsight;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.tbm.careerpathlearning.dto.*;
import com.tbm.careerpathlearning.enums.AppraisalStatus;
import com.tbm.careerpathlearning.enums.AnnualKpiReviewPeriodStatus;
import com.tbm.careerpathlearning.model.*;
import com.tbm.careerpathlearning.repository.*;
import com.tbm.careerpathlearning.service.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.security.access.AccessDeniedException;
import java.time.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class EmployeeInsightSubmissionServiceTest {
    final EmployeeInsightSettings settings=mock(EmployeeInsightSettings.class);
    final EmployeeInsightAccess access=mock(EmployeeInsightAccess.class);
    final EmployeeInsightEvidenceService evidence=mock(EmployeeInsightEvidenceService.class);
    final EmployeeInsightScopeRepository scopes=mock(EmployeeInsightScopeRepository.class);
    final EmployeeInsightGenerationRepository generations=mock(EmployeeInsightGenerationRepository.class);
    final EmployeeInsightSubmissionLinkRepository links=mock(EmployeeInsightSubmissionLinkRepository.class);
    final AnnualAppraisalRepository appraisals=mock(AnnualAppraisalRepository.class);
    final AnnualAppraisalActivityRepository activity=mock(AnnualAppraisalActivityRepository.class);
    final GeminiClient gemini=mock(GeminiClient.class);
    final Clock clock=Clock.fixed(Instant.parse("2026-11-06T01:00:00Z"),ZoneOffset.UTC);
    final UUID actor=UUID.randomUUID();
    final AnnualAppraisal a=new AnnualAppraisal();
    final ReviewPeriodParticipant p=new ReviewPeriodParticipant();
    final EmployeeInsightSubmissionService service=new EmployeeInsightSubmissionService(settings,access,evidence,scopes,generations,links,appraisals,activity,gemini,clock);
    @BeforeEach void setup() {
        when(settings.isEnabled()).thenReturn(true);p.setId(7L);a.setId(12L);a.setParticipant(p);a.setSuperiorId(actor);
        var period=new AnnualKpiReviewPeriod();period.setId(1L);period.setStatus(AnnualKpiReviewPeriodStatus.OPEN);p.setReviewPeriod(period);
        a.setStatus(AppraisalStatus.PENDING_REVIEW);
        when(access.superior(7L,actor,false)).thenReturn(p);
        var scope=new EmployeeInsightScope();scope.setEnabled(true);
        when(scopes.lockByParticipantId(7L)).thenReturn(Optional.of(scope));
        when(evidence.captureForSubmission(7L)).thenReturn(new EmployeeInsightEvidenceService.Captured(null,"current",JsonNodeFactory.instance.objectNode()));
        when(gemini.appraisalModel()).thenReturn("gemini-test");when(appraisals.findById(12L)).thenReturn(Optional.of(a));
    }
    EmployeeInsightGeneration generation(int version,String fingerprint) {
        var g=new EmployeeInsightGeneration();g.setId(UUID.randomUUID());g.setParticipantId(7L);g.setOwnerId(actor);
        g.setStatus("COMPLETED");g.setInsightVersion(version);g.setFingerprint(fingerprint);g.setModel("gemini-test");
        g.setTemplateVersion(AppraisalInsightTemplate.VERSION);return g;
    }
    AnnualAppraisalActivity event(long id,String action) {
        var e=new AnnualAppraisalActivity();e.setId(id);e.setAppraisalId(12L);e.setAction(action);return e;
    }
    @ParameterizedTest @EnumSource(value=AnnualKpiReviewPeriodStatus.class,names={"OPEN","CLOSED"})
    void submissionLinksLatestMatchingVersionWithoutCallingProviderOrChangingSnapshot(AnnualKpiReviewPeriodStatus period) {
        p.getReviewPeriod().setStatus(period);
        var valid=generation(1,"current");var outdated=generation(2,"old");
        when(generations.findAllByParticipantIdOrderByCreatedAtDescIdDesc(7L)).thenReturn(List.of(outdated,valid));
        var original=a.getSnapshot();
        service.submitted(new KpiNotificationEvents.Appraisal(a,event(19,"SUBMITTED"),actor));
        var saved=org.mockito.ArgumentCaptor.forClass(EmployeeInsightSubmissionLink.class);
        verify(links).saveAndFlush(saved.capture());assertEquals(valid.getId(),saved.getValue().getGenerationId());
        assertEquals(19L,saved.getValue().getSubmissionActivityId());assertSame(original,a.getSnapshot());
        verify(gemini,never()).generateAppraisalJson(any());verifyNoInteractions(appraisals);
    }
    @ParameterizedTest @EnumSource(value=AnnualKpiReviewPeriodStatus.class,names={"OPEN","CLOSED"})
    void submissionAndResubmissionKeepTheirExactVersionsAfterApproval(AnnualKpiReviewPeriodStatus period) {
        p.getReviewPeriod().setStatus(period);
        var first=event(19,"SUBMITTED");var second=event(23,"RESUBMITTED");
        var original=generation(1,"current");var regenerated=generation(2,"current");
        when(generations.findAllByParticipantIdOrderByCreatedAtDescIdDesc(7L)).thenReturn(List.of(original));
        service.submitted(new KpiNotificationEvents.Appraisal(a,first,actor));
        a.setStatus(AppraisalStatus.RETURNED);
        when(generations.findAllByParticipantIdOrderByCreatedAtDescIdDesc(7L)).thenReturn(List.of(regenerated,original));
        a.setStatus(AppraisalStatus.PENDING_REVIEW);
        service.submitted(new KpiNotificationEvents.Appraisal(a,second,actor));
        var saved=org.mockito.ArgumentCaptor.forClass(EmployeeInsightSubmissionLink.class);
        verify(links,times(2)).saveAndFlush(saved.capture());var recorded=saved.getAllValues();
        assertEquals(19L,recorded.get(0).getSubmissionActivityId());assertEquals(original.getId(),recorded.get(0).getGenerationId());
        assertEquals(23L,recorded.get(1).getSubmissionActivityId());assertEquals(regenerated.getId(),recorded.get(1).getGenerationId());
        when(activity.findAllByAppraisalIdOrderByIdAsc(12L)).thenReturn(List.of(first,event(20,"RETURNED"),second));
        when(links.findById(19L)).thenReturn(Optional.of(recorded.get(0)));when(links.findById(23L)).thenReturn(Optional.of(recorded.get(1)));
        when(generations.findById(original.getId())).thenReturn(Optional.of(original));
        when(generations.findById(regenerated.getId())).thenReturn(Optional.of(regenerated));
        a.setStatus(AppraisalStatus.APPROVED);a.setFinalDecision(com.tbm.careerpathlearning.enums.AnnualAppraisalRecommendation.NO_RECOMMENDATION);
        when(gemini.appraisalModel()).thenReturn("gemini-next");
        assertEquals(original.getId(),service.get(12L,19L,UUID.randomUUID()).insight().id());
        assertEquals(regenerated.getId(),service.get(12L,null,UUID.randomUUID()).insight().id());
        assertEquals(AppraisalStatus.APPROVED,a.getStatus());
        assertEquals(com.tbm.careerpathlearning.enums.AnnualAppraisalRecommendation.NO_RECOMMENDATION,a.getFinalDecision());
        verify(access,times(2)).superior(7L,actor,false);verify(access,times(2)).submitted(eq(a),any());
        verify(gemini,never()).generateAppraisalJson(any());verify(appraisals,never()).save(any());verify(appraisals,never()).saveAndFlush(any());
    }
    @Test void absentFailedPendingAndOutdatedWorkNeverBlocksSubmission() {
        var failed=generation(1,"current");failed.setStatus("FAILED");
        var queued=generation(2,"current");queued.setStatus("QUEUED");
        var outdated=generation(3,"old");
        when(generations.findAllByParticipantIdOrderByCreatedAtDescIdDesc(7L)).thenReturn(List.of(failed,queued,outdated));
        assertDoesNotThrow(()->service.submitted(new KpiNotificationEvents.Appraisal(a,event(20,"RESUBMITTED"),actor)));
        verifyNoInteractions(links);
        when(evidence.captureForSubmission(7L)).thenThrow(new AccessDeniedException("Unverified fixture"));
        assertDoesNotThrow(()->service.submitted(new KpiNotificationEvents.Appraisal(a,event(21,"SUBMITTED"),actor)));
        verifyNoInteractions(links);
    }
    @Test void disabledAndRoutineActivityHaveNoAiSideEffects() {
        when(settings.isEnabled()).thenReturn(false);
        service.submitted(new KpiNotificationEvents.Appraisal(a,event(1,"SUBMITTED"),actor));
        when(settings.isEnabled()).thenReturn(true);
        for (var action:List.of("DRAFT_CREATED","DRAFT_SAVED","RETURNED","APPROVED","OVERRIDDEN"))
            service.submitted(new KpiNotificationEvents.Appraisal(a,event(2,action),actor));
        verifyNoInteractions(access,evidence,scopes,generations,links,gemini);
    }
    @Test void isolatedHistoricalReadFailureDoesNotBlockSubmission() {
        when(generations.findAllByParticipantIdOrderByCreatedAtDescIdDesc(7L)).thenReturn(List.of(generation(1,"current")));
        when(evidence.captureForSubmission(7L)).thenThrow(new IllegalStateException("Optional historical evidence unavailable"));
        assertDoesNotThrow(()->service.submitted(new KpiNotificationEvents.Appraisal(a,event(21,"SUBMITTED"),actor)));
        verifyNoInteractions(links);verify(gemini,never()).generateAppraisalJson(any());
    }
    @Test void hrGetsExactHistoricalSubmissionNotLatestRegenerationOrAnOlderFallback() {
        var first=event(19,"SUBMITTED");var second=event(23,"RESUBMITTED");
        when(activity.findAllByAppraisalIdOrderByIdAsc(12L)).thenReturn(List.of(first,event(20,"RETURNED"),second));
        var original=generation(1,"old");var link=new EmployeeInsightSubmissionLink();
        link.setAppraisalId(12L);link.setParticipantId(7L);link.setGenerationId(original.getId());
        when(links.findById(19L)).thenReturn(Optional.of(link));when(generations.findById(original.getId())).thenReturn(Optional.of(original));
        a.setStatus(AppraisalStatus.APPROVED);
        var historical=service.get(12L,19L,UUID.randomUUID());
        assertEquals(original.getId(),historical.insight().id());assertFalse(historical.insight().outdated());
        assertNull(service.get(12L,null,UUID.randomUUID()).insight());
        assertThrows(AccessDeniedException.class,()->service.get(12L,20L,UUID.randomUUID()));
        assertThrows(AccessDeniedException.class,()->service.get(12L,999L,UUID.randomUUID()));
        verify(evidence,never()).capture(any());verify(gemini,never()).generateAppraisalJson(any());
    }
}
