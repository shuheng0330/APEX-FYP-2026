package com.tbm.careerpathlearning.repository;

import com.tbm.careerpathlearning.model.SopAssignment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;

@Repository
public interface SopAssignmentRepository extends JpaRepository<SopAssignment, Long> {
    List<SopAssignment> findAllBySopDocumentIdInOrderByCreatedAtDesc(Collection<Long> sopDocumentIds);

    void deleteAllBySopDocumentId(Long sopDocumentId);
}
