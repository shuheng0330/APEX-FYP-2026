package com.tbm.careerpathlearning.repository;

import com.tbm.careerpathlearning.model.CareerPathway;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Set;

public interface CareerPathwayRepository extends JpaRepository<CareerPathway, Long> {

    List<CareerPathway> findAllByIsDeletedIsFalse();

    List<CareerPathway> findAllByIsDeletedIsFalseAndIdIn(Set<Long> id);

}
