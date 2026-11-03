package com.tbm.careerpathlearning.repository;

import com.tbm.careerpathlearning.model.TrainingSessionTemplate;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface TrainingSessionTemplateRepository extends JpaRepository<TrainingSessionTemplate, Long> {

    List<TrainingSessionTemplate> findAllByCreatedBy(UUID createdBy);

    boolean existsByCreatedByAndNameIgnoreCase(UUID createdBy, String name);
}
