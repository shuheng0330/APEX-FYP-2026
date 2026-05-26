package com.tbm.careerpathlearning.service;

import com.tbm.careerpathlearning.dto.StaffDto;
import com.tbm.careerpathlearning.dto.StaffLoginAuditDto;
import com.tbm.careerpathlearning.enums.OtpPurpose;
import java.util.UUID;

public interface AuthenticationService {

    boolean loginWithEmailAndPassword(StaffLoginAuditDto staffLoginAuditDto, StaffDto staffDto, String rawPassword);

    String createAndSaveRefreshToken(UUID userId);

    String generateOtp(StaffDto staffDto, OtpPurpose otpPurpose);

    boolean validateOtp(StaffDto staffDto, String otp, OtpPurpose otpPurpose);

    void updateUserPasswordByUserId(StaffDto staffDto, String password, UUID staffId);
}


