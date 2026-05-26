package com.tbm.careerpathlearning.service.impl;

import com.tbm.careerpathlearning.dto.StaffLearningDocumentProgressDto;
import com.tbm.careerpathlearning.dto.UpdateDocumentProgressDto;
import com.tbm.careerpathlearning.exception.BadRequestException;
import com.tbm.careerpathlearning.exception.DataAccessException;
import com.tbm.careerpathlearning.mapper.AppMapper;
import com.tbm.careerpathlearning.model.LearningDocument;
import com.tbm.careerpathlearning.model.StaffLearningDocumentProgress;
import com.tbm.careerpathlearning.model.StaffLearningMaterial;
import com.tbm.careerpathlearning.repository.*;
import com.tbm.careerpathlearning.service.StaffLearningDocumentService;

import jakarta.transaction.Transactional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.MessageSource;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class StaffLearningDocumentServiceImpl implements StaffLearningDocumentService {

    @Autowired
    private StaffLearningDocumentRepository staffLearningDocumentRepository;

    @Autowired
    private StaffLearningMaterialRepository staffLearningMaterialRepository;

    @Autowired
    private LearningDocumentRepository learningDocumentRepository;

    @Autowired
    private StaffLearningMaterialServiceImpl staffLearningMaterialServiceImpl;

    @Autowired
    private MessageSource messageSource;

    @Autowired
    private AppMapper appMapper;

    private static final String STAFF_NOT_ENROLLED_MATERIAL = "staff.not.enrolled.material";

    private static final String LEARNING_DOCUMENT_NOT_FOUND = "learning.document.not.found";

    @Override
    @Transactional
    public void updateDocumentProgress(UpdateDocumentProgressDto dto) {

        StaffLearningMaterial slm = staffLearningMaterialRepository
                .findByStaff_IdAndLearningMaterial_MaterialId(dto.getStaffId(), dto.getMaterialId())
                .orElseThrow(() -> new BadRequestException(
                        "Invalid Enrollment",
                        messageSource.getMessage(
                                STAFF_NOT_ENROLLED_MATERIAL,
                                null,
                                Locale.getDefault()
                        )
                ));

        LearningDocument learningDocument = learningDocumentRepository.findById(dto.getDocumentId())
                .orElseThrow(() -> new BadRequestException(
                        "Invalid Document",
                        messageSource.getMessage(
                                LEARNING_DOCUMENT_NOT_FOUND,
                                null,
                                Locale.getDefault()
                        )
                ));

        StaffLearningDocumentProgress progressRecord = staffLearningDocumentRepository
                .findByStaffLearningMaterial_EnrollmentIdAndLearningDocument_DocumentId(slm.getEnrollmentId(), dto.getDocumentId())
                .orElseGet(() -> {
                    StaffLearningDocumentProgress newRec = new StaffLearningDocumentProgress();
                    newRec.setStaffLearningMaterial(slm);
                    newRec.setLearningDocument(learningDocument);
                    return newRec;
                });

        progressRecord.setProgress(dto.getProgress());
        progressRecord.setLastPosition(dto.getLastPosition());
        progressRecord.setLastAccessedAt(java.time.LocalDateTime.now());
        progressRecord.setIsCompleted(dto.getIsCompleted());

        if (dto.getIsCompleted()) {
            progressRecord.setProgress(100.0);
        }

        try {
            staffLearningDocumentRepository.save(progressRecord);
            staffLearningMaterialServiceImpl.updateMaterialProgress(
                    slm.getEnrollmentId(),
                    dto.getOverallProgress()
            );
        } catch (Exception ex) {
            throw new DataAccessException(
                    "Update Failed",
                    "Unable to update document progress.",
                    ex
            );
        }
    }

    @Override
    public StaffLearningDocumentProgressDto getProgress(UUID staffId, Long materialId, Long documentId) {
        StaffLearningMaterial slm = staffLearningMaterialRepository
                .findByStaff_IdAndLearningMaterial_MaterialId(staffId, materialId)
                .orElseThrow(() -> new BadRequestException(
                        "Invalid Enrollment",
                        "Staff is not enrolled in this learning material."
                ));

        StaffLearningDocumentProgress entity =
                staffLearningDocumentRepository
                        .findByStaffLearningMaterial_EnrollmentIdAndLearningDocument_DocumentId(
                                slm.getEnrollmentId(),
                                documentId
                        )
                        .orElseThrow(() -> new BadRequestException(
                                "Progress Not Found",
                                "No progress found for this document."
                        ));

        return appMapper.toDto(entity);
    }

    public List<StaffLearningDocumentProgressDto> getAllProgressByMaterial(UUID staffId, Long materialId){
        StaffLearningMaterial slm = staffLearningMaterialRepository
                .findByStaff_IdAndLearningMaterial_MaterialId(staffId, materialId)
                .orElseThrow(() -> new RuntimeException("Enrollment not found"));

        List<StaffLearningDocumentProgress> progressList =
                staffLearningDocumentRepository.findByStaffLearningMaterial_EnrollmentId(slm.getEnrollmentId());

        return progressList.stream()
                .map(appMapper::toDto)
                .collect(Collectors.toList());
    }


}
