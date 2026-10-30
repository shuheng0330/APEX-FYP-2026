package com.tbm.careerpathlearning.repository;

import com.tbm.careerpathlearning.model.AnnualKpiReviewPeriod;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import java.util.List;

public interface AnnualPerformancePeriodRepository extends Repository<AnnualKpiReviewPeriod,Long> {
    @Query("select p from AnnualKpiReviewPeriod p where p.status<>com.tbm.careerpathlearning.enums.AnnualKpiReviewPeriodStatus.DRAFT "
            + "and p.participantsSnapshottedAt is not null and p.startDate is not null and p.endDate is not null order by p.endDate desc,p.id desc")
    List<AnnualKpiReviewPeriod> findPeriods();
}
