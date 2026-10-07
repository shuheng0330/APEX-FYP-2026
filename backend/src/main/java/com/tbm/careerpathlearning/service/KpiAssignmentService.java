package com.tbm.careerpathlearning.service;
import com.tbm.careerpathlearning.enums.*;
import com.tbm.careerpathlearning.model.*;
import com.tbm.careerpathlearning.repository.*;
import com.tbm.careerpathlearning.exception.BadRequestException;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import java.time.*;
import java.util.*;
@Service
public class KpiAssignmentService {
    private final ReviewPeriodParticipantRepository participants;
    private final EmployeeKpiAssignmentRepository assignments;
    private final Clock clock;
    public KpiAssignmentService(ReviewPeriodParticipantRepository participants,EmployeeKpiAssignmentRepository assignments,
            @Qualifier("annualKpiReviewClock") Clock clock) {this.participants=participants;this.assignments=assignments;this.clock=clock;}
    public void requirePublishedRoster(AnnualKpiReviewPeriod period) {
        if(period.getStatus()!=AnnualKpiReviewPeriodStatus.UPCOMING && period.getStatus()!=AnnualKpiReviewPeriodStatus.OPEN)
            throw new BadRequestException("KPI publication or approval requires an Upcoming or Open Annual KPI Review Period");
        if(period.getParticipantsSnapshottedAt()==null)
            throw new BadRequestException("This historical period has no publication-time participant snapshot; use a newly published period or request explicit reconciliation");
    }
    public void cascade(KpiPlan plan) {
        requirePublishedRoster(plan.getReviewPeriod());
        List<ReviewPeriodParticipant> roster=participants.findAllByReviewPeriodId(plan.getReviewPeriod().getId());
        var output=new ArrayList<EmployeeKpiAssignment>();var now=OffsetDateTime.now(clock);
        for(var participant:roster) {
            if(plan.getLevel()==KpiLevel.DEPARTMENT && (participant.getDepartment()==null || !participant.getDepartment().getId().equals(plan.getDepartment().getId()))) continue;
            if(plan.getLevel()==KpiLevel.INDIVIDUAL && !participant.getId().equals(plan.getOwnerParticipantId())) continue;
            for(var item:plan.getItems()) {
                var assignment=new EmployeeKpiAssignment();assignment.setReviewPeriodId(plan.getReviewPeriod().getId());
                assignment.setKpiId(item.getId());assignment.setParticipantId(participant.getId());assignment.setAssignedAt(now);output.add(assignment);
            }
        }
        assignments.saveAllAndFlush(output);
    }
}
