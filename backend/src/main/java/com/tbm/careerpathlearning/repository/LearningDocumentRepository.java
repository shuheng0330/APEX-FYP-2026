package com.tbm.careerpathlearning.repository;

import com.tbm.careerpathlearning.model.LearningDocument;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LearningDocumentRepository extends JpaRepository<LearningDocument, Long> {
}
