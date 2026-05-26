package com.tbm.careerpathlearning.repository;

import com.tbm.careerpathlearning.model.StaffLearningDocumentProgress;
import com.tbm.careerpathlearning.model.StaffLearningMaterial;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface StaffLearningDocumentRepository extends JpaRepository<StaffLearningDocumentProgress, Long> {

    Optional<StaffLearningDocumentProgress> findByStaffLearningMaterial_EnrollmentIdAndLearningDocument_DocumentId(Long enrollmentId, Long documentId);

    List<StaffLearningDocumentProgress> findByStaffLearningMaterial_EnrollmentId(Long enrollmentId);

    void deleteByStaffLearningMaterial(StaffLearningMaterial staffLearningMaterial);
}
