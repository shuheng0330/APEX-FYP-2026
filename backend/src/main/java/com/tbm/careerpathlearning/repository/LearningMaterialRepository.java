package com.tbm.careerpathlearning.repository;

import com.tbm.careerpathlearning.model.LearningMaterial;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface LearningMaterialRepository extends JpaRepository<LearningMaterial,Long> {

}
