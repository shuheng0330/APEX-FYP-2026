package com.tbm.careerpathlearning.repository;

import com.tbm.careerpathlearning.model.SopQuizDraw;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.Optional;

@Repository
public interface SopQuizDrawRepository extends JpaRepository<SopQuizDraw, Long> {

    Optional<SopQuizDraw> findByEnrollmentIdAndSopModuleId(Long enrollmentId, Long sopModuleId);

    void deleteAllByEnrollmentIdIn(Collection<Long> enrollmentIds);
}
