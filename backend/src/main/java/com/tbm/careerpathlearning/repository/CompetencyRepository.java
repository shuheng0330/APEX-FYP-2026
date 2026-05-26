package com.tbm.careerpathlearning.repository;

import com.tbm.careerpathlearning.model.Competency;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.Set;

public interface CompetencyRepository extends JpaRepository<Competency, Long> {

    List<Competency> findAllByIsDeletedIsFalse();

    List<Competency> findAllByIsDeletedIsFalseAndIdIn(Set<Long> competencyIds);

    Optional<Competency> findByNameIgnoreCaseAndIsDeletedIsFalse(String competencyName);

    List<Competency> findAllByNameInIgnoreCaseAndIsDeletedIsFalse(Set<String> competencyNames);
}
