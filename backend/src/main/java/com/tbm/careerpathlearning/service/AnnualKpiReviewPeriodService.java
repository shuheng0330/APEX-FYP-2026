package com.tbm.careerpathlearning.service;

import com.tbm.careerpathlearning.dto.AnnualKpiReviewPeriodDto;
import com.tbm.careerpathlearning.dto.AnnualKpiReviewPeriodRequest;
import java.util.List;
import java.util.UUID;

public interface AnnualKpiReviewPeriodService {
    AnnualKpiReviewPeriodDto create(AnnualKpiReviewPeriodRequest request, boolean publish, UUID actor);
    AnnualKpiReviewPeriodDto update(Long id, AnnualKpiReviewPeriodRequest request, UUID actor);
    AnnualKpiReviewPeriodDto publish(Long id, UUID actor);
    AnnualKpiReviewPeriodDto get(Long id);
    List<AnnualKpiReviewPeriodDto> list();
    List<AnnualKpiReviewPeriodDto.RoleConfiguration> availableRoles();
    AnnualKpiReviewPeriodDto preview(AnnualKpiReviewPeriodRequest request, Long excludedPeriodId);
    void delete(Long id);
    void openDuePeriods();
}
