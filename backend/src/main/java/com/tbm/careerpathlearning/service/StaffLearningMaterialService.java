package com.tbm.careerpathlearning.service;

import com.tbm.careerpathlearning.dto.LearningMaterialDto;
import com.tbm.careerpathlearning.dto.MaterialProgressDto;
import com.tbm.careerpathlearning.dto.StaffLearningMaterialDto;

import java.util.List;
import java.util.UUID;

public interface StaffLearningMaterialService {

    StaffLearningMaterialDto enroll(StaffLearningMaterialDto staffLearningMaterialDto);

    List<LearningMaterialDto> getEnrolledCourses(UUID staffId);

    List<MaterialProgressDto> getProgressList(UUID staffId);

    List<StaffLearningMaterialDto> getEnrollmentsByMaterialId(Long materialId);

    StaffLearningMaterialDto getLearningMaterialByStaffId(UUID staffId, Long materialId);

    void unenroll(UUID staffId, Long materialId);

}
