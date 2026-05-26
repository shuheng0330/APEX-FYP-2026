package com.tbm.careerpathlearning.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tbm.careerpathlearning.dto.LoginRequest;
import com.tbm.careerpathlearning.dto.ResetRequest;
import com.tbm.careerpathlearning.enums.AuthorityName;
import com.tbm.careerpathlearning.enums.OtpPurpose;
import com.tbm.careerpathlearning.enums.StaffAccountStatus;
import com.tbm.careerpathlearning.model.*;
import com.tbm.careerpathlearning.repository.*;
import com.tbm.careerpathlearning.service.EmailService;
import com.tbm.careerpathlearning.service.TokenService;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.Locale;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
public class AuthIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private StaffRepository staffRepository;

    @Autowired
    private StaffLoginAuditRepository staffLoginAuditRepository;

    @Autowired
    private StaffOtpRepository staffOtpRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private OrgChartRepository orgChartRepository;

    @Autowired
    private AuthorityRepository authorityRepository;

    @Autowired
    private RoleAuthorityRepository roleAuthorityRepository;

    @Autowired
    private StaffRefreshTokenRepository staffRefreshTokenRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @MockitoBean
    private EmailService emailService;

    @Autowired
    private TokenService tokenService;

    private Staff activeStaff;
    private Staff firstTimeStaff;
    private final String PASSWORD = "Password123";

    @BeforeEach
    void setUp() {
        // 1. Setup Dependencies (Org Chart & Role) - Required for Staff
        OrgChart org = new OrgChart();
        org.setName("IT Dept");
        org.setDeleted(false);
        org.setCreatedAt(OffsetDateTime.now());
        org.setUpdatedAt(OffsetDateTime.now());
        orgChartRepository.save(org);

        Authority auth = new Authority();
        auth.setName(AuthorityName.ROLE_USER);
        auth.setLabelKey("label");
        auth.setDescriptionKey("desc");
        Authority createdAuth = authorityRepository.save(auth);

        Role role = new Role();
        role.setName("Developer");
        role.setOrgChart(org);
        role.setDeleted(false);
        role.setVisible(true);
        role.setCreatedAt(OffsetDateTime.now());
        role.setUpdatedAt(OffsetDateTime.now());
        Role createdRole = roleRepository.save(role);

        RoleAuthority ra = new RoleAuthority();
        ra.setId(new RoleAuthorityId(createdRole.getId(), createdAuth.getId()));
        ra.setRole(createdRole);
        ra.setAuthority(createdAuth);
        roleAuthorityRepository.save(ra);

        // 2. Setup Active User
        activeStaff = new Staff();
        activeStaff.setId(UUID.randomUUID());
        activeStaff.setName("Active User");
        activeStaff.setEmail("active@tbm.com");
        activeStaff.setPassword(passwordEncoder.encode(PASSWORD));
        activeStaff.setAccountStatus(StaffAccountStatus.ACTIVE);
        activeStaff.setFirstLogin(false);
        activeStaff.setDeleted(false);
        activeStaff.setRole(role);
        activeStaff.setCreatedAt(OffsetDateTime.now());
        activeStaff.setUpdatedAt(OffsetDateTime.now());
        staffRepository.save(activeStaff);
        staffRepository.flush();

        StaffLoginAudit audit = new StaffLoginAudit();
        audit.setStaffId(activeStaff.getId());
        audit.setLoginFailedAttempts(0);
        staffLoginAuditRepository.save(audit);
        staffLoginAuditRepository.flush();

        // 3. Setup First Time Login User
        firstTimeStaff = new Staff();
        firstTimeStaff.setId(UUID.randomUUID());
        firstTimeStaff.setName("Newbie");
        firstTimeStaff.setEmail("new@tbm.com");
        firstTimeStaff.setPassword(passwordEncoder.encode(PASSWORD)); // Temp password
        firstTimeStaff.setAccountStatus(StaffAccountStatus.ACTIVE);
        firstTimeStaff.setFirstLogin(true); // <--- Key flag
        firstTimeStaff.setDeleted(false);
        firstTimeStaff.setRole(role);
        firstTimeStaff.setCreatedAt(OffsetDateTime.now());
        firstTimeStaff.setUpdatedAt(OffsetDateTime.now());
        staffRepository.save(firstTimeStaff);

        // Note: StaffLoginAudit usually created on first login attempt or creation,
        // ensuring it exists for the test logic.
        StaffLoginAudit auditNew = new StaffLoginAudit();
        auditNew.setStaffId(firstTimeStaff.getId());
        auditNew.setLoginFailedAttempts(0);
        staffLoginAuditRepository.save(auditNew);

        StaffRefreshToken refreshToken = new StaffRefreshToken();
        refreshToken.setStaffId(activeStaff.getId());
        refreshToken.setToken(null);
        staffRefreshTokenRepository.save(refreshToken);

        staffRepository.flush();

    }

    // --- Scenario 1: Standard Login ---
    @Test
    void login_ShouldSucceed_WhenCredentialsAreCorrect() throws Exception {
        LoginRequest req = new LoginRequest();
        req.setEmail("active@tbm.com");
        req.setPassword(PASSWORD);

        MvcResult result = mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").exists())
                .andExpect(cookie().exists("sb-refresh"))
                .andReturn();

        // Database Verification (Bottom-Up Assurance)
        StaffLoginAudit audit = staffLoginAuditRepository.findById(activeStaff.getId()).orElseThrow();
        assertThat(audit.getLastLoginAt()).isNotNull();
        assertThat(audit.getLoginFailedAttempts()).isEqualTo(0);
    }

    @Test
    void login_ShouldFail_WhenPasswordIsWrong() throws Exception {
        LoginRequest req = new LoginRequest();
        req.setEmail("active@tbm.com");
        req.setPassword("WrongPass");

        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isForbidden()); // ForbiddenRequestException mapped to 403

    }

    // --- Scenario 2: First Time Login Flow ---
    @Test
    void firstTimeLogin_FullFlow_ShouldActivateAccount() throws Exception {
        // Step 1: Try Normal Login -> Should Fail (400 Bad Request)
        LoginRequest loginReq = new LoginRequest();
        loginReq.setEmail("new@tbm.com");
        loginReq.setPassword(PASSWORD);

        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginReq)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Please complete your first-time login to activate your account."));

        // Step 2: Request Activation OTP
        mockMvc.perform(post("/auth/first-time-login")
                        .param("email", "new@tbm.com"))
                .andExpect(status().isOk());

        // Verify Email Service was called
        verify(emailService).sendAccountActivationEmail(eq("new@tbm.com"), anyString(), any(Locale.class));

        // Retrieve the generated OTP from DB
        StaffOtp otpEntity = staffOtpRepository.findAll().stream()
                .filter(o -> o.getStaff().getId().equals(firstTimeStaff.getId()))
                .filter(o -> o.getOtpPurpose() == OtpPurpose.ACCOUNT_ACTIVATION)
                .findFirst()
                .orElseThrow(() -> new AssertionError("OTP not generated in DB"));

        // Step 3: Reset Password / Activate
        ResetRequest resetReq = new ResetRequest();
        resetReq.setEmail("new@tbm.com");
        resetReq.setOtp(otpEntity.getOtpCode());
        resetReq.setPassword("NewStrongPass1!");

        mockMvc.perform(post("/auth/reset-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(resetReq)))
                .andExpect(status().isOk());

        // Final Database Verification
        Staff updatedStaff = staffRepository.findById(firstTimeStaff.getId()).orElseThrow();
        assertThat(updatedStaff.isFirstLogin()).isFalse(); // Should be active now
        assertThat(passwordEncoder.matches("NewStrongPass1!", updatedStaff.getPassword())).isTrue();
    }

    // --- Scenario 3: Forgot Password Flow ---
    @Test
    void forgotPassword_FullFlow_ShouldResetPassword() throws Exception {
        // Step 1: Request Password Reset
        mockMvc.perform(post("/auth/forgot-password")
                        .param("email", "active@tbm.com"))
                .andExpect(status().isOk());

        // Verify Email Service
        verify(emailService).sendPasswordResetEmail(eq("active@tbm.com"), anyString(), any(Locale.class));

        // Get OTP from DB
        StaffOtp otpEntity = staffOtpRepository.findAll().stream()
                .filter(o -> o.getStaff().getId().equals(activeStaff.getId()))
                .filter(o -> o.getOtpPurpose() == OtpPurpose.PASSWORD_RESET)
                .findFirst()
                .orElseThrow();

        // Step 2: Submit New Password
        ResetRequest resetReq = new ResetRequest();
        resetReq.setEmail("active@tbm.com");
        resetReq.setOtp(otpEntity.getOtpCode());
        resetReq.setPassword("NewPass123!");

        mockMvc.perform(post("/auth/reset-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(resetReq)))
                .andExpect(status().isOk());

        // Step 3: Verify Login works with NEW password
        LoginRequest loginReq = new LoginRequest();
        loginReq.setEmail("active@tbm.com");
        loginReq.setPassword("NewPass123!");

        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginReq)))
                .andExpect(status().isOk());
    }

    // --- Scenario 4: Refresh Token ---
    @Test
    void refresh_ShouldReturnNewToken_WhenCookieValid() throws Exception {

        assertThat(staffRepository.findByIsDeletedIsFalseAndEmail("active@tbm.com")).isPresent();
        assertThat(staffLoginAuditRepository.findById(activeStaff.getId())).isPresent(); // <--- This must pass

        // 1. Login to get a valid refresh cookie
        LoginRequest req = new LoginRequest();
        req.setEmail("active@tbm.com");
        req.setPassword(PASSWORD);

        MvcResult loginResult = mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andDo(print())
                .andReturn();

        Cookie refreshCookie = loginResult.getResponse().getCookie("sb-refresh");
        assertThat(refreshCookie).isNotNull();

        // 2. Call Refresh Endpoint
        mockMvc.perform(post("/auth/refresh")
                        .cookie(refreshCookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").exists());
    }

    // --- Scenario 5: Logout ---
    @Test
    void logout_ShouldClearCookie() throws Exception {
        mockMvc.perform(post("/auth/logout"))
                .andExpect(status().isNoContent()) // 204
                .andExpect(cookie().maxAge("sb-refresh", 0)); // Cookie killed
    }
}