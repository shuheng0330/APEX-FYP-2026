package com.tbm.careerpathlearning.repository;

import com.tbm.careerpathlearning.enums.OtpPurpose;
import com.tbm.careerpathlearning.model.Staff;
import com.tbm.careerpathlearning.model.StaffOtp;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Repository
public interface StaffOtpRepository extends JpaRepository<StaffOtp, Long> {

    @Query("SELECT so FROM StaffOtp so WHERE so.staff.id IN (:staffIds)")
    List<StaffOtp> findAllByStaffIdIn(@Param("staffIds") Set<UUID> staffIds);

    List<StaffOtp> findAllByStaff_Id(UUID staffId);

    Optional<StaffOtp> getByOtpCode(String otpCode);

    Optional<StaffOtp> findTopByStaffAndOtpPurposeAndUsedFalseOrderByCreatedAtDesc(
            Staff staff, OtpPurpose purpose
    );
}
