package com.tbm.careerpathlearning.service;

import com.tbm.careerpathlearning.aspect.MetadataOnlyLogging;
import com.tbm.careerpathlearning.dto.*;
import com.tbm.careerpathlearning.enums.*;
import com.tbm.careerpathlearning.exception.*;
import com.tbm.careerpathlearning.model.*;
import com.tbm.careerpathlearning.repository.*;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.*;
import java.util.*;

@Service @Transactional @MetadataOnlyLogging
@PreAuthorize("hasAuthority('CAN_MANAGE_ATTITUDE_CONFIGURATION')")
public class AttitudeAssignmentService {
    private final AnnualKpiReviewPeriodRepository periods;
    private final ReviewPeriodRoleConfigurationRepository roles;
    private final ReviewPeriodParticipantRepository participants;
    private final AttitudeAssessmentRepository assessments;
    private final AnnualAppraisalRepository appraisals;
    private final EmployeeInsightScopeRepository scopes;
    private final AttitudeConfigurationAssignmentActivityRepository activity;
    private final StaffRepository staff;
    private final AttitudeConfigurationService configurations;
    private final AttitudeConfigurationBinding binding;
    private final Clock clock;
    private final boolean reassignmentEnabled;

    public AttitudeAssignmentService(AnnualKpiReviewPeriodRepository periods,ReviewPeriodRoleConfigurationRepository roles,
            ReviewPeriodParticipantRepository participants,AttitudeAssessmentRepository assessments,AnnualAppraisalRepository appraisals,
            EmployeeInsightScopeRepository scopes,AttitudeConfigurationAssignmentActivityRepository activity,StaffRepository staff,
            AttitudeConfigurationService configurations,AttitudeConfigurationBinding binding,
            @Qualifier("annualKpiReviewClock") Clock clock,
            @Value("${attitude.configuration.reassignment-enabled:false}") boolean reassignmentEnabled) {
        this.periods=periods;this.roles=roles;this.participants=participants;this.assessments=assessments;
        this.appraisals=appraisals;this.scopes=scopes;this.activity=activity;this.staff=staff;
        this.configurations=configurations;this.binding=binding;this.clock=clock;this.reassignmentEnabled=reassignmentEnabled;
    }

    @Transactional(readOnly=true)
    public AttitudeAssignmentState state(Long id,UUID actor) {
        active(actor);requireEnabled();return inspect(requirePeriod(id),actor,List.of(),false);
    }

    public AttitudeAssignmentState update(Long id,AttitudeAssignmentRequest request,UUID actor) {
        var account=active(actor);requireEnabled();periods.lockConfiguration();var period=requirePeriod(id);
        if(request==null || request.requestId()==null || request.expectedConfigurationId()==null || request.configurationId()==null
                || request.reason()==null || request.reason().isBlank() || request.reason().length()>1000)
            throw new BadRequestException("Select a published configuration and enter a correction reason (up to 1000 characters)");
        String reason=request.reason().strip();
        var replay=activity.findByRequestId(request.requestId());
        if(replay.isPresent()) {
            var saved=replay.get();
            if(!Objects.equals(saved.getReviewPeriodId(),id) || !Objects.equals(saved.getActor().getId(),actor)
                    || !Objects.equals(saved.getPreviousConfiguration().getId(),request.expectedConfigurationId())
                    || !Objects.equals(saved.getConfiguration().getId(),request.configurationId()) || !saved.getReason().equals(reason))
                throw blocked(period,actor,"This request was already used for a different correction. Refresh and try again.");
            return inspect(period,actor,List.of(),true);
        }
        var state=inspect(period,actor,List.of(),false);
        if(!state.canUpdate()) throw new AttitudeAssignmentBlockedException(state);
        var previous=period.getAttitudeConfiguration();
        if(!Objects.equals(previous.getId(),request.expectedConfigurationId()))
            throw blocked(period,actor,"The assigned configuration has changed. Review the latest assignment before confirming again.");
        if(Objects.equals(previous.getId(),request.configurationId()))
            throw blocked(period,actor,"This configuration is already assigned. No correction was made.");
        var choice=state.choices().stream().filter(c->c.id().equals(request.configurationId())).findFirst()
                .orElseThrow(()->blocked(period,actor,"Select an available published configuration."));
        if(!choice.missingRoleNames().isEmpty())
            throw blocked(period,actor,"Attitude Evaluation formats are missing for: "+String.join(", ",choice.missingRoleNames()));
        var target=binding.published(choice.id());
        period.setAttitudeConfiguration(target);period.setUpdatedBy(actor);
        // The period FK and immutable audit entry commit or roll back together under the assessment/closure lock.
        periods.saveAndFlush(period);
        activity.saveAndFlush(new AttitudeConfigurationAssignmentActivity(request.requestId(),id,previous,target,account,OffsetDateTime.now(clock),reason));
        return inspect(period,actor,List.of(),false);
    }

    private void requireEnabled() {
        if(!reassignmentEnabled) throw new BadRequestException("Updating an existing Review Period's Attitude configuration is currently unavailable.");
    }
    private AttitudeAssignmentState inspect(AnnualKpiReviewPeriod period,UUID actor,List<String> extra,boolean replayed) {
        var blockers=new ArrayList<String>();var roster=participants.findAllByReviewPeriodId(period.getId());
        if(period.getStatus()!=AnnualKpiReviewPeriodStatus.OPEN) blockers.add("Only an Open Review Period can have its Attitude configuration updated.");
        if(period.getAttitudeConfiguration()==null) blockers.add("This period has no assigned configuration. Use the existing initial assignment action.");
        var required=new LinkedHashMap<Long,String>();
        roles.findAllByReviewPeriodIdOrderByIdAsc(period.getId()).forEach(r->required.put(r.getRole().getId(),r.getRole().getName()));
        roster.forEach(p->{if(p.getRole()!=null) required.putIfAbsent(p.getRole().getId(),p.getRoleName()==null?p.getRole().getName():p.getRoleName());});
        if(period.getParticipantsSnapshottedAt()==null || roster.isEmpty() || required.isEmpty() || roster.stream().anyMatch(p->p.getRole()==null))
            blockers.add("The published employee roster or participating Job Roles need administrator review.");
        var recorded=assessments.findAllByReviewPeriodIdOrderByIdAsc(period.getId());
        if(!recorded.isEmpty()) blockers.add("Attitude assessments have already started. Existing Draft, submitted and completed records must keep their original configuration.");
        if(appraisals.existsByReviewPeriodId(period.getId())) blockers.add("Recorded appraisals depend on this period. A separate historical correction review is required.");
        if(scopes.referencesReviewPeriod(period.getId())) blockers.add("A protected fictional AI fixture references this period. Its approved source configuration cannot be reassigned here.");
        var choices=binding.publishedOptions().stream().map(c->new AttitudeAssignmentState.Choice(c.id(),c.name(),c.publishedAt(),
                required.entrySet().stream().filter(r->!c.coveredRoleIds().contains(r.getKey())).map(Map.Entry::getValue).distinct().sorted().toList())).toList();
        boolean alternate=choices.stream().anyMatch(c->c.missingRoleNames().isEmpty() && (period.getAttitudeConfiguration()==null || !c.id().equals(period.getAttitudeConfiguration().getId())));
        boolean currentCovered=period.getAttitudeConfiguration()!=null && choices.stream().anyMatch(c->c.id().equals(period.getAttitudeConfiguration().getId()) && c.missingRoleNames().isEmpty());
        if(!alternate && !currentCovered)
            blockers.add("No different published configuration covers every participating Job Role. Prepare and publish a complete configuration first.");
        blockers.addAll(extra);
        var references=recorded.stream().map(a->{var p=a.getParticipant();return new AttitudeAssignmentState.Assessment(
                a.getId(),p.getId(),p.getStaffName()==null?"Name unavailable":p.getStaffName(),p.getRoleName(),p.getDepartmentName(),a.getStatus(),a.getConfiguration().getId());}).toList();
        var history=activity.findAllByReviewPeriodIdOrderByIdAsc(period.getId()).stream().map(a->new AttitudeAssignmentState.Activity(
                a.getId(),a.getPreviousConfiguration().getName(),a.getConfiguration().getName(),a.getActor().getName(),a.getRecordedAt(),a.getReason())).toList();
        return new AttitudeAssignmentState(configurations.period(period.getId(),actor),blockers.isEmpty() && alternate,List.copyOf(blockers),choices,references,history,replayed);
    }
    private AttitudeAssignmentBlockedException blocked(AnnualKpiReviewPeriod period,UUID actor,String message) {
        return new AttitudeAssignmentBlockedException(inspect(period,actor,List.of(message),false));
    }
    private AnnualKpiReviewPeriod requirePeriod(Long id) {return periods.findById(id).orElseThrow(()->new BadRequestException("Annual KPI Review Period not found"));}
    private Staff active(UUID actor) {
        var account=staff.findById(actor).orElseThrow(()->new AccessDeniedException("Active account required"));
        if(account.isDeleted() || account.getAccountStatus()!=StaffAccountStatus.ACTIVE) throw new AccessDeniedException("Active account required");
        return account;
    }
}
