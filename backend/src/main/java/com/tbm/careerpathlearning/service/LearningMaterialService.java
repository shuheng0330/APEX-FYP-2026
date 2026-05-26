package com.tbm.careerpathlearning.service;

import com.tbm.careerpathlearning.dto.LearningMaterialDto;
import com.tbm.careerpathlearning.dto.LearningMaterialRequestDto;
import com.tbm.careerpathlearning.model.LearningDocument;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.UUID;

public interface LearningMaterialService {

    LearningMaterialDto createLearningMaterial(LearningMaterialRequestDto request, UUID userId);

    List<LearningMaterialDto> getAllLearningMaterials();

    LearningMaterialDto getLearningMaterialById(Long id);

    void deleteLearningMaterial(Long id);

    LearningMaterialDto updateLearningMaterial(Long id, LearningMaterialRequestDto request);

    List<LearningDocument> uploadDocuments(List<MultipartFile> files, List<String> titles, String folderName);

    void bulkDeleteLearningMaterial(List<Long> ids);
}
