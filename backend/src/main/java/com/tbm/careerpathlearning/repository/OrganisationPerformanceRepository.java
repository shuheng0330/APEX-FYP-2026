package com.tbm.careerpathlearning.repository;

import com.tbm.careerpathlearning.enums.*;
import com.tbm.careerpathlearning.model.*;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.Repository;
import java.time.OffsetDateTime;
import java.util.*;

/** Read projections only; confidential appraisal text/snapshots are never fetched. */
public interface OrganisationPerformanceRepository extends Repository<ReviewPeriodParticipant, Long> {
    @EntityGraph(attributePaths={"reviewPeriod", "department", "employeeLevelConfiguration"})
    @Query("select p from ReviewPeriodParticipant p where p.reviewPeriod.id in :periodIds "
            + "and p.reviewPeriod.status<>com.tbm.careerpathlearning.enums.AnnualKpiReviewPeriodStatus.DRAFT and p.reviewPeriod.participantsSnapshottedAt is not null order by p.id")
    List<ReviewPeriodParticipant> findPopulation(Collection<Long> periodIds);

    interface AppraisalProgress { Long getParticipantId(); AppraisalStatus getStatus(); }
    @Query("select a.participant.id as participantId,a.status as status from AnnualAppraisal a where a.reviewPeriodId=:periodId "
            + "and a.participant.reviewPeriod.id=a.reviewPeriodId and a.participant.staff.id=a.employeeId")
    List<AppraisalProgress> findAppraisalProgress(Long periodId);

    interface Decision {
        Long getParticipantId(); AppraisalStatus getStatus(); OffsetDateTime getSubmittedAt(); OffsetDateTime getReviewedAt();
        AnnualAppraisalRecommendation getSubmittedRecommendation(); AnnualAppraisalRecommendation getFinalDecision();
    }
    @Query("""
        select a.participant.id as participantId,a.status as status,a.submittedAt as submittedAt,a.reviewedAt as reviewedAt,
               event.recommendation as submittedRecommendation,a.finalDecision as finalDecision
        from AnnualAppraisal a left join AnnualAppraisalActivity event on event.appraisalId=a.id
            and event.id=(select max(latest.id) from AnnualAppraisalActivity latest
                where latest.appraisalId=a.id and latest.action in ('SUBMITTED','RESUBMITTED'))
        where a.reviewPeriodId=:periodId and a.participant.reviewPeriod.id=a.reviewPeriodId
            and a.participant.staff.id=a.employeeId and a.status<>com.tbm.careerpathlearning.enums.AppraisalStatus.DRAFT
        """)
    List<Decision> findDecisions(Long periodId);
}
