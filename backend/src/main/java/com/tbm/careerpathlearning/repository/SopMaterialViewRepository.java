package com.tbm.careerpathlearning.repository;

import com.tbm.careerpathlearning.model.SopMaterialView;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.Optional;

@Repository
public interface SopMaterialViewRepository extends JpaRepository<SopMaterialView, Long> {

    Optional<SopMaterialView> findByEnrollmentIdAndSopModuleId(Long enrollmentId, Long sopModuleId);

    void deleteAllByEnrollmentIdIn(Collection<Long> enrollmentIds);
}
