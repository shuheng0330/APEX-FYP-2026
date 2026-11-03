package com.tbm.careerpathlearning.service;

import com.tbm.careerpathlearning.dto.TeamPerformanceDto;
import com.tbm.careerpathlearning.dto.TeamPerformanceDto.*;
import com.tbm.careerpathlearning.enums.*;
import com.tbm.careerpathlearning.exception.BadRequestException;
import com.tbm.careerpathlearning.model.*;
import com.tbm.careerpathlearning.mapper.TeamPerformanceMapper;
import com.tbm.careerpathlearning.repository.TeamPerformanceRepository;
import com.tbm.careerpathlearning.repository.RoleAuthorityRepository;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.Clock;
import java.time.LocalDate;
import java.util.*;
import static com.tbm.careerpathlearning.service.StaffPerformanceAccess.*;
import static com.tbm.careerpathlearning.service.PerformanceSummaryCalculator.*;

@Service
@Transactional(readOnly = true)
@PreAuthorize("hasAnyAuthority('CAN_VIEW_TEAM_PERFORMANCE','CAN_VIEW_DEPARTMENT_PERFORMANCE','CAN_VIEW_ORGANISATION_PERFORMANCE')")
public class TeamPerformanceService {
    private final TeamPerformanceRepository repository;
    private final TeamPerformanceResults results;
    private final StaffPerformanceAccess access;
    private final PerformanceDepartmentResolver departments;
    private final RoleAuthorityRepository roleAuthorities;
    private final TeamPerformanceMapper mapper;
    private final Clock clock;

    public TeamPerformanceService(TeamPerformanceRepository repository, TeamPerformanceResults results,
            StaffPerformanceAccess access, PerformanceDepartmentResolver departments, RoleAuthorityRepository roleAuthorities,
            TeamPerformanceMapper mapper, @Qualifier("annualKpiReviewClock") Clock clock) {
        this.repository = repository; this.results = results; this.access = access; this.departments = departments;
        this.roleAuthorities = roleAuthorities; this.mapper = mapper; this.clock = clock;
    }

    public List<ScopeOption> scopes(UUID actorId) {
        var actor = access.actor(actorId);
        var options = new ArrayList<ScopeOption>();
        if (access.has(TEAM)) options.add(new ScopeOption(Scope.TEAM, null, "My Direct Reports"));
        if (access.has(ORGANISATION)) {
            for (var d : repository.findDepartments()) options.add(new ScopeOption(Scope.DEPARTMENT, d.getId(),
                    d.getName() == null ? "Unassigned Department" : d.getName()));
        } else if (access.has(DEPARTMENT)) {
            var d = departments.resolve(actor.getRole());
            if (d != null) options.add(new ScopeOption(Scope.DEPARTMENT, d.getId(), d.getName()));
        }
        if (!access.has(TEAM) && !access.has(DEPARTMENT) && !access.has(ORGANISATION)) throw denied();
        return List.copyOf(options);
    }

    public List<Period> periods(Scope scope, Long departmentId, UUID actorId) {
        requireScope(scope, departmentId, actorId);
        return available(scope, departmentId, actorId).stream().map(this::period).toList();
    }

    public TeamPerformanceDto dashboard(Scope scope, Long departmentId, Long reviewPeriodId, int historyYears,
            Metric metric, UUID actorId) {
        if (historyYears != 3 && historyYears != 5) throw new BadRequestException("Choose a 3 or 5 year performance trend");
        if (reviewPeriodId == null || reviewPeriodId <= 0 || metric == null) throw new BadRequestException("Select a review period and score metric");
        var selectedScope = requireScope(scope, departmentId, actorId);
        var available = available(scope, departmentId, actorId);
        var selected = available.stream().filter(p -> p.getId().equals(reviewPeriodId)).findFirst().orElseThrow(StaffPerformanceAccess::denied);
        int firstYear = selected.getEndDate().getYear() - historyYears + 1;
        var periods = available.stream().filter(p -> !p.getEndDate().isAfter(selected.getEndDate())
                && p.getEndDate().getYear() >= firstYear)
                .sorted(Comparator.comparing(AnnualKpiReviewPeriod::getEndDate).thenComparing(AnnualKpiReviewPeriod::getId)).toList();
        var population = repository.findPopulation(scope == Scope.TEAM, actorId, departmentId, periods.stream().map(AnnualKpiReviewPeriod::getId).toList());
        var data = results.load(population);
        var current = population.stream().filter(p -> p.getReviewPeriod().getId().equals(reviewPeriodId)).toList();
        var previous = periods.stream().filter(p -> p.getEndDate().getYear() == selected.getEndDate().getYear() - 1).toList();
        AnnualKpiReviewPeriod prior = previous.size() == 1 ? previous.get(0) : null;
        var priorPopulation = prior == null ? List.<ReviewPeriodParticipant>of() : population.stream()
                .filter(p -> p.getReviewPeriod().getId().equals(prior.getId())).toList();
        Comparison comparison = previous.isEmpty() ? Comparison.NO_PREVIOUS_PERIOD
                : previous.size() > 1 ? Comparison.AMBIGUOUS_PREVIOUS_PERIOD
                : comparablePeriod(selected, prior) ? Comparison.AVAILABLE : Comparison.INCOMPATIBLE_CONFIGURATION;
        var before = new HashMap<UUID, ReviewPeriodParticipant>();
        priorPopulation.forEach(p -> before.put(p.getStaff().getId(), p));
        var rows = current.stream().sorted(Comparator.comparing(ReviewPeriodParticipant::getStaffName, Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER))
                        .thenComparing(ReviewPeriodParticipant::getId))
                .map(p -> new Employee(p.getStaff().getId(), p.getId(), p.getStaffName(), p.getDepartmentName(), p.getRoleName(), data.scores().get(p.getId()),
                        employeeChange(p, before.get(p.getStaff().getId()), data, comparison, prior))).toList();
        var summary = summary(current, data);
        var change = scopeChange(current, priorPopulation, data, comparison, prior);
        var trend = periods.stream().map(p -> new TrendPoint(period(p), summary(population.stream()
                .filter(employee -> employee.getReviewPeriod().getId().equals(p.getId())).toList(), data))).toList();
        return new TeamPerformanceDto(selectedScope, period(selected), summary, attention(selected, current, data, actorId), trend,
                distribution(current, data, metric), rows, change);
    }

    private ScopeOption requireScope(Scope scope, Long departmentId, UUID actorId) {
        if (scope == null || scope == Scope.TEAM && departmentId != null) throw denied();
        return scopes(actorId).stream().filter(s -> s.type() == scope && Objects.equals(s.departmentId(), departmentId))
                .findFirst().orElseThrow(StaffPerformanceAccess::denied);
    }

    private List<AnnualKpiReviewPeriod> available(Scope scope, Long departmentId, UUID actorId) {
        return repository.findPeriods(scope == Scope.TEAM, actorId, departmentId).stream()
                .filter(p -> p.getStatus() != AnnualKpiReviewPeriodStatus.DRAFT && p.getParticipantsSnapshottedAt() != null
                        && p.getStartDate() != null && p.getEndDate() != null).toList();
    }

    private Attention attention(AnnualKpiReviewPeriod period, List<ReviewPeriodParticipant> population, TeamPerformanceResults.Data data, UUID actor) {
        LocalDate today = LocalDate.now(clock);
        boolean open = period.getStatus() == AnnualKpiReviewPeriodStatus.OPEN;
        int myKpi = 0, myAttitude = 0, myPlans = 0, overdue = 0, returnedRevisions = 0;
        var byId = new HashMap<Long, ReviewPeriodParticipant>();
        for (var p : population) {
            byId.put(p.getId(), p);
            boolean reportsToMe = currentReport(p, actor);
            for (var c : data.required(p)) {
                var a = data.assessment(p.getId(), c.getId());
                if (a != null && a.getStatus() == KpiAssessmentStatus.PENDING_REVIEW) {
                    if (open && reportsToMe && access.has("CAN_REVIEW_KPI_ASSESSMENT") && actor.equals(a.getSubmittedToSuperiorId())) {
                        myKpi++; if (late(today, c.getSuperiorAssessmentDeadline())) overdue++;
                    }
                }
            }
            var attitude = data.attitudes().get(p.getId());
            if (attitude != null && attitude.getStatus() == AttitudeAssessmentStatus.PENDING_REVIEW) {
                if (open && reportsToMe && access.has("CAN_REVIEW_ATTITUDE_EVALUATION") && actor.equals(attitude.getSubmittedToSuperiorId())) {
                    myAttitude++; if (late(today, period.getSuperiorAttitudeEvaluationDeadline())) overdue++;
                }
            }
        }
        for (var plan : data.plans()) {
            var p = byId.get(plan.getParticipantId());
            if (p == null) continue;
            if ((open || period.getStatus() == AnnualKpiReviewPeriodStatus.UPCOMING) && plan.getStatus() == KpiPlanStatus.PENDING_APPROVAL
                    && access.has("CAN_REVIEW_INDIVIDUAL_KPI") && currentReport(p, actor) && actor.equals(plan.getSubmittedToSuperiorId())) {
                myPlans++; if (late(today, period.getKpiSetupDeadline())) overdue++;
            }
        }
        boolean amendmentsOpen = (open || period.getStatus() == AnnualKpiReviewPeriodStatus.UPCOMING) && period.isKpiAmendmentsEnabled()
                && (period.getKpiAmendmentsCloseDate() == null || !today.isAfter(period.getKpiAmendmentsCloseDate()));
        boolean revisionReviewer = false;
        if (amendmentsOpen && !data.returnedRevisions().isEmpty() && access.has("CAN_REVIEW_INDIVIDUAL_KPI")) {
            var role = access.actor(actor).getRole();
            revisionReviewer = role != null && !role.isDeleted() && roleAuthorities.existsByRoleIdAndAuthorityName(
                    role.getId(), AuthorityName.CAN_REVIEW_INDIVIDUAL_KPI);
        }
        for (var revision : data.returnedRevisions()) {
            var p = byId.get(revision.getParticipantId());
            if (p != null && revisionReviewer && currentReport(p, actor)
                    && actor.equals(revision.getSuperiorId()) && actor.equals(revision.getPreparedBy())
                    && revision.getMode() == KpiRevisionPreparationMode.SUPERIOR) returnedRevisions++;
        }
        var links = new ArrayList<ActionLink>();
        if (myKpi > 0) links.add(link("KPI_REVIEWS", "assessments", period));
        if (myAttitude > 0) links.add(link("ATTITUDE_REVIEWS", "attitude", period));
        if (myPlans > 0) links.add(link("INDIVIDUAL_PLAN_APPROVALS", "reviews", period));
        if (returnedRevisions > 0) links.add(link("RETURNED_INDIVIDUAL_REVISIONS", "revisions", period));
        return new Attention(new Actions(myKpi, myAttitude, myPlans, overdue, returnedRevisions, List.copyOf(links)),
                progress(period, population, data, today));
    }

    private boolean currentReport(ReviewPeriodParticipant p, UUID actor) {
        var employee = p.getStaff();
        return !employee.getId().equals(actor) && !employee.isDeleted() && employee.getAccountStatus() == StaffAccountStatus.ACTIVE
                && employee.getManager() != null && actor.equals(employee.getManager().getId());
    }
    private boolean late(LocalDate today, LocalDate deadline) { return deadline != null && today.isAfter(deadline); }
    private ActionLink link(String task, String tab, AnnualKpiReviewPeriod p) {
        return new ActionLink(task, "/team-performance/team-reviews", Map.of("tab", tab, "reviewPeriodId", p.getId().toString()));
    }

    private Period period(AnnualKpiReviewPeriod p) {
        return mapper.toPeriod(p);
    }
}
