package com.tbm.careerpathlearning.repository;

import com.tbm.careerpathlearning.model.SopQuizReviewEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;

@Repository
public interface SopQuizReviewEventRepository extends JpaRepository<SopQuizReviewEvent, Long> {
    List<SopQuizReviewEvent> findAllBySopModuleIdOrderByCreatedAtDescIdDesc(Long sopModuleId);

    void deleteAllBySopModuleIdIn(Collection<Long> sopModuleIds);
}
