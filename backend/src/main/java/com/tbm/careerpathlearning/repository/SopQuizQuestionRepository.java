package com.tbm.careerpathlearning.repository;

import com.tbm.careerpathlearning.model.SopQuizQuestion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;

@Repository
public interface SopQuizQuestionRepository extends JpaRepository<SopQuizQuestion, Long> {
    List<SopQuizQuestion> findAllBySopModuleIdOrderByIdAsc(Long sopModuleId);

    List<SopQuizQuestion> findAllBySopModuleIdInOrderByIdAsc(Collection<Long> sopModuleIds);

    void deleteAllBySopModuleIdIn(Collection<Long> sopModuleIds);
}
