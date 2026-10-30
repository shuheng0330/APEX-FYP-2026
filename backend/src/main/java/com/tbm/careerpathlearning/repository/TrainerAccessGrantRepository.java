package com.tbm.careerpathlearning.repository;

import com.tbm.careerpathlearning.model.TrainerAccessGrant;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Repository
public interface TrainerAccessGrantRepository extends JpaRepository<TrainerAccessGrant, UUID> {

    /** Grants in force until a last day that is now past, whose lapse has not been recorded yet. */
    List<TrainerAccessGrant> findAllByRevokedAtIsNullAndLapsedAtIsNullAndEndDateBefore(LocalDate today);
}
