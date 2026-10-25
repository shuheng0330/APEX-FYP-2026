package com.tbm.careerpathlearning.repository;

import com.tbm.careerpathlearning.model.SopEnrollment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface SopEnrollmentRepository extends JpaRepository<SopEnrollment, Long> {
    List<SopEnrollment> findAllBySopDocumentId(Long sopDocumentId);

    List<SopEnrollment> findAllBySopDocumentIdIn(Collection<Long> sopDocumentIds);

    List<SopEnrollment> findAllByStaffId(UUID staffId);

    Optional<SopEnrollment> findBySopDocumentIdAndStaffId(Long sopDocumentId, UUID staffId);

    void deleteAllBySopDocumentId(Long sopDocumentId);
}
