package com.tbm.careerpathlearning.repository;

import com.tbm.careerpathlearning.model.SopDocument;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface SopDocumentRepository extends JpaRepository<SopDocument, Long> {
    List<SopDocument> findAllByIsDeletedFalseOrderByCreatedAtDesc();
}
