package com.tbm.careerpathlearning.service;

import com.tbm.careerpathlearning.enums.*;
import com.tbm.careerpathlearning.model.*;
import com.tbm.careerpathlearning.repository.*;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;

import static com.tbm.careerpathlearning.enums.AuthorityName.*;
import static com.tbm.careerpathlearning.enums.NotificationType.*;

/** Reads unfinished tasks and persists only notifications; it never runs transitions or score refreshes. */
@Service
public class KpiNotificationScheduleService {
    private static final DateTimeFormatter DATE=DateTimeFormatter.ofPattern("d MMM uuuu",Locale.ENGLISH);
    private static final String SELF="/my-performance/my-assessments", TEAM="/team-performance/team-reviews";
    private final AnnualKpiReviewPeriodRepository periods;
    private final ReviewPeriodParticipantRepository participants;
    private final ReviewCheckpointRepository checkpoints;
    private final KpiAssessmentRepository assessments;
    private final AttitudeAssessmentRepository attitudes;
    private final AnnualAppraisalRepository appraisals;
    private final KpiPlanRepository plans;
    private final KpiPlanVersionRepository versions;
    private final KpiCentralResultRepository results;
    private final CompanyKpiResultDelegateRepository delegates;
    private final IndividualKpiAssistanceAuthorizationRepository assistance;
    private final KpiAssessmentService kpiAvailability;
    private final AnnualAppraisalReadinessService readiness;
    private final KpiWorkflowNotifications notify;
    private final Clock clock;

    public KpiNotificationScheduleService(AnnualKpiReviewPeriodRepository periods,ReviewPeriodParticipantRepository participants,
            ReviewCheckpointRepository checkpoints,KpiAssessmentRepository assessments,AttitudeAssessmentRepository attitudes,
            AnnualAppraisalRepository appraisals,KpiPlanRepository plans,KpiPlanVersionRepository versions,
            KpiCentralResultRepository results,CompanyKpiResultDelegateRepository delegates,
            IndividualKpiAssistanceAuthorizationRepository assistance,KpiAssessmentService kpiAvailability,
            AnnualAppraisalReadinessService readiness,
            KpiWorkflowNotifications notify,@Qualifier("annualKpiReviewClock") Clock clock) {
        this.periods=periods;this.participants=participants;this.checkpoints=checkpoints;this.assessments=assessments;
        this.attitudes=attitudes;this.appraisals=appraisals;this.plans=plans;this.versions=versions;this.results=results;
        this.delegates=delegates;this.assistance=assistance;this.kpiAvailability=kpiAvailability;
        this.readiness=readiness;this.notify=notify;this.clock=clock;
    }

    @Transactional
    public void scanToday() {
        LocalDate today=LocalDate.now(clock);
        var groups=new LinkedHashMap<Group,Reminder>();
        for(var status:List.of(AnnualKpiReviewPeriodStatus.UPCOMING,AnnualKpiReviewPeriodStatus.OPEN)) {
            for(var period:periods.findAllByStatus(status)) {
                if(period.getStatus()!=status || period.getParticipantsSnapshottedAt()==null) continue;
                var roster=participants.findAllByReviewPeriodId(period.getId()).stream().filter(notify::eligible).toList();
                var periodPlans=plans.findAllByReviewPeriodId(period.getId());
                setupReminders(period,roster,periodPlans,today,groups);
                if(status!=AnnualKpiReviewPeriodStatus.OPEN) continue;
                var schedule=checkpoints.findAllByReviewPeriodIdOrderByReviewFrequencyAscSequenceNumberAsc(period.getId());
                for(var p:roster) employeeTasks(p,schedule,today,groups);
                centralTasks(period,schedule,today);
            }
        }
        groups.forEach((group,reminder)->notify.scheduled("reminder:"+group.period()+":"+group.task()+":"+group.deadline()+":"+group.phase(),
                reminder.recipient(),PERFORMANCE_REMINDER,reminder.title(),reminder.body(),reminder.link(),reminder.permission()));
    }

    private void employeeTasks(ReviewPeriodParticipant p,List<ReviewCheckpoint> schedule,LocalDate today,Map<Group,Reminder> groups) {
        var saved=assessments.findAllByParticipantId(p.getId());
        for(var c:schedule) {
            if(c.getReviewFrequency()!=p.getReviewFrequency()) continue;
            var a=saved.stream().filter(x->Objects.equals(x.getCheckpoint().getId(),c.getId())).findFirst().orElse(null);
            String link=periodLink(SELF,p.getReviewPeriod())+"&tab=kpi&checkpointId="+c.getId();
            if(a==null || a.getStatus()==KpiAssessmentStatus.DRAFT) {
                if(kpiAvailability.actionableSelfAssessmentForNotification(p,c,a))
                    employeeNotice(p,"kpi:"+c.getId(),"KPI Self-Assessment (checkpoint ending "+DATE.format(c.getEndDate())+")",link,c.getSelfAssessmentDeadline(),
                            openingDate(p,c.getEndDate().plusDays(1)),today);
            } else if(a.getStatus()==KpiAssessmentStatus.PENDING_REVIEW) {
                var superior=notify.currentSuperior(p);
                if(superior!=null && Objects.equals(superior.getId(),a.getSubmittedToSuperiorId()) && !a.getItems().isEmpty())
                    grouped(groups,p.getReviewPeriod(),"KPI_REVIEW",superior,CAN_REVIEW_KPI_ASSESSMENT,c.getSuperiorAssessmentDeadline(),today,
                            "KPI reviews",periodLink(TEAM,p.getReviewPeriod())+"&tab=assessments");
            }
        }
        var attitude=attitudes.findByParticipantId(p.getId()).orElse(null);
        if(attitude==null || attitude.getStatus()==AttitudeAssessmentStatus.DRAFT) {
            if(AttitudeAssessmentAvailability.actionable(p,attitude,today))
                employeeNotice(p,"attitude","annual Attitude Self-Assessment",periodLink(SELF,p.getReviewPeriod())+"&tab=attitude",
                        p.getReviewPeriod().getAttitudeSelfAssessmentDeadline(),openingDate(p,p.getReviewPeriod().getAttitudeSelfAssessmentStartDate()),today);
        } else if(attitude.getStatus()==AttitudeAssessmentStatus.PENDING_REVIEW) {
            var superior=notify.currentSuperior(p);
            if(superior!=null && Objects.equals(superior.getId(),attitude.getSubmittedToSuperiorId()) && !attitude.getItems().isEmpty())
                grouped(groups,p.getReviewPeriod(),"ATTITUDE_REVIEW",superior,CAN_REVIEW_ATTITUDE_EVALUATION,
                        p.getReviewPeriod().getSuperiorAttitudeEvaluationDeadline(),today,"Attitude Evaluations",periodLink(TEAM,p.getReviewPeriod())+"&tab=attitude");
        }
        appraisalReminders(p,today,groups);
    }

    private void employeeNotice(ReviewPeriodParticipant p,String task,String label,String link,LocalDate deadline,LocalDate opening,LocalDate today) {
        String phase=phase(deadline,today);
        if(phase!=null) notify.scheduled("reminder:"+p.getId()+":"+task+":"+deadline+":"+phase,p.getStaff(),PERFORMANCE_REMINDER,
                "Self-Assessment reminder","Your "+label+" for "+p.getReviewPeriod().getName()+". "+dueText(deadline,phase),link,ROLE_USER);
        else if(today.equals(opening)) notify.scheduled("available:"+p.getId()+":"+task,p.getStaff(),KPI_TASK_AVAILABLE,
                "Self-Assessment available","Your "+label+" for "+p.getReviewPeriod().getName()+" is available. Deadline: "+DATE.format(deadline)+".",link,ROLE_USER);
    }

    private void appraisalReminders(ReviewPeriodParticipant p,LocalDate today,Map<Group,Reminder> groups) {
        var a=appraisals.findByParticipantId(p.getId()).orElse(null);
        var superior=notify.currentSuperior(p); if(superior==null) return;
        var period=p.getReviewPeriod();
        if(a==null || a.getStatus()==AppraisalStatus.DRAFT || a.getStatus()==AppraisalStatus.RETURNED) {
            if(a!=null && !Objects.equals(superior.getId(),a.getSuperiorId())) return;
            if(phase(period.getAppraisalRecommendationDeadline(),today)!=null && readiness.readyForNotification(p))
                grouped(groups,period,"APPRAISAL_SUBMISSION",superior,CAN_SUBMIT_ANNUAL_APPRAISAL,period.getAppraisalRecommendationDeadline(),today,
                        "appraisal recommendations",periodLink("/team-performance/team-appraisals",period));
        } else if(a.getStatus()==AppraisalStatus.PENDING_REVIEW && Objects.equals(a.getSuperiorId(),superior.getId())) {
            for(var hr:notify.recipients(CAN_REVIEW_ANNUAL_APPRAISAL)) {
                if(Objects.equals(hr.getId(),a.getEmployeeId()) || Objects.equals(hr.getId(),a.getSuperiorId())) continue;
                grouped(groups,period,"HR_APPRAISAL_REVIEW",hr,CAN_REVIEW_ANNUAL_APPRAISAL,period.getHrFinalisationDeadline(),today,
                        "HR appraisal reviews",periodLink("/appraisals/review",period));
            }
        }
    }

    private void setupReminders(AnnualKpiReviewPeriod period,List<ReviewPeriodParticipant> roster,List<KpiPlan> periodPlans,
                                LocalDate today,Map<Group,Reminder> groups) {
        var deadline=period.getKpiSetupDeadline(); if(phase(deadline,today)==null) return;
        if(periodPlans.stream().noneMatch(p->p.getLevel()==KpiLevel.COMPANY && p.getStatus()==KpiPlanStatus.PUBLISHED))
            for(var owner:notify.recipients(CAN_MANAGE_COMPANY_KPI)) grouped(groups,period,"COMPANY_PLAN",owner,CAN_MANAGE_COMPANY_KPI,deadline,today,
                    "Company KPI plans",periodLink("/kpi-management/company-kpis",period));
        var departmentIds=new HashSet<Long>();
        for(var p:roster) {
            if(p.getDepartment()!=null && departmentIds.add(p.getDepartment().getId())) {
                var plan=periodPlans.stream().filter(x->x.getLevel()==KpiLevel.DEPARTMENT && x.getDepartment()!=null && Objects.equals(x.getDepartment().getId(),p.getDepartment().getId())).findFirst().orElse(null);
                if(plan!=null && plan.getStatus()==KpiPlanStatus.PENDING_APPROVAL) {
                    for(var reviewer:notify.recipients(CAN_APPROVE_DEPARTMENT_KPI)) grouped(groups,period,"DEPARTMENT_APPROVAL",reviewer,CAN_APPROVE_DEPARTMENT_KPI,deadline,today,
                            "Department KPI plan approvals",periodLink("/kpi-management/kpi-review",period)+"&tab=department");
                } else if(plan==null || plan.getStatus()!=KpiPlanStatus.APPROVED)
                    for(var owner:notify.departmentOwners(p.getDepartment())) grouped(groups,period,"DEPARTMENT_PLAN",owner,CAN_MANAGE_DEPARTMENT_KPI,deadline,today,
                            "Department KPI plans",periodLink("/kpi-management/department-kpis",period));
            }
            var plan=periodPlans.stream().filter(x->x.getLevel()==KpiLevel.INDIVIDUAL && Objects.equals(x.getOwnerParticipantId(),p.getId())).findFirst().orElse(null);
            if(plan!=null && plan.getStatus()==KpiPlanStatus.APPROVED) continue;
            var superior=notify.currentSuperior(p);
            if(plan!=null && plan.getStatus()==KpiPlanStatus.PENDING_APPROVAL) {
                if(superior!=null && Objects.equals(superior.getId(),plan.getSubmittedToSuperiorId()))
                    grouped(groups,period,"INDIVIDUAL_PLAN_REVIEW",superior,CAN_REVIEW_INDIVIDUAL_KPI,deadline,today,
                            "Individual KPI plan reviews",periodLink(TEAM,period)+"&tab=reviews");
            } else if(plan!=null && plan.getAssistanceAuthorizationId()!=null) {
                var authorisation=assistance.findById(plan.getAssistanceAuthorizationId()).orElse(null);
                if(authorisation!=null && authorisation.getStatus()==KpiAssistanceStatus.AUTHORIZED && superior!=null
                        && Objects.equals(authorisation.getSuperior().getId(),superior.getId()))
                    grouped(groups,period,"ASSISTED_PLAN",superior,CAN_REVIEW_INDIVIDUAL_KPI,deadline,today,
                            "assisted Individual KPI plans",periodLink(TEAM,period)+"&tab=assistance");
            } else notify.scheduled("reminder:"+p.getId()+":individual-plan:"+deadline+":"+phase(deadline,today),p.getStaff(),PERFORMANCE_REMINDER,
                    "Individual KPI plan reminder","Your Individual KPI plan for "+period.getName()+". "+dueText(deadline,phase(deadline,today)),
                    periodLink("/my-performance/my-kpi-plan",period),ROLE_USER);
        }
    }

    private void centralTasks(AnnualKpiReviewPeriod period,List<ReviewCheckpoint> schedule,LocalDate today) {
        if(period.getAssessmentModel()!=KpiAssessmentModel.CENTRAL_RESULTS) return;
        for(var version:versions.findAllByReviewPeriodIdOrderByVersionNumberDescIdDesc(period.getId())) {
            var plan=version.getPlan();
            if((version.getStatus()!=KpiVersionStatus.EFFECTIVE && version.getStatus()!=KpiVersionStatus.SUPERSEDED)
                    || (version.getLevel()!=KpiLevel.COMPANY && version.getLevel()!=KpiLevel.DEPARTMENT)
                    || (version.getLevel()==KpiLevel.COMPANY?plan.getStatus()!=KpiPlanStatus.PUBLISHED:plan.getStatus()!=KpiPlanStatus.APPROVED)) continue;
            for(var checkpoint:schedule) {
                var opens=checkpoint.getEndDate().plusDays(1);
                if(!today.equals(opens) || results.eligibleParticipants(version.getId(),checkpoint.getId())==0
                        || results.findByPlanVersionIdAndCheckpointIdAndStatus(version.getId(),checkpoint.getId(),KpiCentralResultStatus.PUBLISHED).isPresent()) continue;
                var recipients=version.getLevel()==KpiLevel.COMPANY?companyEntrants(period):notify.departmentOwners(plan.getDepartment());
                for(var entrant:recipients) notify.scheduled("central-available:"+version.getId()+":"+checkpoint.getId(),entrant,KPI_TASK_AVAILABLE,
                        "KPI result entry available",(version.getLevel()==KpiLevel.COMPANY?"Company":"Department")+" KPI results for "+period.getName()
                                +" (reporting period ending "+DATE.format(checkpoint.getEndDate())+") are ready for entry.",
                        periodLink("/kpi-management/kpi-results",period)+"&planVersionId="+version.getId()+"&checkpointId="+checkpoint.getId(),
                        version.getLevel()==KpiLevel.COMPANY && notify.permitted(entrant,CAN_MANAGE_COMPANY_KPI)?CAN_MANAGE_COMPANY_KPI:
                                version.getLevel()==KpiLevel.COMPANY?CAN_ENTER_COMPANY_KPI_RESULTS:CAN_MANAGE_DEPARTMENT_KPI);
            }
        }
    }

    private List<Staff> companyEntrants(AnnualKpiReviewPeriod period) {
        var entrants=new LinkedHashMap<UUID,Staff>();
        notify.recipients(CAN_MANAGE_COMPANY_KPI).forEach(s->entrants.put(s.getId(),s));
        for(var s:notify.recipients(CAN_ENTER_COMPANY_KPI_RESULTS))
            if(delegates.existsByReviewPeriodIdAndStaffIdAndRevokedAtIsNull(period.getId(),s.getId())) entrants.put(s.getId(),s);
        return List.copyOf(entrants.values());
    }

    private void grouped(Map<Group,Reminder> groups,AnnualKpiReviewPeriod period,String task,Staff recipient,AuthorityName permission,
                         LocalDate deadline,LocalDate today,String label,String link) {
        String phase=phase(deadline,today); if(phase==null || !notify.permitted(recipient,permission)) return;
        groups.putIfAbsent(new Group(period.getId(),task,recipient.getId(),deadline,phase),
                new Reminder(recipient,permission,"Performance task reminder","You have "+label+" for "+period.getName()+". "+dueText(deadline,phase),link));
    }
    private String phase(LocalDate deadline,LocalDate today) {
        return deadline==null?null:today.equals(deadline.minusDays(3))?"DUE_SOON":today.equals(deadline.plusDays(1))?"OVERDUE":null;
    }
    private String dueText(LocalDate deadline,String phase) {
        return "OVERDUE".equals(phase)?"The deadline was "+DATE.format(deadline)+". You can still complete the task.":"Due on "+DATE.format(deadline)+".";
    }
    private LocalDate openingDate(ReviewPeriodParticipant p,LocalDate opening) {
        return max(opening,p.getReviewPeriod().getStartDate(),date(p.getReviewPeriod().getOpenedAt()),date(p.getCreatedAt()));
    }
    private LocalDate date(OffsetDateTime value) { return value==null?null:value.atZoneSameInstant(clock.getZone()).toLocalDate(); }
    private LocalDate max(LocalDate... dates) { return Arrays.stream(dates).filter(Objects::nonNull).max(Comparator.naturalOrder()).orElse(null); }
    private String periodLink(String path,AnnualKpiReviewPeriod period) {return path+"?reviewPeriodId="+period.getId();}
    private record Group(Long period,String task,UUID recipient,LocalDate deadline,String phase) {}
    private record Reminder(Staff recipient,AuthorityName permission,String title,String body,String link) {}
}
