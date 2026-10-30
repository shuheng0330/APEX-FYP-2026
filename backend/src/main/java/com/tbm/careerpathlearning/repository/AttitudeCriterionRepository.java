package com.tbm.careerpathlearning.repository;

import com.tbm.careerpathlearning.model.AttitudeCriterion;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface AttitudeCriterionRepository extends JpaRepository<AttitudeCriterion,Long> {
    List<AttitudeCriterion> findAllByConfigurationIdAndActiveTrueOrderByDisplayOrderAscIdAsc(Long configurationId);
}
