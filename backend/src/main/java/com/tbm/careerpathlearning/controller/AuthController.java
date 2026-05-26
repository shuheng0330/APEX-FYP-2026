package com.tbm.careerpathlearning.controller;

import com.tbm.careerpathlearning.dto.*;
import com.tbm.careerpathlearning.enums.OtpPurpose;
import com.tbm.careerpathlearning.enums.StaffAccountStatus;
import com.tbm.careerpathlearning.exception.BadRequestException;
import com.tbm.careerpathlearning.exception.ForbiddenRequestException;
import com.tbm.careerpathlearning.service.*;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.MessageSource;
import org.springframework.http.*;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;

@Slf4j
@RestController
@RequestMapping("/auth")
public class AuthController {

    @Value("${cookies.expiry.day}")
    private Integer COOKIES_EXPIRY_DAYS;

    @Value("${account.max.login.failed.attempts}")
    private int MAX_FAILED_ATTEMPTS;

    @Value("${account.max.reset.password.attempts}")
    private int MAX_RESET_PASSWORD_ATTEMPTS;

    @Value("${cookies.secure:true}")
    private boolean cookieSecure;

    @Autowired
    private MessageSource messageSource;

    @Autowired
    private EmailService emailService;

    @Autowired
    private StaffService staffService;

    @Autowired
    private RoleAuthorityService roleAuthorityService;

    @Autowired
    private AuthorityService authorityService;

    @Autowired
    private ValidationService validationService;

    @Autowired
    private StaffLoginAuditService staffLoginAuditService;

    @Autowired
    private AuthenticationService authenticationService;

    @Autowired
    private TokenService tokenService;

    @Autowired
    private StaffRefreshTokenService staffRefreshTokenService;

    private static final Logger logger = LoggerFactory.getLogger(AuthController.class);

    private static final int ONE_DAY_IN_HOURS = 24;

    private static final int MINUTE_BEFORE_OTP_RESENT = 1;

    private static final int ZERO = 0;

    private static final String PASSWORD_REGEX = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d).{6,}$";

    private static final String FORBIDDEN_ERR_MSG_CODE = "forbidden.request.err.msg";

    private static final String INVALID_REQUEST_BODY_ERR_MSG_CODE = "invalid.request.body.err.msg";

    private static final String EMAIL_NOT_REGISTERED_ERR_TITLE_CODE = "email.not.registered.err.title";

    private static final String EMAIL_NOT_REGISTERED_ERR_MSG_CODE = "email.not.registered.err.msg";

    private static final String ACCOUNT_BLOCKED_ERR_TITLE_CODE = "account.blocked.err.title";

    private static final String ACCOUNT_BLOCKED_ERR_MSG_CODE = "account.blocked.err.msg";

    private static final String OTP_EXCEED_DAY_LIMIT_ERR_TITLE_CODE = "otp.request.exceed.day.limit.err.title";

    private static final String OTP_EXCEED_DAY_LIMIT_ERR_MSG_CODE = "otp.request.exceed.day.limit.err.msg";

    private static final String OTP_EXCEED_MINUTE_LIMIT_ERR_TITLE_CODE = "otp.request.exceed.minute.limit.err.title";

    private static final String OTP_EXCEED_MINUTE_LIMIT_ERR_MSG_CODE = "otp.request.exceed.minute.limit.err.msg";

    private static final String INVALID_PASSWORD_FORMAT_ERR_TITLE_CODE = "invalid.password.format.err.title";

    private static final String INVALID_PASSWORD_FORMAT_ERR_MSG_CODE = "invalid.password.format.err.msg";

    private static final String FIRST_TIME_LOGIN_ERR_TITLE_CODE = "first.time.login.err.title";

    private static final String FIRST_TIME_LOGIN_ERR_MSG_CODE = "first.time.login.err.msg";

    private static final String NOT_FIRST_TIME_LOGIN_ERR_TITLE_CODE = "not.first.time.login.err.title";

    private static final String NOT_FIRST_TIME_LOGIN_ERR_MSG_CODE = "not.first.time.login.err.msg";

    private static final String LOGIN_OK = "login.ok.msg";

    private static final String RESET_PASSWORD_EMAIL_OK = "reset.password.email.ok";

    private static final String RESET_PASSWORD_UPDATE_OK = "reset.password.update.ok";

    private static final String REFRESH_OK = "refresh.ok.msg";

    private static final String LOGIN_OPERATION = "Login";

    private static final String FORGOT_PASSWORD_OPERATION = "Forgot Password";

    private static final String RESET_PASSWORD_OPERATION = "Reset Password";

    private static final String FIRST_TIME_LOGIN_OPERATION = "First Time Login";

    public AuthController() {

    }

    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody LoginRequest req,
                                   HttpServletRequest request,
                                   HttpServletResponse response) throws Exception {
        if (validationService.isNullOrBlank(req.getEmail()) || validationService.isNullOrBlank(req.getPassword())) {
            String errorMessage = messageSource.getMessage(INVALID_REQUEST_BODY_ERR_MSG_CODE, new String[]{LOGIN_OPERATION}, Locale.getDefault());

            throw new BadRequestException(errorMessage);
        }

        String email = req.getEmail().trim().toLowerCase();
        String password = req.getPassword();

        Optional<StaffDto> existedEmail = this.staffService.findByIsDeletedIsFalseAndEmail(email);

        if (existedEmail.isEmpty()) {
            String errorTitle = messageSource.getMessage(EMAIL_NOT_REGISTERED_ERR_TITLE_CODE, null, Locale.getDefault());
            String errorMessage = messageSource.getMessage(EMAIL_NOT_REGISTERED_ERR_MSG_CODE, null, Locale.getDefault());
            throw new BadRequestException(errorTitle, errorMessage);
        }

        StaffDto staffDto = existedEmail.get();

        if (staffDto.isFirstLogin()) {
            String errorTitle = messageSource.getMessage(FIRST_TIME_LOGIN_ERR_TITLE_CODE, null, Locale.getDefault());
            String errorMessage = messageSource.getMessage(FIRST_TIME_LOGIN_ERR_MSG_CODE, null, Locale.getDefault());
            throw new BadRequestException(errorTitle, errorMessage);
        }

        StaffLoginAuditDto staffLoginAuditDto = staffLoginAuditService.getStaffLoginAuditById(staffDto.getId());

        if (staffDto.getAccountStatus() != StaffAccountStatus.ACTIVE || staffLoginAuditDto.getLoginFailedAttempts() >= MAX_FAILED_ATTEMPTS) {
            String errorTitle = messageSource.getMessage(ACCOUNT_BLOCKED_ERR_TITLE_CODE, null, Locale.getDefault());
            String errorMessage = messageSource.getMessage(ACCOUNT_BLOCKED_ERR_MSG_CODE, null, Locale.getDefault());
            throw new ForbiddenRequestException(errorTitle, errorMessage);
        }

        boolean authenticated = authenticationService.loginWithEmailAndPassword(staffLoginAuditDto, staffDto, password);

        if (!authenticated) {
            String errorMessage = messageSource.getMessage(FORBIDDEN_ERR_MSG_CODE, null, Locale.getDefault());

            throw new ForbiddenRequestException(errorMessage);
        }

        UUID userId = staffDto.getId();

        List<GrantedAuthority> authorities = this.getGrantedAuthoritiesList(staffDto);
        List<String> roles = authorities.stream()
                .map(GrantedAuthority::getAuthority)
                .toList();

        String accessToken = tokenService.generateAccessToken(userId, roles);

        String refreshToken = authenticationService.createAndSaveRefreshToken(userId);

        ResponseCookie cookie = ResponseCookie.from("sb-refresh", refreshToken)
                .httpOnly(true)
                .secure(cookieSecure)
                .path("/")
                .sameSite("Lax")
                .maxAge(Duration.ofDays(COOKIES_EXPIRY_DAYS))
                .build();
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());

        staffLoginAuditDto.setLoginFailedAttempts(0); //reset failure attempts once successfully logged in
        staffLoginAuditDto.setLastLoginAt(OffsetDateTime.now());
        staffLoginAuditService.updateStaffLoginAudit(userId, staffLoginAuditDto);

        return ResponseEntity.ok(Map.of(
                "message", messageSource.getMessage(LOGIN_OK, null, Locale.getDefault()),
                "userId", userId.toString(),
                "roles", roles,
                "accessToken", accessToken
        ));
    }

    @PostMapping("/refresh")
    public ResponseEntity<?> refresh(HttpServletRequest request, HttpServletResponse response) throws Exception {
        String refreshToken = Arrays.stream(Optional.ofNullable(request.getCookies()).orElse(new Cookie[0]))
                .filter(c -> "sb-refresh".equals(c.getName()))
                .map(Cookie::getValue).findFirst().orElse(null);

        if (refreshToken == null) {
            String errorMessage = messageSource.getMessage(FORBIDDEN_ERR_MSG_CODE, null, Locale.getDefault());

            throw new ForbiddenRequestException(errorMessage);
        }

        Optional<StaffRefreshTokenDto> staffRefreshTokenDto = staffRefreshTokenService.getStaffRefreshTokenByToken(refreshToken);

        if (staffRefreshTokenDto.isEmpty() || staffRefreshTokenDto.get().getExpiresAt().isBefore(OffsetDateTime.now())) {
            throw new ForbiddenRequestException(messageSource.getMessage(FORBIDDEN_ERR_MSG_CODE, null, Locale.getDefault()));
        }

        UUID userId = staffRefreshTokenDto.get().getStaffId();

        //refresh token
        String newRefreshToken = authenticationService.createAndSaveRefreshToken(userId);

        ResponseCookie cookie = ResponseCookie.from("sb-refresh", newRefreshToken)
                .httpOnly(true)
                .secure(cookieSecure)
                .path("/")
                .sameSite("Lax")
                .maxAge(Duration.ofDays(COOKIES_EXPIRY_DAYS))
                .build();
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());

        //Access Token
        StaffDto staffDto = staffService.findById(userId);
        List<String> roles = getGrantedAuthoritiesList(staffDto).stream()
                .map(GrantedAuthority::getAuthority)
                .toList();

        String accessToken = tokenService.generateAccessToken(userId, roles);

        return ResponseEntity.ok(Map.of(
                "message", messageSource.getMessage(REFRESH_OK, null, Locale.getDefault()),
                "userId", userId,
                "roles", roles,
                "accessToken", accessToken
        ));
    }

    @PostMapping("/logout")
    public ResponseEntity<?> logout(HttpServletResponse response) {
        // Delete refresh token cookie
        ResponseCookie sbRefreshCookie = ResponseCookie.from("sb-refresh", "")
                .httpOnly(true)
                .secure(cookieSecure)
                .sameSite("Lax")
                .path("/")
                .maxAge(ZERO)
                .build();

        response.addHeader(HttpHeaders.SET_COOKIE, sbRefreshCookie.toString());

        SecurityContextHolder.clearContext();

        return ResponseEntity.noContent().build();
    }

    @GetMapping("/me")
    public ResponseEntity<?> me(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            String errorMessage = messageSource.getMessage(FORBIDDEN_ERR_MSG_CODE, null, Locale.getDefault());

            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(errorMessage);
        }

        List<String> roles = authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .toList();

        return ResponseEntity.ok(Map.of(
                "userId", authentication.getPrincipal(),
                "roles", roles
        ));
    }

    @PostMapping("/forgot-password")
    public ResponseEntity<?> forgotPassword(@RequestParam String email) throws Exception {
        if (validationService.isNullOrBlank(email)) {
            String errorMessage = messageSource.getMessage(INVALID_REQUEST_BODY_ERR_MSG_CODE, new String[]{FORGOT_PASSWORD_OPERATION}, Locale.getDefault());

            throw new BadRequestException(errorMessage);
        }

        email = email.trim().toLowerCase();

        Optional<StaffDto> existedEmail = this.staffService.findByIsDeletedIsFalseAndEmail(email);

        if (existedEmail.isEmpty()) {
            String errorTitle = messageSource.getMessage(EMAIL_NOT_REGISTERED_ERR_TITLE_CODE, null, Locale.getDefault());
            String errorMessage = messageSource.getMessage(EMAIL_NOT_REGISTERED_ERR_MSG_CODE, null, Locale.getDefault());

            throw new BadRequestException(errorTitle, errorMessage);
        }

        StaffDto staffDto = existedEmail.get();

        if (staffDto.isFirstLogin()) {
            return firstTimeLogin(email);
        }

        StaffLoginAuditDto staffLoginAuditDto = staffLoginAuditService.getStaffLoginAuditById(staffDto.getId());

        if (staffDto.getAccountStatus() != StaffAccountStatus.ACTIVE || staffLoginAuditDto.getLoginFailedAttempts() >= MAX_FAILED_ATTEMPTS) {
            String errorTitle = messageSource.getMessage(ACCOUNT_BLOCKED_ERR_TITLE_CODE, null, Locale.getDefault());
            String errorMessage = messageSource.getMessage(ACCOUNT_BLOCKED_ERR_MSG_CODE, null, Locale.getDefault());
            throw new ForbiddenRequestException(errorTitle, errorMessage);
        }

        if (staffLoginAuditDto.getLastForgotPasswordAt() != null) {
            OffsetDateTime now = OffsetDateTime.now();
            OffsetDateTime lastResetPasswordAt = staffLoginAuditDto.getLastForgotPasswordAt();

            long hours = ChronoUnit.HOURS.between(lastResetPasswordAt, now);
            if (hours >= ONE_DAY_IN_HOURS) { // Check Reset Password Email Sending Limit within the Same Day
                staffLoginAuditDto.setForgotPasswordAttempts(0); // Reset attempts after 24 hours
            } else if (staffLoginAuditDto.getForgotPasswordAttempts() >= MAX_RESET_PASSWORD_ATTEMPTS) {
                String errorTitle = messageSource.getMessage(OTP_EXCEED_DAY_LIMIT_ERR_TITLE_CODE, null, Locale.getDefault());
                String errorMessage = messageSource.getMessage(OTP_EXCEED_DAY_LIMIT_ERR_MSG_CODE, null, Locale.getDefault());

                throw new ForbiddenRequestException(errorTitle, errorMessage);
            }
        }

        if (staffLoginAuditDto.getLastForgotPasswordAt() != null) {
            OffsetDateTime now = OffsetDateTime.now();
            OffsetDateTime lastResetPasswordAt = staffLoginAuditDto.getLastForgotPasswordAt();

            long minutes = ChronoUnit.MINUTES.between(lastResetPasswordAt, now);

            if (minutes <= MINUTE_BEFORE_OTP_RESENT) {
                String errorTitle = messageSource.getMessage(OTP_EXCEED_MINUTE_LIMIT_ERR_TITLE_CODE, null, Locale.getDefault());
                String errorMessage = messageSource.getMessage(OTP_EXCEED_MINUTE_LIMIT_ERR_MSG_CODE, null, Locale.getDefault());

                throw new ForbiddenRequestException(errorTitle, errorMessage);
            }

        }

        String otp = authenticationService.generateOtp(staffDto, OtpPurpose.PASSWORD_RESET);

        this.emailService.sendPasswordResetEmail(email, otp, Locale.getDefault()); // async to avoid causing fe blocker

        staffLoginAuditDto.setForgotPasswordAttempts(staffLoginAuditDto.getForgotPasswordAttempts() + 1);
        staffLoginAuditDto.setLastForgotPasswordAt(OffsetDateTime.now());

        staffLoginAuditService.updateStaffLoginAudit(staffDto.getId(), staffLoginAuditDto);

        return ResponseEntity.ok(Map.of(
                "message", messageSource.getMessage(RESET_PASSWORD_EMAIL_OK, null, Locale.getDefault())
        ));
    }

    @PostMapping("/reset-password")
    public ResponseEntity<?> resetPassword(@RequestBody ResetRequest req) throws Exception {
        if (validationService.isNullOrBlank(req.getEmail()) || validationService.isNullOrBlank(req.getOtp())
                || validationService.isNullOrBlank(req.getPassword())) {
            String errorMessage = messageSource.getMessage(INVALID_REQUEST_BODY_ERR_MSG_CODE, new String[]{RESET_PASSWORD_OPERATION}, Locale.getDefault());

            throw new BadRequestException(errorMessage);
        }

        String email = req.getEmail().trim().toLowerCase();
        String otp = req.getOtp().trim();
        String newPassword = req.getPassword();

        boolean validPassword = newPassword.matches(PASSWORD_REGEX);

        if (!validPassword) {
            String errorTitle = messageSource.getMessage(INVALID_PASSWORD_FORMAT_ERR_TITLE_CODE, null, Locale.getDefault());
            String errorMessage = messageSource.getMessage(INVALID_PASSWORD_FORMAT_ERR_MSG_CODE, null, Locale.getDefault());

            throw new BadRequestException(errorTitle, errorMessage);
        }

        Optional<StaffDto> existedEmail = this.staffService.findByIsDeletedIsFalseAndEmail(email);

        if (existedEmail.isEmpty()) {
            String errorTitle = messageSource.getMessage(EMAIL_NOT_REGISTERED_ERR_TITLE_CODE, null, Locale.getDefault());
            String errorMessage = messageSource.getMessage(EMAIL_NOT_REGISTERED_ERR_MSG_CODE, null, Locale.getDefault());
            throw new BadRequestException(errorTitle, errorMessage);
        }

        StaffDto staffDto = existedEmail.get();
        StaffLoginAuditDto staffLoginAuditDto = staffLoginAuditService.getStaffLoginAuditById(staffDto.getId());

        if (staffDto.getAccountStatus() != StaffAccountStatus.ACTIVE || staffLoginAuditDto.getLoginFailedAttempts() >= MAX_FAILED_ATTEMPTS) {
            String errorTitle = messageSource.getMessage(ACCOUNT_BLOCKED_ERR_TITLE_CODE, null, Locale.getDefault());
            String errorMessage = messageSource.getMessage(ACCOUNT_BLOCKED_ERR_MSG_CODE, null, Locale.getDefault());
            throw new ForbiddenRequestException(errorTitle, errorMessage);
        }

        boolean isValidated = false;

        if (staffDto.isFirstLogin()) {
            isValidated = authenticationService.validateOtp(staffDto, otp, OtpPurpose.ACCOUNT_ACTIVATION);
        } else {
            isValidated = authenticationService.validateOtp(staffDto, otp, OtpPurpose.PASSWORD_RESET);
        }

        if (!isValidated) {
            String errorMessage = messageSource.getMessage(FORBIDDEN_ERR_MSG_CODE, null, Locale.getDefault());

            throw new ForbiddenRequestException(errorMessage);
        }

        UUID userId = staffDto.getId();
        authenticationService.updateUserPasswordByUserId(staffDto, newPassword, userId);

        if (staffDto.isFirstLogin()) {
            staffDto.setFirstLogin(false);
            staffService.update(userId, staffDto);
        }

        staffLoginAuditDto.setLastResetPasswordAt(OffsetDateTime.now());
        staffLoginAuditService.updateStaffLoginAudit(userId, staffLoginAuditDto);

        return ResponseEntity.ok(Map.of(
                "message", messageSource.getMessage(RESET_PASSWORD_UPDATE_OK, null, Locale.getDefault())
        ));
    }

    @PostMapping("/first-time-login")
    public ResponseEntity<?> firstTimeLogin(@RequestParam String email) throws Exception {
        if (validationService.isNullOrBlank(email)) {
            String errorMessage = messageSource.getMessage(INVALID_REQUEST_BODY_ERR_MSG_CODE, new String[]{FIRST_TIME_LOGIN_OPERATION}, Locale.getDefault());

            throw new BadRequestException(errorMessage);
        }

        email = email.trim().toLowerCase();

        Optional<StaffDto> existedEmail = this.staffService.findByIsDeletedIsFalseAndEmail(email);

        if (existedEmail.isEmpty()) {
            String errorTitle = messageSource.getMessage(EMAIL_NOT_REGISTERED_ERR_TITLE_CODE, null, Locale.getDefault());
            String errorMessage = messageSource.getMessage(EMAIL_NOT_REGISTERED_ERR_MSG_CODE, null, Locale.getDefault());

            throw new BadRequestException(errorTitle, errorMessage);
        }

        StaffDto staffDto = existedEmail.get();

        if (!staffDto.isFirstLogin()) {
            String errorTitle = messageSource.getMessage(NOT_FIRST_TIME_LOGIN_ERR_TITLE_CODE, null, Locale.getDefault());
            String errorMessage = messageSource.getMessage(NOT_FIRST_TIME_LOGIN_ERR_MSG_CODE, null, Locale.getDefault());
            throw new BadRequestException(errorTitle, errorMessage);
        }

        // Create Refresh Token Record
        StaffRefreshTokenDto staffRefreshTokenDto = new StaffRefreshTokenDto();
        staffRefreshTokenDto.setStaffId(staffDto.getId());

        staffRefreshTokenService.createStaffRefreshToken(staffRefreshTokenDto);

        // Create Login Audit Record
        StaffLoginAuditDto staffLoginAuditDtoToBeCreated = new StaffLoginAuditDto();
        staffLoginAuditDtoToBeCreated.setStaffId(staffDto.getId());

        StaffLoginAuditDto staffLoginAuditDto = staffLoginAuditService.createStaffLoginAudit(staffLoginAuditDtoToBeCreated);

        if (staffDto.getAccountStatus() != StaffAccountStatus.ACTIVE || staffLoginAuditDto.getLoginFailedAttempts() >= MAX_FAILED_ATTEMPTS) {
            String errorTitle = messageSource.getMessage(ACCOUNT_BLOCKED_ERR_TITLE_CODE, null, Locale.getDefault());
            String errorMessage = messageSource.getMessage(ACCOUNT_BLOCKED_ERR_MSG_CODE, null, Locale.getDefault());
            throw new ForbiddenRequestException(errorTitle, errorMessage);
        }

        if (staffLoginAuditDto.getLastForgotPasswordAt() != null && staffLoginAuditDto.getForgotPasswordAttempts() >= MAX_RESET_PASSWORD_ATTEMPTS) {
            OffsetDateTime now = OffsetDateTime.now();
            OffsetDateTime lastResetPasswordAt = staffLoginAuditDto.getLastForgotPasswordAt();

            long hours = ChronoUnit.HOURS.between(lastResetPasswordAt, now);
            if (hours >= ONE_DAY_IN_HOURS) { // Check Reset Password Email Sending Limit within the Same Day
                staffLoginAuditDto.setForgotPasswordAttempts(0); // Reset attempts after 24 hours
            } else {
                String errorTitle = messageSource.getMessage(OTP_EXCEED_DAY_LIMIT_ERR_TITLE_CODE, null, Locale.getDefault());
                String errorMessage = messageSource.getMessage(OTP_EXCEED_DAY_LIMIT_ERR_MSG_CODE, null, Locale.getDefault());

                throw new ForbiddenRequestException(errorTitle, errorMessage);
            }
        }

        if (staffLoginAuditDto.getLastForgotPasswordAt() != null) {
            OffsetDateTime now = OffsetDateTime.now();
            OffsetDateTime lastResetPasswordAt = staffLoginAuditDto.getLastForgotPasswordAt();

            long minutes = ChronoUnit.MINUTES.between(lastResetPasswordAt, now);

            if (minutes <= MINUTE_BEFORE_OTP_RESENT) {
                String errorTitle = messageSource.getMessage(OTP_EXCEED_MINUTE_LIMIT_ERR_TITLE_CODE, null, Locale.getDefault());
                String errorMessage = messageSource.getMessage(OTP_EXCEED_MINUTE_LIMIT_ERR_MSG_CODE, null, Locale.getDefault());

                throw new ForbiddenRequestException(errorTitle, errorMessage);
            }

        }

        String otp = authenticationService.generateOtp(staffDto, OtpPurpose.ACCOUNT_ACTIVATION);

        this.emailService.sendAccountActivationEmail(email, otp, Locale.getDefault()); // async to avoid causing fe blocker

        staffLoginAuditDto.setForgotPasswordAttempts(staffLoginAuditDto.getForgotPasswordAttempts() + 1);
        staffLoginAuditDto.setLastForgotPasswordAt(OffsetDateTime.now());

        staffLoginAuditService.updateStaffLoginAudit(staffDto.getId(), staffLoginAuditDto);

        return ResponseEntity.ok(Map.of(
                "message", messageSource.getMessage(RESET_PASSWORD_EMAIL_OK, null, Locale.getDefault())
        ));
    }

    private List<GrantedAuthority> getGrantedAuthoritiesList(StaffDto staffDto) {
        RoleDto role = staffDto.getRole();
        List<RoleAuthorityDto> roleAuthorityDtoList = roleAuthorityService.getAllByRoleId(role.getId());

        List<GrantedAuthority> authorities = new ArrayList<>();

        for (RoleAuthorityDto roleAuthorityDto : roleAuthorityDtoList) {
            AuthorityDto authorityDto = authorityService.findById(roleAuthorityDto.getAuthority().getId());
            SimpleGrantedAuthority simpleGrantedAuthority = new SimpleGrantedAuthority(authorityDto.getName().getAuthorityName());
            authorities.add(simpleGrantedAuthority);
        }

        return authorities;
    }
}