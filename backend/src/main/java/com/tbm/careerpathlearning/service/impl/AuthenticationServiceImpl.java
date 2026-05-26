package com.tbm.careerpathlearning.service.impl;

import com.tbm.careerpathlearning.dto.StaffDto;
import com.tbm.careerpathlearning.dto.StaffLoginAuditDto;
import com.tbm.careerpathlearning.dto.StaffRefreshTokenDto;
import com.tbm.careerpathlearning.enums.OtpPurpose;
import com.tbm.careerpathlearning.enums.StaffAccountStatus;
import com.tbm.careerpathlearning.exception.BadRequestException;
import com.tbm.careerpathlearning.exception.ForbiddenRequestException;
import com.tbm.careerpathlearning.service.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.MessageSource;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.util.Locale;
import java.util.UUID;

@Service
public class AuthenticationServiceImpl implements AuthenticationService {

    private static final Logger logger = LoggerFactory.getLogger(AuthenticationServiceImpl.class);

    @Value("${account.max.login.failed.attempts}")
    private int MAX_FAILED_ATTEMPTS;

    @Value("${cookies.expiry.day}")
    private Integer COOKIES_EXPIRY_DAYS;

    @Autowired
    private MessageSource messageSource;

    @Autowired
    private StaffService staffService;

    @Autowired
    private StaffOtpService staffOtpService;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private TokenService tokenService;

    @Autowired
    private StaffRefreshTokenService staffRefreshTokenService;

    @Autowired
    private StaffLoginAuditService staffLoginAuditService;

    private static final String INVALID_CREDENTIALS_ERR_TITLE_CODE = "invalid.credentials.err.title";

    private static final String INVALID_CREDENTIALS_ERR_MSG_CODE = "invalid.credentials.err.msg";

    private static final String ACCOUNT_BLOCKED_ERR_TITLE_CODE = "account.blocked.err.title";

    private static final String ACCOUNT_BLOCKED_ERR_MSG_CODE = "account.blocked.err.msg";

    private static final String INVALID_DATA_ERR_MSG_CODE = "invalid.data.err.msg";

    private static final String UPDATE_OPERATION = "Update Staff Account";

    @Override
    public boolean loginWithEmailAndPassword(StaffLoginAuditDto staffLoginAuditDto, StaffDto staffDto, String rawPassword) {

        if (passwordEncoder.matches(rawPassword, staffDto.getPassword())) {
            return true;
        } else {
            boolean isBlocked = false;

            staffLoginAuditDto.setLoginFailedAttempts(staffLoginAuditDto.getLoginFailedAttempts() + 1);
            staffLoginAuditDto.setLastLoginAt(OffsetDateTime.now());

            if (staffLoginAuditDto.getLoginFailedAttempts() == MAX_FAILED_ATTEMPTS) {
                isBlocked = true;
                staffDto.setAccountStatus(StaffAccountStatus.INACTIVE);
            }

            staffService.update(staffDto.getId(), staffDto);
            staffLoginAuditService.updateStaffLoginAudit(staffLoginAuditDto.getStaffId(), staffLoginAuditDto);

            if (isBlocked) {
                throw new ForbiddenRequestException(
                        messageSource.getMessage(ACCOUNT_BLOCKED_ERR_TITLE_CODE, null, Locale.getDefault()),
                        messageSource.getMessage(ACCOUNT_BLOCKED_ERR_MSG_CODE, null, Locale.getDefault())
                );
            }

            throw new ForbiddenRequestException(
                    messageSource.getMessage(INVALID_CREDENTIALS_ERR_TITLE_CODE, null, Locale.getDefault()),
                    messageSource.getMessage(INVALID_CREDENTIALS_ERR_MSG_CODE, null, Locale.getDefault())
            );
        }
    }

    @Override
    public String createAndSaveRefreshToken(UUID userId) {
        String refreshToken = tokenService.generateRefreshToken();
        StaffRefreshTokenDto staffRefreshTokenDto = staffRefreshTokenService.getStaffRefreshTokenById(userId);
        staffRefreshTokenDto.setToken(refreshToken);
        staffRefreshTokenDto.setCreatedAt(OffsetDateTime.now());
        staffRefreshTokenDto.setExpiresAt(OffsetDateTime.now().plusDays(COOKIES_EXPIRY_DAYS));

        staffRefreshTokenService.updateStaffRefreshToken(userId, staffRefreshTokenDto);

        return refreshToken;
    }

    @Override
    public String generateOtp(StaffDto staffDto, OtpPurpose otpPurpose) {
        return staffOtpService.createStaffOtp(staffDto, otpPurpose).getOtpCode();
    }

    @Override
    public boolean validateOtp(StaffDto staffDto, String otp, OtpPurpose otpPurpose) {
        return staffOtpService.validateOtp(staffDto, otp, otpPurpose);
    }

    @Override
    public void updateUserPasswordByUserId(StaffDto staffDto, String password, UUID staffId) {
        if (password != null && password.trim().length() > 255) {
            throw new BadRequestException(messageSource.getMessage(INVALID_DATA_ERR_MSG_CODE, new String[]{UPDATE_OPERATION}, Locale.getDefault()));
        }

        staffDto.setPassword(passwordEncoder.encode(password)); //Encode Before Update
        staffDto.setUpdatedBy(staffId);
        staffDto.setUpdatedAt(OffsetDateTime.now());

        staffService.update(staffId, staffDto);
    }
}

