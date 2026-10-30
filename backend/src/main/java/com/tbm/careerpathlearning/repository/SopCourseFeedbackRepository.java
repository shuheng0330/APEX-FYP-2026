package com.tbm.careerpathlearning.repository;

import com.tbm.careerpathlearning.model.SopCourseFeedback;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface SopCourseFeedbackRepository extends JpaRepository<SopCourseFeedback, Long> {
    Optional<SopCourseFeedback> findBySopDocumentIdAndStaffId(Long sopDocumentId, UUID staffId);

    List<SopCourseFeedback> findAllBySopDocumentIdIn(Collection<Long> sopDocumentIds);

    void deleteAllBySopDocumentId(Long sopDocumentId);
}
