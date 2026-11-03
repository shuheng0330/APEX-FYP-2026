package com.tbm.careerpathlearning.service;

import com.tbm.careerpathlearning.dto.*;
import com.tbm.careerpathlearning.enums.AttitudeAssessmentStatus;
import java.util.*;

public interface AttitudeAssessmentService {
    List<KpiPeriodContextDto> periods(UUID actor);
    AttitudeAssessmentDto mine(Long reviewPeriodId,UUID actor);
    AttitudeAssessmentDto get(Long id,UUID actor);
    AttitudeAssessmentDto create(AttitudeAssessmentRequest request,UUID actor);
    AttitudeAssessmentDto update(Long id,AttitudeAssessmentRequest request,UUID actor);
    AttitudeAssessmentDto submit(Long id,UUID actor);
    List<AttitudeAssessmentReviewDto> reviews(Long reviewPeriodId,AttitudeAssessmentStatus status,UUID actor);
    AttitudeAssessmentDto saveSuperiorDraft(Long id,AttitudeSuperiorAssessmentRequest request,UUID actor);
    AttitudeAssessmentDto completeReview(Long id,UUID actor);
}
