package com.tbm.careerpathlearning.service;

import com.tbm.careerpathlearning.dto.LearningMaterialDto;

import java.util.List;
import java.util.UUID;

public interface LearningMaterialRecommendationService {

   List<LearningMaterialDto> recommendForUser(UUID roleId);
}
