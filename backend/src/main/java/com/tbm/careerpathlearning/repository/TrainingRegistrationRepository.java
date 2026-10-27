package com.tbm.careerpathlearning.repository;

import com.tbm.careerpathlearning.model.TrainingRegistration;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;

@Repository
public interface TrainingRegistrationRepository extends JpaRepository<TrainingRegistration, Long> {

    List<TrainingRegistration> findAllByTrainingId(Long trainingId);

    /** Every registration for these sessions, to count their seats in one go (FR-13-04). */
    List<TrainingRegistration> findAllByTrainingIdIn(Collection<Long> trainingIds);

    boolean existsByTrainingId(Long trainingId);
}
