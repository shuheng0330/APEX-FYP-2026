package com.tbm.careerpathlearning.service;
import com.tbm.careerpathlearning.model.*;
import com.tbm.careerpathlearning.repository.*;
import com.tbm.careerpathlearning.enums.StaffAccountStatus;
import com.tbm.careerpathlearning.exception.BadRequestException;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import java.time.*;
import java.util.*;

@Service
public class ReviewPeriodEnrolmentService {
    private final StaffRepository staff;
    private final ReviewPeriodParticipantRepository participants;
    private final ReviewPeriodParticipantFactory factory;
    private final PerformanceDepartmentResolver departments;
    private final Clock clock;
    public ReviewPeriodEnrolmentService(StaffRepository staff,ReviewPeriodParticipantRepository participants,
            ReviewPeriodParticipantFactory factory,PerformanceDepartmentResolver departments,@Qualifier("annualKpiReviewClock") Clock clock) {
        this.staff=staff;this.participants=participants;this.factory=factory;this.departments=departments;this.clock=clock;
    }
    public void enrol(AnnualKpiReviewPeriod period,List<ReviewPeriodRoleConfiguration> selected) {
        if(period.getParticipantsSnapshottedAt()!=null || participants.existsByReviewPeriodId(period.getId()))
            throw new BadRequestException("This review period already has a participant snapshot; refresh is not supported");
        Map<Long,ReviewPeriodRoleConfiguration> byRole=new HashMap<>();
        selected.forEach(c->{if(!c.getRole().isDeleted() && c.getRole().isPerformanceReviewEligible()) byRole.put(c.getRole().getId(),c);});
        var now=OffsetDateTime.now(clock);var roster=new ArrayList<ReviewPeriodParticipant>();
        for(Staff employee:staff.findEligibleReviewStaff(byRole.keySet(),StaffAccountStatus.ACTIVE)) {
            var configuration=byRole.get(employee.getRole().getId());
            if(configuration==null || employee.isDeleted() || employee.getAccountStatus()!=StaffAccountStatus.ACTIVE
                    || employee.getRole().isDeleted() || !employee.getRole().isPerformanceReviewEligible()) continue;
            var participant=factory.snapshot(period,employee,configuration,departments.resolve(employee.getRole()));
            participant.setCreatedAt(now);roster.add(participant);
        }
        participants.saveAllAndFlush(roster);
        period.setParticipantsSnapshottedAt(now);
    }
}
