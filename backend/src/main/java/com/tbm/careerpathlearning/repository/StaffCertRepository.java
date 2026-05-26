package com.tbm.careerpathlearning.repository;

import com.tbm.careerpathlearning.model.StaffCert;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Repository
public interface StaffCertRepository extends JpaRepository<StaffCert, Long> {

    Optional<StaffCert> findByStaff_IdAndFileNameIgnoreCase(UUID staffId, String fileName);

    List<StaffCert> findAllByStaff_Id(UUID staffId);

    void deleteAllByStaff_Id(UUID staffId);

    void deleteAllByStaff_IdIn(Set<UUID> staffIds);
}
