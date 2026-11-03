package com.tbm.careerpathlearning.repository;

import com.tbm.careerpathlearning.enums.StaffAccountStatus;
import com.tbm.careerpathlearning.model.Staff;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface OrgWideStaffRepository extends JpaRepository<Staff, UUID> {

    @Query("""
            SELECT COUNT(s)
            FROM Staff s
            WHERE s.isDeleted = false
              AND s.accountStatus = :accountStatus
              AND s.role IS NOT NULL
              AND s.role.id <> :excludedRoleId
            """)
    Long countActiveRealStaff(@Param("accountStatus") StaffAccountStatus accountStatus,
                              @Param("excludedRoleId") Long excludedRoleId);
}
