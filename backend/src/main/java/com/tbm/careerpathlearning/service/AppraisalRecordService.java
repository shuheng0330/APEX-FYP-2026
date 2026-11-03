package com.tbm.careerpathlearning.service;

import com.tbm.careerpathlearning.dto.AppraisalReadinessDto;
import com.tbm.careerpathlearning.dto.AppraisalRecordDto;
import com.tbm.careerpathlearning.dto.HrAppraisalActionDto;

import java.util.List;
import java.util.UUID;

public interface AppraisalRecordService {

    AppraisalRecordDto createOrUpdateDraft(AppraisalRecordDto dto, UUID userId);

    AppraisalRecordDto submit(UUID id, UUID userId);

    List<AppraisalRecordDto> getAllByStaffId(UUID staffId);

    List<AppraisalRecordDto> getPendingRecords();

    List<AppraisalRecordDto> getReviewRecords();

    List<AppraisalRecordDto> getLatestTeamRecords(UUID managerId);

    AppraisalRecordDto approve(UUID id, UUID userId);

    AppraisalRecordDto overrideAndApprove(UUID id, HrAppraisalActionDto dto, UUID userId);

    AppraisalRecordDto returnForRevision(UUID id, HrAppraisalActionDto dto, UUID userId);

    AppraisalReadinessDto calculateReadinessScore(UUID staffId, Long evaluationCycleId, Integer reviewPeriodYears);
}
