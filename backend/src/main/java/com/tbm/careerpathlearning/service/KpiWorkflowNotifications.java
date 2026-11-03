package com.tbm.careerpathlearning.service;

import com.tbm.careerpathlearning.enums.*;
import com.tbm.careerpathlearning.exception.BadRequestException;
import com.tbm.careerpathlearning.model.*;
import com.tbm.careerpathlearning.repository.*;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;

import static com.tbm.careerpathlearning.enums.AuthorityName.*;
import static com.tbm.careerpathlearning.enums.NotificationType.*;

/** Resolves recipients from current permissions and scope; all messages exclude confidential content. */
@Service
@Transactional(propagation = Propagation.MANDATORY)
public class KpiWorkflowNotifications {
    private static final String MINE = "/my-performance/my-kpi-plan";
    private static final String ASSESSMENTS = "/my-performance/my-assessments";
    private static final String TEAM = "/team-performance/team-reviews";
    private static final String REVIEW = "/kpi-management/kpi-review";
    private static final String DEPARTMENT = "/kpi-management/department-kpis";
    private final NotificationService notifications;
    private final StaffRepository staff;
    private final RoleAuthorityRepository authorities;
    private final ReviewPeriodParticipantRepository participants;
    private final AnnualAppraisalRepository appraisals;
    private final AnnualAppraisalReadinessService readiness;
    private final PerformanceDepartmentResolver departments;

    public KpiWorkflowNotifications(NotificationService notifications, StaffRepository staff,
            RoleAuthorityRepository authorities, ReviewPeriodParticipantRepository participants,
            AnnualAppraisalRepository appraisals, AnnualAppraisalReadinessService readiness,
            PerformanceDepartmentResolver departments) {
        this.notifications=notifications; this.staff=staff; this.authorities=authorities;
        this.participants=participants; this.appraisals=appraisals; this.readiness=readiness; this.departments=departments;
    }

    @EventListener
    public void enrolled(KpiNotificationEvents.Enrolled event) {
        for (var p : event.participants()) {
            if (!eligible(p)) continue;
            send("period:"+p.getId(), p.getStaff(), event.actor(), KPI_REVIEW_PERIOD,
                    "Review schedule available", "Your review schedule for "+p.getReviewPeriod().getName()+" is available.",
                    periodLink(MINE,p.getReviewPeriod()) , ROLE_USER);
        }
    }

    @EventListener
    public void plan(KpiNotificationEvents.Plan event) {
        if (!Set.of("PUBLISHED","SUBMITTED","RETURNED","APPROVED","CONFIRMED").contains(event.action())) return;
        var plan=event.plan();
        String key="plan:"+plan.getId()+":"+event.eventId();
        if (plan.getLevel()==KpiLevel.COMPANY) {
            companyAvailable(plan,key,event.actor()); return;
        }
        boolean submitted="SUBMITTED".equals(event.action());
        String outcome=switch(event.action()) {case "RETURNED" -> "returned for revision"; case "CONFIRMED" -> "confirmed"; default -> "approved";};
        if (plan.getLevel()==KpiLevel.DEPARTMENT) {
            if (submitted) {
                for (var reviewer:staff.findAllActiveWithRole()) send(key,reviewer,event.actor(),KPI_PLAN,
                        "Department KPI approval needed", plan.getDepartment().getName()+"'s KPI plan needs approval.",
                        planLink(REVIEW,plan,"department"),CAN_APPROVE_DEPARTMENT_KPI);
            } else {
                for (var owner:departmentOwners(plan.getDepartment())) send(key,owner,event.actor(),KPI_PLAN,
                        "Department KPI plan updated", "Your Department KPI plan was "+outcome+".",
                        planLink(DEPARTMENT,plan,null),CAN_MANAGE_DEPARTMENT_KPI);
            }
        } else {
            var p=plan.getOwnerParticipant(); if (!eligible(p)) return;
            if (submitted) {
                var superior=currentSuperior(p);
                if (superior!=null && Objects.equals(superior.getId(),plan.getSubmittedToSuperiorId()))
                    send(key,superior,event.actor(),KPI_PLAN,"Individual KPI review needed",
                            p.getStaffName()+"'s Individual KPI plan needs review.",planLink(TEAM,plan,"reviews"),CAN_REVIEW_INDIVIDUAL_KPI);
            } else send(key,p.getStaff(),event.actor(),KPI_PLAN,"Individual KPI plan updated",
                    "Your Individual KPI plan was "+outcome+".",planLink(MINE,plan,null),ROLE_USER);
        }
    }

    @EventListener
    public void assistance(KpiNotificationEvents.Assistance event) {
        var request=event.request(); var p=request.getOwnerParticipant(); if (!eligible(p)) return;
        String key="assistance:"+request.getId()+":"+request.getStatus();
        if (request.getStatus()==KpiAssistanceStatus.REQUESTED) {
            for (var reviewer:staff.findAllActiveWithRole()) send(key,reviewer,event.actor(),KPI_ASSISTANCE,
                    "KPI assistance review needed", "A KPI assistance request needs review.",
                    periodLink(REVIEW,p.getReviewPeriod())+"&tab=assistance&assistanceId="+request.getId(),CAN_AUTHORIZE_INDIVIDUAL_KPI_ASSISTANCE);
        } else if (request.getStatus()==KpiAssistanceStatus.AUTHORIZED || request.getStatus()==KpiAssistanceStatus.REJECTED) {
            var superior=currentSuperior(p);
            if (superior!=null && Objects.equals(superior.getId(),request.getSuperior().getId()))
                send(key,superior,event.actor(),KPI_ASSISTANCE,"KPI assistance request updated",
                        "Your KPI assistance request was "+(request.getStatus()==KpiAssistanceStatus.AUTHORIZED?"authorised":"rejected")+".",
                        periodLink(TEAM,p.getReviewPeriod())+"&tab=assistance&assistanceId="+request.getId(),CAN_REVIEW_INDIVIDUAL_KPI);
        }
    }

    @EventListener
    public void revision(KpiNotificationEvents.Revision event) {
        var v=event.version(); var plan=v.getPlan();
        String key="revision:"+v.getId()+":"+v.getDecisionHistory().size();
        String organisation=periodLink(REVIEW,plan.getReviewPeriod())+"&tab=revisions&revisionId="+v.getId();
        String team=periodLink(TEAM,plan.getReviewPeriod())+"&tab=revisions&revisionId="+v.getId();
        if (v.getStatus()==KpiVersionStatus.EFFECTIVE) {
            if (v.getLevel()==KpiLevel.COMPANY) companyAvailable(plan,key,event.actor());
            else if (v.getLevel()==KpiLevel.DEPARTMENT) {
                for (var owner:departmentOwners(plan.getDepartment())) send(key,owner,event.actor(),KPI_REVISION,
                        "Revised KPI plan effective","Your Department's revised KPI plan is now effective.",planLink(DEPARTMENT,plan,null),CAN_MANAGE_DEPARTMENT_KPI);
            } else if (eligible(plan.getOwnerParticipant())) send(key,plan.getOwnerParticipant().getStaff(),event.actor(),KPI_REVISION,
                    "Revised KPI plan effective","Your revised Individual KPI plan is now effective.",planLink(MINE,plan,null),ROLE_USER);
            return;
        }
        if (v.getLevel()==KpiLevel.DEPARTMENT) {
            if (v.getStatus()==KpiVersionStatus.REQUESTED) {
                for (var reviewer:staff.findAllActiveWithRole()) if (!Objects.equals(reviewer.getId(),v.getRequestedBy()))
                    revisionAction(key,reviewer,event.actor(),organisation,CAN_PREPARE_DEPARTMENT_KPI_REVISION,"A Department KPI revision needs preparation.");
            } else if (v.getStatus()==KpiVersionStatus.PENDING_MD_APPROVAL) {
                for (var reviewer:staff.findAllActiveWithRole()) if (!Objects.equals(reviewer.getId(),v.getPreparedBy()) && !Objects.equals(reviewer.getId(),v.getRequestedBy()))
                    revisionAction(key,reviewer,event.actor(),organisation,CAN_APPROVE_DEPARTMENT_KPI,"A Department KPI revision needs approval.");
            } else if (v.getStatus()==KpiVersionStatus.PENDING_HOD_CONFIRMATION) {
                var owner=account(v.getRequestedBy());
                if (inDepartment(owner,plan.getDepartment())) revisionAction(key,owner,event.actor(),organisation,CAN_MANAGE_DEPARTMENT_KPI,
                        "Your Department KPI revision needs final confirmation.");
            } else if (v.getStatus()==KpiVersionStatus.RETURNED) revisionAction(key,account(v.getPreparedBy()),event.actor(),organisation,
                    CAN_PREPARE_DEPARTMENT_KPI_REVISION,"A Department KPI revision was returned to you.");
        } else if (v.getLevel()==KpiLevel.INDIVIDUAL && eligible(plan.getOwnerParticipant())) {
            var superior=currentSuperior(plan.getOwnerParticipant());
            if (superior==null || !Objects.equals(superior.getId(),v.getRevisionSuperiorId())) return;
            if ((v.getStatus()==KpiVersionStatus.REQUESTED && v.getPreparationMode()==KpiRevisionPreparationMode.SUPERIOR)
                    || v.getStatus()==KpiVersionStatus.PENDING_APPROVAL)
                revisionAction(key,superior,event.actor(),team,CAN_REVIEW_INDIVIDUAL_KPI,"An Individual KPI revision needs your attention.");
            else if (v.getStatus()==KpiVersionStatus.RETURNED)
                revisionAction(key,account(v.getPreparedBy()),event.actor(),organisation,CAN_MANAGE_INDIVIDUAL_KPI_REVISIONS,"An Individual KPI revision was returned to you.");
        }
    }

    @EventListener
    public void kpiReview(KpiNotificationEvents.KpiReview event) {
        var a=event.assessment(); var p=a.getParticipant(); if (!eligible(p)) return;
        if (a.getStatus()==KpiAssessmentStatus.PENDING_REVIEW) {
            var superior=currentSuperior(p);
            if (superior!=null && Objects.equals(superior.getId(),a.getSubmittedToSuperiorId()))
                send("kpi-submitted:"+a.getId(),superior,event.actor(),KPI_ASSESSMENT,"KPI Self-Assessment submitted",
                        p.getStaffName()+"'s KPI Self-Assessment for "+checkpointLabel(a)+" needs review.",periodLink(TEAM,p.getReviewPeriod())+"&tab=assessments&assessmentId="+a.getId(),CAN_REVIEW_KPI_ASSESSMENT);
        } else if (a.getStatus()==KpiAssessmentStatus.REVIEWED) {
            // A central-model completed score already produces the combined outcome in score().
            if (a.getAssessmentModel()!=KpiAssessmentModel.CENTRAL_RESULTS || a.getCheckpointScore()==null)
                send("kpi-reviewed:"+a.getId(),p.getStaff(),event.actor(),KPI_ASSESSMENT,"Individual KPI review complete",
                        "Your Individual KPI review for "+checkpointLabel(a)+" is complete.",assessmentLink(a),ROLE_USER);
            notifyReady(p,event.actor());
        }
    }

    @EventListener
    public void attitudeReview(KpiNotificationEvents.AttitudeReview event) {
        var a=event.assessment(); var p=a.getParticipant(); if (!eligible(p)) return;
        String link=periodLink(a.getStatus()==AttitudeAssessmentStatus.PENDING_REVIEW?TEAM:ASSESSMENTS,p.getReviewPeriod())
                +"&tab=attitude&attitudeAssessmentId="+a.getId();
        if (a.getStatus()==AttitudeAssessmentStatus.PENDING_REVIEW) {
            var superior=currentSuperior(p);
            if (superior!=null && Objects.equals(superior.getId(),a.getSubmittedToSuperiorId()))
                send("attitude-submitted:"+a.getId(),superior,event.actor(),ATTITUDE_ASSESSMENT,"Attitude Self-Assessment submitted",
                        p.getStaffName()+"'s annual Attitude Self-Assessment needs review.",link,CAN_REVIEW_ATTITUDE_EVALUATION);
        } else if (a.getStatus()==AttitudeAssessmentStatus.REVIEWED) {
            send("attitude-reviewed:"+a.getId(),p.getStaff(),event.actor(),ATTITUDE_ASSESSMENT,"Attitude Evaluation complete",
                    "Your annual Attitude Evaluation results are available.",link,ROLE_USER);
            notifyReady(p,event.actor());
        }
    }

    @EventListener
    public void score(KpiNotificationEvents.Score event) {
        var a=event.assessment(); var p=a.getParticipant(); if (!eligible(p)) return;
        if (a.getCheckpointScore()==null || (event.previousScore()!=null && event.previousScore().compareTo(a.getCheckpointScore())==0)) return;
        String message=event.reviewCompletion()?"Your Individual KPI review is complete and your Checkpoint KPI Score is available."
                :event.previousScore()==null?"Your Checkpoint KPI Score is available.":"Your Checkpoint KPI Score has been updated.";
        send("checkpoint-score:"+a.getId()+":"+event.calculation(),p.getStaff(),event.actor(),KPI_CHECKPOINT_SCORE,
                "Checkpoint KPI results available",message+" Review checkpoint: "+checkpointLabel(a)+".",assessmentLink(a)+"&showBreakdown=true",ROLE_USER);
        notifyReady(p,event.actor());
    }

    @EventListener
    public void appraisal(KpiNotificationEvents.Appraisal event) {
        var a=event.appraisal(); var p=a.getParticipant(); var action=event.activity().getAction();
        String key="appraisal:"+a.getId()+":"+event.activity().getId();
        if ("SUBMITTED".equals(action) || "RESUBMITTED".equals(action)) {
            if (!eligible(p) || currentSuperior(p)==null || !Objects.equals(currentSuperior(p).getId(),a.getSuperiorId())) return;
            for (var reviewer:staff.findAllActiveWithRole()) if (!Objects.equals(reviewer.getId(),a.getEmployeeId()) && !Objects.equals(reviewer.getId(),a.getSuperiorId()))
                send(key,reviewer,event.actor(),ANNUAL_APPRAISAL,"Appraisal review needed","An appraisal recommendation needs HR review.",
                        "/appraisals/review/"+a.getId(),CAN_REVIEW_ANNUAL_APPRAISAL);
        } else if (Set.of("RETURNED","APPROVED","OVERRIDDEN").contains(action)) {
            var superior=account(a.getSuperiorId());
            boolean current=currentSuperior(p)!=null && Objects.equals(currentSuperior(p).getId(),a.getSuperiorId());
            if (current)
                send(key,superior,event.actor(),ANNUAL_APPRAISAL,"Appraisal review updated",
                        "RETURNED".equals(action)?"An appraisal was returned for revision.":"HR has finalised an appraisal.",
                        appraisalLink(p),CAN_SUBMIT_ANNUAL_APPRAISAL);
        }
    }

    private void companyAvailable(KpiPlan plan,String key,UUID actor) {
        var roster=participants.findAllByReviewPeriodId(plan.getReviewPeriod().getId());
        for (var p:roster) if (eligible(p) && permitted(p.getStaff(),CAN_MANAGE_DEPARTMENT_KPI) && inDepartment(p.getStaff(),p.getDepartment()))
            send(key,p.getStaff(),actor,KPI_PLAN,"Company KPI plan available","The Company KPI plan for "+plan.getReviewPeriod().getName()+" is available.",
                    planLink(MINE,plan,null),ROLE_USER);
    }

    private void notifyReady(ReviewPeriodParticipant p,UUID actor) {
        if (!eligible(p) || p.getReviewPeriod().getStatus()!=AnnualKpiReviewPeriodStatus.OPEN) return;
        var superior=currentSuperior(p);
        if (!permitted(superior,CAN_SUBMIT_ANNUAL_APPRAISAL)) return;
        var appraisal=appraisals.findByParticipantId(p.getId()).orElse(null);
        if (appraisal!=null && appraisal.getStatus()!=AppraisalStatus.DRAFT && appraisal.getStatus()!=AppraisalStatus.RETURNED) return;
        if (readiness.readyForNotification(p)) send("appraisal-ready:"+p.getId(),superior,actor,ANNUAL_APPRAISAL,
                "Results ready for appraisal",p.getStaffName()+"'s results are ready for appraisal.",appraisalLink(p),CAN_SUBMIT_ANNUAL_APPRAISAL);
    }

    private void revisionAction(String key,Staff recipient,UUID actor,String link,AuthorityName authority,String body) {
        send(key,recipient,actor,KPI_REVISION,"KPI revision action needed",body,link,authority);
    }
    List<Staff> departmentOwners(OrgChart department) {
        return staff.findAllActiveWithRole().stream().filter(s->permitted(s,CAN_MANAGE_DEPARTMENT_KPI) && inDepartment(s,department)).toList();
    }
    boolean inDepartment(Staff s,OrgChart department) {
        if (!businessAccount(s) || department==null || department.isDeleted()) return false;
        try { var resolved=departments.resolve(s.getRole()); return resolved!=null && Objects.equals(resolved.getId(),department.getId()); }
        catch (BadRequestException ignored) { return false; }
    }
    Staff currentSuperior(ReviewPeriodParticipant p) {
        if (!eligible(p)) return null;
        var manager=p.getStaff().getManager();
        return active(manager) && !Objects.equals(manager.getId(),p.getStaff().getId())?manager:null;
    }
    boolean eligible(ReviewPeriodParticipant p) { return p!=null && businessAccount(p.getStaff()); }
    private boolean businessAccount(Staff s) { return active(s) && s.getRole().isPerformanceReviewEligible(); }
    private boolean active(Staff s) { return s!=null && s.getId()!=null && !s.isDeleted() && s.getAccountStatus()==StaffAccountStatus.ACTIVE
            && s.getRole()!=null && !s.getRole().isDeleted(); }
    Staff account(UUID id) { return id==null?null:staff.findById(id).orElse(null); }
    boolean permitted(Staff s,AuthorityName authority) {
        return active(s) && authorities.existsByRoleIdAndAuthorityName(s.getRole().getId(),authority);
    }
    private void send(String key,Staff recipient,UUID actor,NotificationType type,String title,String body,String link,AuthorityName authority) {
        if (recipient==null || Objects.equals(recipient.getId(),actor) || !permitted(recipient,ROLE_USER) || !permitted(recipient,authority)) return;
        notifications.notifyInAppOnce(key+":"+recipient.getId(),recipient.getId(),type,title,body,link);
    }
    List<Staff> recipients(AuthorityName authority) {
        return staff.findAllActiveWithRole().stream().filter(s->permitted(s,ROLE_USER) && permitted(s,authority)).toList();
    }
    void scheduled(String key,Staff recipient,NotificationType type,String title,String body,String link,AuthorityName authority) {
        send(key,recipient,null,type,title,body,link,authority);
    }
    private String periodLink(String path,AnnualKpiReviewPeriod period) { return path+"?reviewPeriodId="+period.getId(); }
    private String planLink(String path,KpiPlan plan,String tab) {
        return periodLink(path,plan.getReviewPeriod())+(tab==null?"":"&tab="+tab)+"&planId="+plan.getId();
    }
    private String assessmentLink(KpiAssessment a) {
        return periodLink(ASSESSMENTS,a.getParticipant().getReviewPeriod())+"&tab=kpi&checkpointId="+a.getCheckpoint().getId()+"&assessmentId="+a.getId();
    }
    private String appraisalLink(ReviewPeriodParticipant p) { return "/team-performance/team-appraisals/"+p.getReviewPeriod().getId()+"/"+p.getId(); }
    private String checkpointLabel(KpiAssessment a) {
        return a.getCheckpoint().getStartDate()+" to "+a.getCheckpoint().getEndDate()+" ("+a.getParticipant().getReviewPeriod().getName()+")";
    }
}
