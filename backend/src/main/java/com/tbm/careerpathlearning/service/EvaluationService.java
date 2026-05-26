package com.tbm.careerpathlearning.service;

import com.tbm.careerpathlearning.dto.EvaluationDTO;

import java.util.List;
import java.util.UUID;

public interface EvaluationService {
    EvaluationDTO createEvaluation(EvaluationDTO dto);
//    EvaluationDTO updateEvaluation(EvaluationDTO dto);
    List<EvaluationDTO> getAllEvaluations();

    List<EvaluationDTO> getAllEvaluationsByStaffId(UUID staffId);

    List<EvaluationDTO> getAllDownLineEvaluation(UUID staffId);
}
