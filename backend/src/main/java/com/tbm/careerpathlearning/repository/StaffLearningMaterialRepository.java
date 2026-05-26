package com.tbm.careerpathlearning.repository;

import com.tbm.careerpathlearning.model.LearningMaterial;
import com.tbm.careerpathlearning.model.StaffLearningMaterial;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface StaffLearningMaterialRepository extends JpaRepository<StaffLearningMaterial, Long> {

    boolean existsByStaff_IdAndLearningMaterial_MaterialId(UUID staffId, Long materialId);

    @Query("SELECT s.learningMaterial FROM StaffLearningMaterial s WHERE s.staff.id = :staffId ")
    List<LearningMaterial> findLearningMaterialsByStaffId(@Param("staffId") UUID staffId);

    Optional<StaffLearningMaterial> findByStaff_IdAndLearningMaterial_MaterialId(UUID staffId, Long materialId);

    @Query("SELECT s FROM StaffLearningMaterial s WHERE s.staff.id = :staffId")
    List<StaffLearningMaterial> findByStaffId(@Param("staffId") UUID staffId);

    @Query("SELECT s FROM StaffLearningMaterial s WHERE s.learningMaterial.materialId = :materialId")
    List<StaffLearningMaterial> findByMaterialId(@Param("materialId") Long materialId);

    List<StaffLearningMaterial> findByStaff_IdAndIsCompletedTrue(UUID staffId);


}
