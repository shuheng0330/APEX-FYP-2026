package com.tbm.careerpathlearning.repository;

import com.tbm.careerpathlearning.enums.TrainingRegistrationStatus;
import com.tbm.careerpathlearning.model.TrainingRegistration;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface TrainingRegistrationRepository extends JpaRepository<TrainingRegistration, Long> {

    List<TrainingRegistration> findAllByTrainingId(Long trainingId);

    /** Every registration for these sessions, to count their seats in one go (FR-13-04). */
    List<TrainingRegistration> findAllByTrainingIdIn(Collection<Long> trainingIds);

    boolean existsByTrainingId(Long trainingId);

    /** A person's registration for a session; there is at most one, reused if they register again. */
    Optional<TrainingRegistration> findByTrainingIdAndStaffId(Long trainingId, UUID staffId);

    /** A person's registrations in these states, across sessions. */
    List<TrainingRegistration> findAllByStaffIdAndStatusIn(UUID staffId, Collection<TrainingRegistrationStatus> statuses);

    /** Seat offers that have run out (FR-13-07). */
    List<TrainingRegistration> findAllByStatusAndOfferExpiresAtLessThanEqual(TrainingRegistrationStatus status,
                                                                             LocalDateTime time);
}
