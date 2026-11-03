package com.tbm.careerpathlearning.service;

import com.tbm.careerpathlearning.dto.*;
import com.tbm.careerpathlearning.enums.*;
import com.tbm.careerpathlearning.mapper.KpiPlanMapper;
import com.tbm.careerpathlearning.model.AnnualKpiReviewPeriod;
import com.tbm.careerpathlearning.repository.*;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.*;
import java.util.*;

@Service
public class TeamReviewPeriodService {
    private final ReviewPeriodParticipantRepository participants;
    private final StaffRepository staff;
    private final KpiPlanMapper mapper;
    private final Clock clock;
    public TeamReviewPeriodService(ReviewPeriodParticipantRepository participants, StaffRepository staff,
            KpiPlanMapper mapper, @Qualifier("annualKpiReviewClock") Clock clock) {
        this.participants=participants; this.staff=staff; this.mapper=mapper; this.clock=clock;
    }

    @Transactional(readOnly=true)
    @PreAuthorize("hasAnyAuthority('CAN_REVIEW_INDIVIDUAL_KPI','CAN_REVIEW_KPI_ASSESSMENT','CAN_REVIEW_ATTITUDE_EVALUATION')")
    public TeamReviewPeriodsDto periods(UUID actor) {
        var account=staff.findById(actor).orElseThrow(()->new AccessDeniedException("An active account is required"));
        if(account.isDeleted() || account.getAccountStatus()!=StaffAccountStatus.ACTIVE)
            throw new AccessDeniedException("An active account is required");
        var dateOrder=Comparator.comparing(AnnualKpiReviewPeriod::getStartDate,Comparator.nullsLast(Comparator.reverseOrder()))
                .thenComparing(AnnualKpiReviewPeriod::getId,Comparator.reverseOrder());
        var options=participants.findTeamReviewPeriods(actor).stream()
                .filter(p->p.getStatus()!=AnnualKpiReviewPeriodStatus.DRAFT).sorted(dateOrder).toList();
        var selected=options.stream().filter(p->p.getStatus()==AnnualKpiReviewPeriodStatus.OPEN).findFirst()
                .orElseGet(()->options.stream().filter(p->p.getEndDate()!=null && p.getEndDate().isBefore(LocalDate.now(clock)))
                    .sorted(Comparator.comparing(AnnualKpiReviewPeriod::getEndDate,Comparator.reverseOrder()).thenComparing(dateOrder))
                    .findFirst().orElse(null));
        return new TeamReviewPeriodsDto(options.stream().map(mapper::toContext).toList(), selected==null?null:selected.getId());
    }
}
