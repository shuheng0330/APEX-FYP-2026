package com.tbm.careerpathlearning.service;

import com.tbm.careerpathlearning.dto.*;
import java.util.*;

public interface AnnualAppraisalService {
    List<KpiPeriodContextDto> periods(boolean hrReview,UUID actor);
    List<AnnualAppraisalCandidateDto> team(Long periodId,UUID actor);
    List<AnnualAppraisalDto> reviews(Long periodId,UUID actor);
    AnnualAppraisalReadinessDto readiness(Long participantId,UUID actor);
    AnnualAppraisalDto get(Long id,UUID actor);
    List<AnnualAppraisalDto> history(Long participantId,UUID actor);
    List<AnnualPerformanceHistoryDto> performanceHistory(Long participantId,UUID actor);
    AnnualAppraisalDto create(AnnualAppraisalRequest request,UUID actor);
    AnnualAppraisalDto update(Long id,AnnualAppraisalRequest request,UUID actor);
    AnnualAppraisalDto submit(Long id,AnnualAppraisalActionRequest request,UUID actor);
    AnnualAppraisalDto approve(Long id,AnnualAppraisalActionRequest request,UUID actor);
    AnnualAppraisalDto overrideAndApprove(Long id,AnnualAppraisalActionRequest request,UUID actor);
    AnnualAppraisalDto returnForRevision(Long id,AnnualAppraisalActionRequest request,UUID actor);
}
