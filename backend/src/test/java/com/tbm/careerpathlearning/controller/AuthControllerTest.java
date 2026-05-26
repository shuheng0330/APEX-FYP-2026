package com.tbm.careerpathlearning.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tbm.careerpathlearning.dto.*;
import com.tbm.careerpathlearning.enums.OtpPurpose;
import com.tbm.careerpathlearning.enums.StaffAccountStatus;
import com.tbm.careerpathlearning.service.*;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.HttpHeaders;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.context.MessageSource;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.OffsetDateTime;
import java.util.*;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

@WebMvcTest(controllers = AuthController.class, properties = {
        "account.max.login.failed.attempts=5",
        "account.max.reset.password.attempts=7",
        "cookies.expiry.day=7",
        "cookies.secure=false"
})
@AutoConfigureMockMvc(addFilters = false)
class AuthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private StaffService staffService;
    @MockitoBean
    private AuthenticationService authenticationService;
    @MockitoBean
    private TokenService tokenService;
    @MockitoBean
    private StaffLoginAuditService staffLoginAuditService;
    @MockitoBean
    private ValidationService validationService;
    @MockitoBean
    private RoleAuthorityService roleAuthorityService;
    @MockitoBean
    private AuthorityService authorityService;
    @MockitoBean
    private StaffRefreshTokenService staffRefreshTokenService;
    @MockitoBean
    private EmailService emailService;
    @MockitoBean
    private MessageSource messageSource;

    // Helper objects for tests
    private StaffDto mockStaff;
    private StaffLoginAuditDto mockAudit;

    @BeforeEach
    void setUp() {
        // 1. Setup Data
        mockStaff = new StaffDto();
        mockStaff.setId(UUID.randomUUID());
        mockStaff.setEmail("staff@test.com");
        mockStaff.setAccountStatus(StaffAccountStatus.ACTIVE);
        mockStaff.setFirstLogin(false);
        RoleDto role = new RoleDto();
        role.setId(1L);
        mockStaff.setRole(role);

        mockAudit = new StaffLoginAuditDto();
        mockAudit.setStaffId(mockStaff.getId());
        mockAudit.setLoginFailedAttempts(0);

        // 2. Setup Message Source Mocks (To avoid null pointer on error messages)
        when(messageSource.getMessage(anyString(), any(), any(Locale.class))).thenReturn("Mock Error Message");

    }

    // --- TEST CASE 1: Successful Login ---
    @Test
    void login_ShouldReturn200_WhenCredentialsAreValid() throws Exception {
        // Arrange
        LoginRequest req = new LoginRequest();
        req.setEmail("staff@test.com");
        req.setPassword("Password123");

        // Mock Validation
        when(validationService.isNullOrBlank(any())).thenReturn(false);
        // Mock Finding User
        when(staffService.findByIsDeletedIsFalseAndEmail(req.getEmail())).thenReturn(Optional.of(mockStaff));
        // Mock Audit
        when(staffLoginAuditService.getStaffLoginAuditById(mockStaff.getId())).thenReturn(mockAudit);
        // Mock Auth Service (Logic we tested previously)
        when(authenticationService.loginWithEmailAndPassword(any(), any(), any())).thenReturn(true);
        // Mock Token Generation
        when(tokenService.generateAccessToken(any(), any())).thenReturn("mock-access-token");
        when(authenticationService.createAndSaveRefreshToken(any())).thenReturn("mock-refresh-token");

        // Act & Assert
        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").value("mock-access-token"))
                .andExpect(jsonPath("$.userId").value(mockStaff.getId().toString()));
    }

    @Test
    void login_ShouldReturn200_WhenUserHasNoAuthorities() throws Exception {
        // Arrange
        LoginRequest req = new LoginRequest();
        req.setEmail("staff@test.com");
        req.setPassword("Password123");

        // Mock empty authorities list
        when(roleAuthorityService.getAllByRoleId(any())).thenReturn(Collections.emptyList());

        when(validationService.isNullOrBlank(any())).thenReturn(false);
        when(staffService.findByIsDeletedIsFalseAndEmail(req.getEmail())).thenReturn(Optional.of(mockStaff));
        when(staffLoginAuditService.getStaffLoginAuditById(mockStaff.getId())).thenReturn(mockAudit);
        when(authenticationService.loginWithEmailAndPassword(any(), any(), any())).thenReturn(true);
        when(tokenService.generateAccessToken(any(), any())).thenReturn("token");
        when(authenticationService.createAndSaveRefreshToken(any())).thenReturn("refresh");

        // Act & Assert
        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.roles").isEmpty()); // Verify roles list is empty
    }

    // --- TEST CASE 3: Unregistered Email ---
    @Test
    void login_ShouldReturn400_WhenEmailNotRegistered() throws Exception {
        // Arrange
        LoginRequest req = new LoginRequest();
        req.setEmail("unknown@test.com");
        req.setPassword("pass");

        when(validationService.isNullOrBlank(any())).thenReturn(false);
        // Mock: Email NOT found
        when(staffService.findByIsDeletedIsFalseAndEmail(req.getEmail())).thenReturn(Optional.empty());

        // Act & Assert
        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest()); // Expect 400
    }

    // --- TEST CASE 4: First Time User ---
    @Test
    void login_ShouldReturn400_WhenUserIsFirstTimeLogin() throws Exception {
        // Arrange
        LoginRequest req = new LoginRequest();
        req.setEmail("new@test.com");
        req.setPassword("pass");

        mockStaff.setFirstLogin(true); // FLAG IS TRUE

        when(validationService.isNullOrBlank(any())).thenReturn(false);
        when(staffService.findByIsDeletedIsFalseAndEmail(req.getEmail())).thenReturn(Optional.of(mockStaff));

        // Act & Assert
        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest()); // 400 Bad Request
    }

    // --- TEST CASE 6: Account Blocked (Boundary Value) ---
    @Test
    void login_ShouldReturn403_WhenMaxAttemptsReached() throws Exception {
        // Arrange
        LoginRequest req = new LoginRequest();
        req.setEmail("staff@test.com");
        req.setPassword("pass");

        // Set failures to 5 (assuming default MAX is 5)
        mockAudit.setLoginFailedAttempts(5);

        when(validationService.isNullOrBlank(any())).thenReturn(false);
        when(staffService.findByIsDeletedIsFalseAndEmail(req.getEmail())).thenReturn(Optional.of(mockStaff));
        when(staffLoginAuditService.getStaffLoginAuditById(mockStaff.getId())).thenReturn(mockAudit);

        // Act & Assert
        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isForbidden()); // 403 Forbidden
    }

    // ==========================================================
    // TEST CASE: Forgot Password
    // ==========================================================

    @Test
    void forgotPassword_ShouldReturn200_WhenEmailValidAndWithinLimits() throws Exception {
        // Arrange
        String email = "staff@test.com";

        when(validationService.isNullOrBlank(email)).thenReturn(false);
        when(staffService.findByIsDeletedIsFalseAndEmail(email)).thenReturn(Optional.of(mockStaff));
        when(staffLoginAuditService.getStaffLoginAuditById(mockStaff.getId())).thenReturn(mockAudit);

        // Mock Rate Limiting (Not exceeded)
        mockAudit.setForgotPasswordAttempts(0);
        mockAudit.setLastForgotPasswordAt(OffsetDateTime.now().minusHours(1)); // Last attempt was 1 hour ago

        when(authenticationService.generateOtp(any(), eq(OtpPurpose.PASSWORD_RESET))).thenReturn("123456");

        when(messageSource.getMessage(eq("reset.password.email.ok"), any(), any(Locale.class)))
                .thenReturn("reset.password.email.ok");

        // Act & Assert
        mockMvc.perform(post("/auth/forgot-password")
                        .param("email", email)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("reset.password.email.ok")); // Matches Mock MessageSource

        // Verify Email Service was called
        verify(emailService).sendPasswordResetEmail(eq(email), eq("123456"), any(Locale.class));
    }

    @Test
    void forgotPassword_ShouldReturn403_WhenDayLimitExceeded() throws Exception {
        // Arrange
        String email = "staff@test.com";

        when(validationService.isNullOrBlank(email)).thenReturn(false);
        when(staffService.findByIsDeletedIsFalseAndEmail(email)).thenReturn(Optional.of(mockStaff));
        when(staffLoginAuditService.getStaffLoginAuditById(mockStaff.getId())).thenReturn(mockAudit);

        // Mock Rate Limit Exceeded (e.g., 7 attempts already made today)
        mockAudit.setForgotPasswordAttempts(7);
        mockAudit.setLastForgotPasswordAt(OffsetDateTime.now().minusMinutes(30)); // Within same day

        // Act & Assert
        mockMvc.perform(post("/auth/forgot-password")
                        .param("email", email)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isForbidden()); // 403 Forbidden
    }

    @Test
    void forgotPassword_ShouldReturn403_WhenMinuteLimitExceeded() throws Exception {
        // Arrange
        String email = "staff@test.com";

        when(validationService.isNullOrBlank(email)).thenReturn(false);
        when(staffService.findByIsDeletedIsFalseAndEmail(email)).thenReturn(Optional.of(mockStaff));
        when(staffLoginAuditService.getStaffLoginAuditById(mockStaff.getId())).thenReturn(mockAudit);

        // Mock: User just requested an OTP 30 seconds ago (Limit is 1 minute)
        mockAudit.setLastForgotPasswordAt(OffsetDateTime.now().minusSeconds(30));

        when(messageSource.getMessage(eq("otp.request.exceed.minute.limit.err.title"), any(), any())).thenReturn("Too Fast");

        // Act & Assert
        mockMvc.perform(post("/auth/forgot-password")
                        .param("email", email)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isForbidden());
    }

    // ==========================================================
    // TEST CASE: First Time Login
    // ==========================================================

    @Test
    void firstTimeLogin_ShouldReturn200_WhenUserIsFirstTime() throws Exception {
        // Arrange
        String email = "new@test.com";
        mockStaff.setFirstLogin(true); // User IS first time login

        when(validationService.isNullOrBlank(email)).thenReturn(false);
        when(staffService.findByIsDeletedIsFalseAndEmail(email)).thenReturn(Optional.of(mockStaff));

        // Mock creation of audit/token records since they don't exist for new users logic
        when(staffLoginAuditService.createStaffLoginAudit(any())).thenReturn(mockAudit);
        when(authenticationService.generateOtp(any(), eq(OtpPurpose.ACCOUNT_ACTIVATION))).thenReturn("654321");

        // Act & Assert
        mockMvc.perform(post("/auth/first-time-login")
                        .param("email", email)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk());

        verify(emailService).sendAccountActivationEmail(eq(email), eq("654321"), any(Locale.class));
    }

    @Test
    void firstTimeLogin_ShouldReturn400_WhenUserIsNotFirstTime() throws Exception {
        // Arrange
        String email = "staff@test.com";
        mockStaff.setFirstLogin(false); // User is NOT first time login

        when(validationService.isNullOrBlank(email)).thenReturn(false);
        when(staffService.findByIsDeletedIsFalseAndEmail(email)).thenReturn(Optional.of(mockStaff));

        // Act & Assert
        mockMvc.perform(post("/auth/first-time-login")
                        .param("email", email)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest()); // Should assume "Not first time login" error
    }

    @Test
    void firstTimeLogin_ShouldReturn403_WhenAccountBlocked() throws Exception {
        // Arrange
        String email = "blocked@test.com";
        mockStaff.setFirstLogin(true);
        mockStaff.setAccountStatus(StaffAccountStatus.INACTIVE); // BLOCKED

        when(validationService.isNullOrBlank(email)).thenReturn(false);
        when(staffService.findByIsDeletedIsFalseAndEmail(email)).thenReturn(Optional.of(mockStaff));
        when(staffLoginAuditService.getStaffLoginAuditById(mockStaff.getId())).thenReturn(mockAudit);

        // Mock message source for error
        when(messageSource.getMessage(eq("account.blocked.err.title"), any(), any())).thenReturn("Blocked");
        when(messageSource.getMessage(eq("account.blocked.err.msg"), any(), any())).thenReturn("Account is inactive");

        // Act & Assert
        mockMvc.perform(post("/auth/first-time-login")
                        .param("email", email)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isForbidden());
    }

    @Test
    void firstTimeLogin_ShouldReturn403_WhenDayLimitExceeded() throws Exception {
        // Arrange
        String email = "new@test.com";
        mockStaff.setFirstLogin(true);

        when(validationService.isNullOrBlank(email)).thenReturn(false);
        when(staffService.findByIsDeletedIsFalseAndEmail(email)).thenReturn(Optional.of(mockStaff));

        // Mock Audit: Exceeded attempts
        mockAudit.setForgotPasswordAttempts(7); // Max is 7
        mockAudit.setLastForgotPasswordAt(OffsetDateTime.now().minusMinutes(30)); // Today

        // Need to mock finding the audit (usually created or found)
        // Note: Your controller logic for firstTimeLogin calls createStaffLoginAudit THEN checks limit.
        when(staffLoginAuditService.createStaffLoginAudit(any())).thenReturn(mockAudit);

        when(messageSource.getMessage(eq("otp.request.exceed.day.limit.err.title"), any(), any())).thenReturn("Limit Exceeded");

        // Act & Assert
        mockMvc.perform(post("/auth/first-time-login")
                        .param("email", email)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isForbidden());
    }

    // ==========================================================
    // TEST CASE: Reset Password (The actual update)
    // ==========================================================

    @Test
    void resetPassword_ShouldReturn200_WhenOtpAndFormatValid() throws Exception {
        // Arrange
        ResetRequest req = new ResetRequest();
        req.setEmail("staff@test.com");
        req.setOtp("123456");
        req.setPassword("ValidPass123"); // Meets Regex: Uppercase, Lowercase, Number, 6+ chars

        when(validationService.isNullOrBlank(any())).thenReturn(false);
        when(staffService.findByIsDeletedIsFalseAndEmail(req.getEmail())).thenReturn(Optional.of(mockStaff));
        when(staffLoginAuditService.getStaffLoginAuditById(mockStaff.getId())).thenReturn(mockAudit);

        // Mock OTP Validation Success
        when(authenticationService.validateOtp(any(), eq("123456"), eq(OtpPurpose.PASSWORD_RESET))).thenReturn(true);

        // Act & Assert
        mockMvc.perform(post("/auth/reset-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk());

        // Verify password update service was called
        verify(authenticationService).updateUserPasswordByUserId(any(), eq("ValidPass123"), eq(mockStaff.getId()));
    }

    @Test
    void resetPassword_ShouldReturn400_WhenPasswordFormatInvalid() throws Exception {
        // Arrange
        ResetRequest req = new ResetRequest();
        req.setEmail("staff@test.com");
        req.setOtp("123456");
        req.setPassword("weak"); // FAIL REGEX (Too short, no uppercase, no number)

        when(validationService.isNullOrBlank(any())).thenReturn(false);

        // Act & Assert
        mockMvc.perform(post("/auth/reset-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest()); // 400 Bad Request

        // Verify we NEVER called the update service
        verify(authenticationService, never()).updateUserPasswordByUserId(any(), any(), any());
    }

    @Test
    void resetPassword_ShouldReturn403_WhenOtpIsInvalid() throws Exception {
        // Arrange
        ResetRequest req = new ResetRequest();
        req.setEmail("staff@test.com");
        req.setOtp("WRONG_OTP");
        req.setPassword("ValidPass123");

        when(validationService.isNullOrBlank(any())).thenReturn(false);
        when(staffService.findByIsDeletedIsFalseAndEmail(req.getEmail())).thenReturn(Optional.of(mockStaff));
        when(staffLoginAuditService.getStaffLoginAuditById(mockStaff.getId())).thenReturn(mockAudit);

        // Mock OTP Service returning FALSE
        when(authenticationService.validateOtp(any(), eq("WRONG_OTP"), any())).thenReturn(false);

        // Mock the error message
        when(messageSource.getMessage(eq("forbidden.request.err.msg"), any(), any())).thenReturn("Invalid OTP");
        when(validationService.isNullOrBlank(any())).thenReturn(false);

        // Act & Assert
        mockMvc.perform(post("/auth/reset-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isForbidden());
    }

    // ==========================================================
    // TEST CASE: Logout
    // ==========================================================

    @Test
    void logout_ShouldReturn204_AndClearCookie() throws Exception {
        // Act & Assert
        mockMvc.perform(post("/auth/logout"))
                .andExpect(status().isNoContent()) // Expect 204 No Content
                // Verify the "Set-Cookie" header exists and tries to expire the cookie (Max-Age=0)
                .andExpect(result -> {
                    String cookieHeader = result.getResponse().getHeader(HttpHeaders.SET_COOKIE);
                    if (cookieHeader == null || !cookieHeader.contains("Max-Age=0")) {
                        throw new AssertionError("Response should contain a cookie with Max-Age=0 to clear it");
                    }
                });
    }

    // ==========================================================
    // TEST CASE: Refresh Token (NFR-001 Secure Authentication)
    // ==========================================================

    @Test
    void refresh_ShouldReturn200_WhenTokenValid() throws Exception {
        // Arrange
        String oldToken = "valid-refresh-token";
        String newToken = "new-refresh-token";
        UUID userId = mockStaff.getId();

        // Prepare Mock DB Record
        StaffRefreshTokenDto tokenDto = new StaffRefreshTokenDto();
        tokenDto.setStaffId(userId);
        tokenDto.setToken(oldToken);
        tokenDto.setExpiresAt(OffsetDateTime.now().plusDays(1)); // Valid (Future)

        // Mock Service Calls
        when(staffRefreshTokenService.getStaffRefreshTokenByToken(oldToken)).thenReturn(Optional.of(tokenDto));
        when(authenticationService.createAndSaveRefreshToken(userId)).thenReturn(newToken);
        when(staffService.findById(userId)).thenReturn(mockStaff);
        when(tokenService.generateAccessToken(any(), any())).thenReturn("new-access-token");

        // Act & Assert
        mockMvc.perform(post("/auth/refresh")
                        .cookie(new Cookie("sb-refresh", oldToken))
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").value("new-access-token"))
                // Verify Security: New cookie is HTTP-Only and Secure (based on properties)
                .andExpect(result -> {
                    String cookie = result.getResponse().getHeader(HttpHeaders.SET_COOKIE);
                    if (cookie == null || !cookie.contains("HttpOnly")) {
                        throw new AssertionError("Cookie must be HttpOnly for security");
                    }
                });
    }

    @Test
    void refresh_ShouldReturn403_WhenCookieMissing() throws Exception {
        // Act & Assert (No Cookie sent)
        when(validationService.isNullOrBlank(null)).thenReturn(true);

        mockMvc.perform(post("/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isForbidden()); // Controller throws ForbiddenRequestException
    }

    @Test
    void refresh_ShouldReturn403_WhenTokenExpired() throws Exception {
        // Arrange
        String expiredToken = "expired-token";
        StaffRefreshTokenDto tokenDto = new StaffRefreshTokenDto();
        tokenDto.setExpiresAt(OffsetDateTime.now().minusDays(1)); // Expired (Past)

        when(staffRefreshTokenService.getStaffRefreshTokenByToken(expiredToken)).thenReturn(Optional.of(tokenDto));

        when(validationService.isNullOrBlank(null)).thenReturn(true);

        // Act & Assert
        mockMvc.perform(post("/auth/refresh")
                        .cookie(new Cookie("sb-refresh", expiredToken))
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isForbidden()); // Security check failed
    }

    // ==========================================================
    // TEST CASE: Me Endpoint (NFR-001 Integrity)
    // ==========================================================

    @Test
    void me_ShouldReturn200_WhenAuthenticated() throws Exception {
        // Arrange
        // Create a Mock Authentication Object
        Authentication mockAuth = mock(Authentication.class);
        when(mockAuth.isAuthenticated()).thenReturn(true);
        when(mockAuth.getPrincipal()).thenReturn(mockStaff.getId());
        when(mockAuth.getAuthorities()).thenReturn((Collection) List.of(new SimpleGrantedAuthority("ROLE_USER")));

        // Act & Assert
        mockMvc.perform(get("/auth/me")
                        .principal(mockAuth) // Inject the Security Principal
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value(mockStaff.getId().toString()))
                .andExpect(jsonPath("$.roles[0]").value("ROLE_USER"));
    }

    @Test
    void me_ShouldReturn401_WhenUnauthenticated() throws Exception {
        // Act & Assert (No Principal passed)
        mockMvc.perform(get("/auth/me")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isUnauthorized()); // Controller returns 401
    }
}
