package com.tbm.careerpathlearning.repository;

import com.tbm.careerpathlearning.model.TrainingEvidence;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface TrainingEvidenceRepository extends JpaRepository<TrainingEvidence, Long> {

    List<TrainingEvidence> findAllByTrainingIdOrderByUploadedAtAscEvidenceIdAsc(Long trainingId);
}
