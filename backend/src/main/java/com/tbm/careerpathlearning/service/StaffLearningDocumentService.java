package com.tbm.careerpathlearning.service;

import com.tbm.careerpathlearning.dto.StaffLearningDocumentProgressDto;
import com.tbm.careerpathlearning.dto.UpdateDocumentProgressDto;

import java.util.List;
import java.util.UUID;

public interface StaffLearningDocumentService {
    void updateDocumentProgress(UpdateDocumentProgressDto dto);

     StaffLearningDocumentProgressDto getProgress(UUID staffId, Long materialId, Long documentId);

    List<StaffLearningDocumentProgressDto> getAllProgressByMaterial(UUID staffId, Long materialId);
}
