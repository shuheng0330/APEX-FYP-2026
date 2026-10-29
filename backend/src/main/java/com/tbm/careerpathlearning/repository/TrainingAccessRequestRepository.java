package com.tbm.careerpathlearning.repository;

import com.tbm.careerpathlearning.model.TrainingAccessRequest;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

@Repository
public interface TrainingAccessRequestRepository extends JpaRepository<TrainingAccessRequest, Long> {

    boolean existsByStaffIdAndTrainingId(UUID staffId, Long trainingId);

    /** A person's requests made from these sessions, for their view of each. */
    List<TrainingAccessRequest> findAllByStaffIdAndTrainingIdIn(UUID staffId, Collection<Long> trainingIds);
}
