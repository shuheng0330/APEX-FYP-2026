package com.tbm.careerpathlearning.service;

import com.tbm.careerpathlearning.dto.EvaluationCycleDto;

import java.util.List;
import java.util.UUID;

public interface EvaluationCycleService {

    EvaluationCycleDto getCurrentCycle();

    void openNewCycle(EvaluationCycleDto dto, UUID userId);

    EvaluationCycleDto updateEvaluationCycle( Long cycleId, EvaluationCycleDto dto, UUID adminId);

    List<EvaluationCycleDto> getEvaluationCycleHistory();
}
