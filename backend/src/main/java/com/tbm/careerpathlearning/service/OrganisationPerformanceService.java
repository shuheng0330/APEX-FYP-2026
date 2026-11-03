package com.tbm.careerpathlearning.service;

import com.tbm.careerpathlearning.dto.OrganisationPerformanceDto;
import com.tbm.careerpathlearning.dto.OrganisationPerformanceDto.*;
import com.tbm.careerpathlearning.dto.TeamPerformanceDto.*;
import com.tbm.careerpathlearning.enums.*;
import com.tbm.careerpathlearning.exception.BadRequestException;
import com.tbm.careerpathlearning.mapper.TeamPerformanceMapper;
import com.tbm.careerpathlearning.model.*;
import com.tbm.careerpathlearning.repository.OrganisationPerformanceRepository;
import com.tbm.careerpathlearning.repository.AnnualPerformancePeriodRepository;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import java.time.Clock;
import java.time.LocalDate;
import java.util.*;
import static com.tbm.careerpathlearning.service.PerformanceSummaryCalculator.*;

@Service
@Transactional(readOnly=true, isolation=Isolation.REPEATABLE_READ)
@PreAuthorize("hasAuthority('CAN_VIEW_ORGANISATION_PERFORMANCE')")
public class OrganisationPerformanceService {
    static final int MINIMUM_DECISION_GROUP_SIZE=5;
    private final OrganisationPerformanceRepository repository;
    private final AnnualPerformancePeriodRepository periodRepository;
    private final TeamPerformanceResults results;
    private final StaffPerformanceAccess access;
    private final TeamPerformanceMapper mapper;
    private final Clock clock;
    public OrganisationPerformanceService(OrganisationPerformanceRepository repository, AnnualPerformancePeriodRepository periodRepository, TeamPerformanceResults results,
            StaffPerformanceAccess access, TeamPerformanceMapper mapper, @Qualifier("annualKpiReviewClock") Clock clock) {
        this.repository=repository; this.periodRepository=periodRepository; this.results=results; this.access=access; this.mapper=mapper; this.clock=clock;
    }
    public List<Period> periods(UUID actorId) {
        requireAccess(actorId);
        return periodRepository.findPeriods().stream().map(mapper::toPeriod).toList();
    }
    public OrganisationPerformanceDto dashboard(Long reviewPeriodId, int historyYears, Metric metric, UUID actorId) {
        requireAccess(actorId);
        if (reviewPeriodId==null || reviewPeriodId<=0 || metric==null || historyYears!=3 && historyYears!=5)
            throw new BadRequestException("Select a published review period, score metric and 3 or 5 year range");
        var available=periodRepository.findPeriods();
        var selected=available.stream().filter(p -> p.getId().equals(reviewPeriodId)).findFirst().orElseThrow(StaffPerformanceAccess::denied);
        var periods=available.stream().filter(p -> !p.getEndDate().isAfter(selected.getEndDate()) && p.getEndDate().getYear()>=selected.getEndDate().getYear()-historyYears+1)
                .sorted(Comparator.comparing(AnnualKpiReviewPeriod::getEndDate).thenComparing(AnnualKpiReviewPeriod::getId)).toList();
        var population=repository.findPopulation(periods.stream().map(AnnualKpiReviewPeriod::getId).toList());
        var data=results.load(population);
        var byPeriod=new HashMap<Long,List<ReviewPeriodParticipant>>();
        population.forEach(p -> byPeriod.computeIfAbsent(p.getReviewPeriod().getId(), k -> new ArrayList<>()).add(p));
        var current=byPeriod.getOrDefault(reviewPeriodId,List.of());
        var previous=periods.stream().filter(p -> p.getEndDate().getYear()==selected.getEndDate().getYear()-1).toList();
        var prior=previous.size()==1?previous.get(0):null;
        var before=prior==null?List.<ReviewPeriodParticipant>of():byPeriod.getOrDefault(prior.getId(),List.of());
        var comparison=previous.isEmpty()?Comparison.NO_PREVIOUS_PERIOD:previous.size()>1?Comparison.AMBIGUOUS_PREVIOUS_PERIOD
                :comparablePeriod(selected,prior)?Comparison.AVAILABLE:Comparison.INCOMPATIBLE_CONFIGURATION;
        var pending=new HashSet<Long>();
        for(var p:repository.findAppraisalProgress(reviewPeriodId))if(p.getStatus()==AppraisalStatus.PENDING_REVIEW)pending.add(p.getParticipantId());
        var groups=groupDepartments(current);var priorGroups=groupDepartments(before);
        var departments=new ArrayList<Department>();
        for(var entry:groups.entrySet()) {
            var members=entry.getValue();
            departments.add(new Department(entry.getKey(),departmentName(members),summary(members,data),progress(selected,members,data,LocalDate.now(clock)),
                    (int)members.stream().filter(p -> pending.contains(p.getId())).count(),
                    scopeChange(members,priorGroups.getOrDefault(entry.getKey(),List.of()),data,comparison,prior)));
        }
        departments.sort(Comparator.comparing(Department::name,String.CASE_INSENSITIVE_ORDER).thenComparing(Department::departmentId,Comparator.nullsLast(Comparator.naturalOrder())));
        var trend=periods.stream().map(p -> new TrendPoint(mapper.toPeriod(p),summary(byPeriod.getOrDefault(p.getId(),List.of()),data))).toList();
        var series=new LinkedHashMap<Long,List<TrendPoint>>();var names=new HashMap<Long,String>();
        for(var p:periods)for(var entry:groupDepartments(byPeriod.getOrDefault(p.getId(),List.of())).entrySet()) {
            series.computeIfAbsent(entry.getKey(),k -> new ArrayList<>()).add(new TrendPoint(mapper.toPeriod(p),summary(entry.getValue(),data)));
            names.put(entry.getKey(),departmentName(entry.getValue()));
        }
        var departmentTrends=series.entrySet().stream().map(e -> new DepartmentTrend(e.getKey(),names.get(e.getKey()),List.copyOf(e.getValue())))
                .sorted(Comparator.comparing(DepartmentTrend::name,String.CASE_INSENSITIVE_ORDER).thenComparing(DepartmentTrend::departmentId,Comparator.nullsLast(Comparator.naturalOrder()))).toList();
        return new OrganisationPerformanceDto(mapper.toPeriod(selected),historyYears,summary(current,data),scopeChange(current,before,data,comparison,prior),
                trend,distribution(current,data,metric),List.copyOf(departments),departmentTrends,
                decisions(repository.findDecisions(reviewPeriodId),current));
    }
    private void requireAccess(UUID actorId) {
        access.actor(actorId);
        if(!access.has(StaffPerformanceAccess.ORGANISATION))throw StaffPerformanceAccess.denied();
    }
    private Map<Long,List<ReviewPeriodParticipant>> groupDepartments(List<ReviewPeriodParticipant> population) {
        var groups=new LinkedHashMap<Long,List<ReviewPeriodParticipant>>();
        for(var p:population)groups.computeIfAbsent(p.getDepartment()==null?null:p.getDepartment().getId(), k -> new ArrayList<>()).add(p);
        return groups;
    }
    private String departmentName(List<ReviewPeriodParticipant> population) {
        return population.stream().sorted(Comparator.comparing(ReviewPeriodParticipant::getId)).map(ReviewPeriodParticipant::getDepartmentName)
                .filter(n -> n!=null && !n.isBlank()).findFirst().orElse("Unassigned Department");
    }
    private DecisionDistribution decisions(List<OrganisationPerformanceRepository.Decision> records,List<ReviewPeriodParticipant> population) {
        var participants=new HashSet<Long>();population.forEach(p -> participants.add(p.getId()));
        var recommendations=new EnumMap<AnnualAppraisalRecommendation,Integer>(AnnualAppraisalRecommendation.class);
        var finalDecisions=new EnumMap<AnnualAppraisalRecommendation,Integer>(AnnualAppraisalRecommendation.class);
        int submitted=0,approved=0;
        for(var record:records) {
            if(!participants.contains(record.getParticipantId()) || record.getStatus()==AppraisalStatus.DRAFT)continue;
            if(record.getSubmittedAt()!=null && record.getSubmittedRecommendation()!=null) { recommendations.merge(record.getSubmittedRecommendation(),1,Integer::sum);submitted++; }
            if(record.getStatus()==AppraisalStatus.APPROVED && record.getReviewedAt()!=null && record.getFinalDecision()!=null) { finalDecisions.merge(record.getFinalDecision(),1,Integer::sum);approved++; }
        }
        return new DecisionDistribution(MINIMUM_DECISION_GROUP_SIZE,disclose(recommendations,submitted),disclose(finalDecisions,approved));
    }
    private DecisionSeries disclose(Map<AnnualAppraisalRecommendation,Integer> counts,int total) {
        if(total==0)return new DecisionSeries(Disclosure.NO_DATA,0,List.of());
        // Suppress the whole series, including its total, to prevent subtraction from exposing a rare category.
        if(total<MINIMUM_DECISION_GROUP_SIZE || counts.size()<2 || counts.values().stream().anyMatch(n -> n<MINIMUM_DECISION_GROUP_SIZE))
            return new DecisionSeries(Disclosure.SUPPRESSED,null,List.of());
        return new DecisionSeries(Disclosure.AVAILABLE,total,Arrays.stream(AnnualAppraisalRecommendation.values())
                .map(d -> new DecisionCount(d,counts.getOrDefault(d,0))).toList());
    }
}
