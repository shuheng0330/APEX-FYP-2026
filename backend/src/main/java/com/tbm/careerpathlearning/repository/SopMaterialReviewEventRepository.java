package com.tbm.careerpathlearning.repository;

import com.tbm.careerpathlearning.model.SopMaterialReviewEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;

@Repository
public interface SopMaterialReviewEventRepository extends JpaRepository<SopMaterialReviewEvent, Long> {

    List<SopMaterialReviewEvent> findAllBySopModuleIdOrderByCreatedAtDescIdDesc(Long sopModuleId);

    void deleteAllBySopModuleIdIn(Collection<Long> sopModuleIds);
}
