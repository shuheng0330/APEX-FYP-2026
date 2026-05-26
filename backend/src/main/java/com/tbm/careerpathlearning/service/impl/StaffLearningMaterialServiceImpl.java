package com.tbm.careerpathlearning.service.impl;

import com.tbm.careerpathlearning.dto.*;
import com.tbm.careerpathlearning.mapper.AppMapper;
import com.tbm.careerpathlearning.model.*;
import com.tbm.careerpathlearning.repository.LearningMaterialRepository;
import com.tbm.careerpathlearning.repository.StaffLearningDocumentRepository;
import com.tbm.careerpathlearning.repository.StaffLearningMaterialRepository;
import com.tbm.careerpathlearning.repository.StaffRepository;
import com.tbm.careerpathlearning.service.StaffLearningMaterialService;
import jakarta.transaction.Transactional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class StaffLearningMaterialServiceImpl implements StaffLearningMaterialService {

    @Autowired
    private StaffLearningMaterialRepository staffLearningMaterialRepository;

    @Autowired
    private StaffRepository staffRepository;

    @Autowired
    private LearningMaterialRepository learningMaterialRepository;

    @Autowired
    private StaffLearningDocumentRepository  staffLearningDocumentRepository;

    @Autowired
    private AppMapper appMapper;

    @Override
    public StaffLearningMaterialDto enroll (StaffLearningMaterialDto staffLearningMaterialDto) {
        Staff staff = staffRepository.findById(staffLearningMaterialDto.getStaffId())
                .orElseThrow(() -> new RuntimeException("Staff not found"));
        LearningMaterial material = learningMaterialRepository.findById(staffLearningMaterialDto.getMaterialId())
                .orElseThrow(() -> new RuntimeException("Material not found"));

        if (staffLearningMaterialRepository.existsByStaff_IdAndLearningMaterial_MaterialId(staffLearningMaterialDto.getStaffId(), staffLearningMaterialDto.getMaterialId())) {
            throw new RuntimeException("Enrollment already exist");
        }

            StaffLearningMaterial staffLearningMaterial = new StaffLearningMaterial();
            staffLearningMaterial.setStaff(staff);
            staffLearningMaterial.setLearningMaterial(material);
            staffLearningMaterial.setEnrolledAt(LocalDateTime.now());
            staffLearningMaterial.setCompleted(false);
            staffLearningMaterialRepository.save(staffLearningMaterial);

            List<LearningDocument> documents = material.getLearningDocuments();
            for (LearningDocument doc : documents){
                StaffLearningDocumentProgress docProgress = new StaffLearningDocumentProgress();
                docProgress.setStaffLearningMaterial(staffLearningMaterial);
                docProgress.setLearningDocument(doc);
                docProgress.setProgress(0.0);
                docProgress.setLastAccessedAt(LocalDateTime.now());

                staffLearningDocumentRepository.save(docProgress);
            }

          return appMapper.toDto(staffLearningMaterial);
        }


    @Override
    public List<LearningMaterialDto> getEnrolledCourses(UUID staffId){
        return staffLearningMaterialRepository.findLearningMaterialsByStaffId(staffId).stream()
                .map(material -> appMapper.toDto(material))
                .collect(Collectors.toList());
    }

    @Override
    public List<MaterialProgressDto> getProgressList(UUID staffId) {
        List<StaffLearningMaterial> enrolledList = staffLearningMaterialRepository.findByStaffId(staffId);
        return enrolledList.stream()
                .map(enrollment -> new MaterialProgressDto(
                        enrollment.getLearningMaterial().getMaterialId(),
                        enrollment.getProgress() != null ? enrollment.getProgress() : 0.0
                ))
                .toList();
    }

    @Override
    public StaffLearningMaterialDto getLearningMaterialByStaffId(UUID staffId, Long materialId){
        return staffLearningMaterialRepository.findByStaff_IdAndLearningMaterial_MaterialId(staffId, materialId).map(appMapper::toDto)
                .orElseThrow(() -> new RuntimeException("Learning Material Not Found"));
    }

    @Transactional
    public void updateMaterialProgress(Long enrollmentId, Double overallProgress) {
        StaffLearningMaterial slm = staffLearningMaterialRepository.findById(enrollmentId)
                .orElseThrow(() -> new RuntimeException("Enrollment not found"));

        // Get all document progresses for this enrollment
        List<StaffLearningDocumentProgress> documentProgressList =
                staffLearningDocumentRepository.findByStaffLearningMaterial_EnrollmentId(enrollmentId);

        if (documentProgressList.isEmpty()) {
            slm.setProgress(0.0);
            slm.setCompleted(false);
            staffLearningMaterialRepository.save(slm);
            return;
        }

        slm.setProgress(overallProgress);

        // Mark completed if all documents = 100%
        boolean allCompleted = documentProgressList.stream()
                .allMatch(doc -> doc.getProgress() >= 100.0);
        slm.setCompleted(allCompleted);

        if (allCompleted) {
            slm.setCompletedAt(LocalDateTime.now());
        }

        staffLearningMaterialRepository.save(slm);
    }

    @Override
    public List<StaffLearningMaterialDto> getEnrollmentsByMaterialId(Long materialId) {
        List<StaffLearningMaterial> enrolledList = staffLearningMaterialRepository.findByMaterialId(materialId);

        return enrolledList.stream().map(record -> {
            StaffLearningMaterialDto staffLearningMaterialDto = new StaffLearningMaterialDto();
            staffLearningMaterialDto.setStaffDto(appMapper.toDto(record.getStaff()));
            staffLearningMaterialDto.setProgress(record.getProgress());
            staffLearningMaterialDto.setEnrolledAt(record.getEnrolledAt());
            staffLearningMaterialDto.setCompletedAt(record.getCompletedAt());
            staffLearningMaterialDto.setCompleted(record.isCompleted());
            return staffLearningMaterialDto;

        }).collect(Collectors.toList());

    }

    @Override
    @Transactional
    public void unenroll(UUID staffId, Long materialId) {

        StaffLearningMaterial enrollment = staffLearningMaterialRepository
                .findByStaff_IdAndLearningMaterial_MaterialId(staffId, materialId)
                .orElseThrow(() -> new RuntimeException("Enrollment not found"));

        staffLearningDocumentRepository.deleteByStaffLearningMaterial(enrollment);

        staffLearningMaterialRepository.delete(enrollment);
    }

}
