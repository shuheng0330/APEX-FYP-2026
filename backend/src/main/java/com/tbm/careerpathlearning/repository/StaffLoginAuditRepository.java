package com.tbm.careerpathlearning.repository;

import com.tbm.careerpathlearning.model.StaffLoginAudit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Set;
import java.util.UUID;

@Repository
public interface StaffLoginAuditRepository extends JpaRepository<StaffLoginAudit, UUID> {

    @Query("SELECT sla FROM StaffLoginAudit sla WHERE sla.staffId IN (:staffIds)")
    List<StaffLoginAudit> findAllByIdIn(@Param("staffIds") Set<UUID> staffIds);
}
