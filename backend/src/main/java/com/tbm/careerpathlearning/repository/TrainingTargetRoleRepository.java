package com.tbm.careerpathlearning.repository;

import com.tbm.careerpathlearning.model.Role;
import com.tbm.careerpathlearning.model.TrainingTargetRole;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface TrainingTargetRoleRepository extends JpaRepository<TrainingTargetRole, Long> {

    @Query("SELECT DISTINCT ttr.training.trainingId FROM TrainingTargetRole ttr WHERE ttr.role.id = :roleId")
    List<Long> findTrainingIdsByRoleIds(@Param("roleId") Long roleId);

    void deleteByTraining_TrainingId(Long trainingId);

}
