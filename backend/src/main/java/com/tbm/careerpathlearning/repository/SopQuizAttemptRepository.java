package com.tbm.careerpathlearning.repository;

import com.tbm.careerpathlearning.model.SopQuizAttempt;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;

@Repository
public interface SopQuizAttemptRepository extends JpaRepository<SopQuizAttempt, Long> {
    List<SopQuizAttempt> findAllByEnrollmentIdOrderByAttemptedAtAscIdAsc(Long enrollmentId);

    List<SopQuizAttempt> findAllByEnrollmentIdInOrderByAttemptedAtAscIdAsc(Collection<Long> enrollmentIds);

    void deleteAllByEnrollmentIdIn(Collection<Long> enrollmentIds);
}
