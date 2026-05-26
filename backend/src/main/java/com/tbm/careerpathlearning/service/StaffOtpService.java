package com.tbm.careerpathlearning.service;

import com.tbm.careerpathlearning.dto.StaffDto;
import com.tbm.careerpathlearning.dto.StaffOtpDto;
import com.tbm.careerpathlearning.enums.OtpPurpose;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public interface StaffOtpService {

    String generateOtp();

    List<StaffOtpDto> getAllStaffOtps();

    StaffOtpDto getStaffOtpById(Long id);

    List<StaffOtpDto> getStaffOtpByStaffId(UUID id);

    Optional<StaffOtpDto> getStaffOtpByOtpCode(String otpCode);

    List<StaffOtpDto> getStaffOtpByStaffIdIn(Set<UUID> ids);

    StaffOtpDto updateStaffOtp(Long id, StaffOtpDto dto);

    StaffOtpDto createStaffOtp(StaffDto staffDto, OtpPurpose otpPurpose);

    Optional<StaffOtpDto> findTopByStaffAndOtpPurposeAndUsedFalseOrderByCreatedAtDesc(
            StaffDto staff, OtpPurpose purpose
    );

    boolean validateOtp(StaffDto staffDto, String otp, OtpPurpose otpPurpose);

    void deleteStaffOtp(Long staffID);

    void deleteAllByIdIn(Set<Long> staffIds);

}
