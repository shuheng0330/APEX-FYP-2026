package com.tbm.careerpathlearning.repository;

import com.tbm.careerpathlearning.model.SopAssignment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;

@Repository
public interface SopAssignmentRepository extends JpaRepository<SopAssignment, Long> {
    List<SopAssignment> findAllBySopDocumentIdInOrderByCreatedAtDesc(Collection<Long> sopDocumentIds);

    /** Assignments that also take in new joiners, newest first (FR-09-12). */
    List<SopAssignment> findAllByIncludeNewJoinersTrueOrderByCreatedAtDesc();

    void deleteAllBySopDocumentId(Long sopDocumentId);
}
