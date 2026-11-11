package com.tbm.careerpathlearning.service.appraisalinsight;

import com.tbm.careerpathlearning.aspect.*;
import com.tbm.careerpathlearning.dto.EmployeeInsightDto.Submitted;
import com.tbm.careerpathlearning.enums.AppraisalStatus;
import com.tbm.careerpathlearning.exception.BadRequestException;
import com.tbm.careerpathlearning.model.*;
import com.tbm.careerpathlearning.repository.*;
import com.tbm.careerpathlearning.service.GeminiClient;
import com.tbm.careerpathlearning.service.KpiNotificationEvents;
import org.springframework.context.event.EventListener;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.*;
import java.util.*;

/** Captures a link in the existing submission transaction; never calls Gemini or changes its snapshot. */
@Service @MetadataOnlyLogging
public class EmployeeInsightSubmissionService {
    private final EmployeeInsightSettings settings;
    private final EmployeeInsightAccess access;
    private final EmployeeInsightEvidenceService evidence;
    private final EmployeeInsightScopeRepository scopes;
    private final EmployeeInsightGenerationRepository generations;
    private final EmployeeInsightSubmissionLinkRepository links;
    private final AnnualAppraisalRepository appraisals;
    private final AnnualAppraisalActivityRepository activity;
    private final GeminiClient gemini;
    private final Clock clock;

    public EmployeeInsightSubmissionService(EmployeeInsightSettings settings,EmployeeInsightAccess access,
            EmployeeInsightEvidenceService evidence,EmployeeInsightScopeRepository scopes,
            EmployeeInsightGenerationRepository generations,EmployeeInsightSubmissionLinkRepository links,
            AnnualAppraisalRepository appraisals,AnnualAppraisalActivityRepository activity,GeminiClient gemini,Clock clock) {
        this.settings=settings;this.access=access;this.evidence=evidence;this.scopes=scopes;this.generations=generations;
        this.links=links;this.appraisals=appraisals;this.activity=activity;this.gemini=gemini;this.clock=clock;
    }

    @EventListener
    @Transactional(propagation=org.springframework.transaction.annotation.Propagation.MANDATORY)
    public void submitted(KpiNotificationEvents.Appraisal event) {
        if (!settings.isEnabled() || !Set.of("SUBMITTED","RESUBMITTED").contains(event.activity().getAction())) return;
        try (var ignored=MetadataOnlyLogScope.enter()) {
            var a=event.appraisal();
            if (!Objects.equals(a.getSuperiorId(),event.actor())) return;
            var scope=scopes.lockByParticipantId(a.getParticipant().getId()).orElse(null);
            if (scope==null || !scope.isEnabled()) return;
            var candidates=generations.findAllByParticipantIdOrderByCreatedAtDescIdDesc(a.getParticipant().getId()).stream()
                    .filter(g->"COMPLETED".equals(g.getStatus()) && Objects.equals(g.getOwnerId(),event.actor())
                        && Objects.equals(gemini.appraisalModel(),g.getModel())
                        && AppraisalInsightTemplate.VERSION.equals(g.getTemplateVersion())).toList();
            if (candidates.isEmpty()) return;
            // Absent/untrusted/incomplete AI evidence is optional, never an appraisal blocker.
            EmployeeInsightEvidenceService.Captured captured;
            try {
                access.superior(a.getParticipant().getId(),event.actor(),false);
                captured=evidence.captureForSubmission(a.getParticipant().getId());
            } catch (RuntimeException unavailable) { return; }
            var found=candidates.stream().filter(g->captured.fingerprint().equals(g.getFingerprint()))
                    .max(Comparator.comparing(EmployeeInsightGeneration::getInsightVersion)).orElse(null);
            if (found==null) return;
            var link=new EmployeeInsightSubmissionLink(); link.setSubmissionActivityId(event.activity().getId());
            link.setAppraisalId(a.getId());link.setParticipantId(a.getParticipant().getId());
            link.setGenerationId(found.getId());link.setCreatedAt(OffsetDateTime.now(clock)); links.saveAndFlush(link);
        }
    }

    @Transactional(readOnly=true)
    public Submitted get(Long appraisalId,Long submissionActivityId,UUID actor) {
        try (var ignored=MetadataOnlyLogScope.enter()) {
            var a=appraisals.findById(appraisalId).orElseThrow(EmployeeInsightSubmissionService::denied);
            access.submitted(a,actor);
            if (a.getStatus()==AppraisalStatus.DRAFT) throw denied();
            var events=activity.findAllByAppraisalIdOrderByIdAsc(appraisalId).stream()
                    .filter(e->Set.of("SUBMITTED","RESUBMITTED").contains(e.getAction())).toList();
            var selected=submissionActivityId==null?events.stream().max(Comparator.comparing(AnnualAppraisalActivity::getId)).orElse(null)
                    :events.stream().filter(e->Objects.equals(e.getId(),submissionActivityId)).findFirst().orElseThrow(EmployeeInsightSubmissionService::denied);
            if (selected==null || !settings.isEnabled()) return new Submitted(appraisalId,selected==null?null:selected.getId(),null);
            evidence.verifyScope(a.getParticipant());
            var link=links.findById(selected.getId()).orElse(null);
            if (link==null) return new Submitted(appraisalId,selected.getId(),null);
            var g=generations.findById(link.getGenerationId()).orElseThrow(EmployeeInsightSubmissionService::denied);
            if (!Objects.equals(link.getAppraisalId(),appraisalId) || !Objects.equals(g.getParticipantId(),a.getParticipant().getId())) throw denied();
            // Historical content remains exact even after corrections or model/template changes.
            return new Submitted(appraisalId,selected.getId(),EmployeeAppraisalInsightService.dto(g,false));
        }
    }
    private static AccessDeniedException denied() { return new AccessDeniedException("Submitted AI insight is unavailable"); }
}
