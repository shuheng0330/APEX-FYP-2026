package com.tbm.careerpathlearning.service;

import com.tbm.careerpathlearning.dto.*;
import java.util.*;

public interface AttitudeAssessmentService {
    List<KpiPeriodContextDto> periods(UUID actor);
    AttitudeAssessmentDto mine(Long reviewPeriodId,UUID actor);
    AttitudeAssessmentDto get(Long id,UUID actor);
    AttitudeAssessmentDto create(AttitudeAssessmentRequest request,UUID actor);
    AttitudeAssessmentDto update(Long id,AttitudeAssessmentRequest request,UUID actor);
    AttitudeAssessmentDto submit(Long id,UUID actor);
}
