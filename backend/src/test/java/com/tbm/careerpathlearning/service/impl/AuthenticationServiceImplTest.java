package com.tbm.careerpathlearning.service.impl;

import com.tbm.careerpathlearning.dto.StaffDto;
import com.tbm.careerpathlearning.dto.StaffLoginAuditDto;
import com.tbm.careerpathlearning.dto.StaffRefreshTokenDto;
import com.tbm.careerpathlearning.enums.StaffAccountStatus;
import com.tbm.careerpathlearning.exception.ForbiddenRequestException;
import com.tbm.careerpathlearning.service.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.MessageSource;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Locale;
import java.util.UUID;

import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(MockitoExtension.class)
public class AuthenticationServiceImplTest {

    @Mock
    private MessageSource messageSource;

    @Mock
    private StaffService staffService;

    @Mock
    private StaffOtpService staffOtpService;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private TokenService tokenService;

    @Mock
    private StaffRefreshTokenService staffRefreshTokenService;

    @Mock
    private StaffLoginAuditService staffLoginAuditService;

    @InjectMocks
    private AuthenticationServiceImpl authenticationService;

    @BeforeEach
    void setUp() {
        // Since we are not loading Spring Context, @Value fields are null/0.
        // We set them manually using ReflectionTestUtils.
        ReflectionTestUtils.setField(authenticationService, "MAX_FAILED_ATTEMPTS", 5);
        ReflectionTestUtils.setField(authenticationService, "COOKIES_EXPIRY_DAYS", 7);
    }

    // ==========================================================
    // TEST CASE: Login Functionality
    // ==========================================================

    @Test
    void loginWithEmailAndPassword_ShouldReturnTrue_WhenPasswordMatches() {
        // Arrange
        String rawPassword = "password123";
        StaffDto staffDto = new StaffDto();
        staffDto.setPassword("encodedPassword");
        StaffLoginAuditDto auditDto = new StaffLoginAuditDto();

        when(passwordEncoder.matches(rawPassword, staffDto.getPassword())).thenReturn(true);

        // Act
        boolean result = authenticationService.loginWithEmailAndPassword(auditDto, staffDto, rawPassword);

        // Assert
        assertTrue(result);
        verify(staffLoginAuditService, never()).updateStaffLoginAudit(any(), any());
    }

    @Test
    void loginWithEmailAndPassword_ShouldThrowException_WhenPasswordWrong() {
        // Arrange
        String rawPassword = "wrongPassword";
        StaffDto staffDto = new StaffDto();
        staffDto.setPassword("encodedPassword");
        staffDto.setId(UUID.randomUUID());

        StaffLoginAuditDto auditDto = new StaffLoginAuditDto();
        auditDto.setStaffId(staffDto.getId());
        auditDto.setLoginFailedAttempts(0);

        when(passwordEncoder.matches(rawPassword, staffDto.getPassword())).thenReturn(false);

        when(messageSource.getMessage(eq("invalid.credentials.err.title"), any(), any(Locale.class))).thenReturn("Error");
        when(messageSource.getMessage(eq("invalid.credentials.err.msg"), any(), any(Locale.class))).thenReturn("Invalid credentials");

        // Act & Assert
        ForbiddenRequestException exception = assertThrows(ForbiddenRequestException.class, () -> {
            authenticationService.loginWithEmailAndPassword(auditDto, staffDto, rawPassword);
        });

        assertEquals("Invalid credentials", exception.getMessage());

        // Verify incremented the failure count
        assertEquals(1, auditDto.getLoginFailedAttempts());
        verify(staffLoginAuditService).updateStaffLoginAudit(eq(staffDto.getId()), eq(auditDto));
    }

    @Test
    void loginWithEmailAndPassword_ShouldBlockAccount_WhenMaxAttemptsReached() {
        // Arrange
        String rawPassword = "wrongPassword";
        StaffDto staffDto = new StaffDto();
        staffDto.setId(UUID.randomUUID());
        staffDto.setPassword("encodedPassword");

        StaffLoginAuditDto auditDto = new StaffLoginAuditDto();
        auditDto.setStaffId(staffDto.getId());
        auditDto.setLoginFailedAttempts(4); // ALREADY FAILED TWICE (Max is 5)

        when(passwordEncoder.matches(rawPassword, staffDto.getPassword())).thenReturn(false);

        // Mock the "Blocked" error message
        when(messageSource.getMessage(eq("account.blocked.err.title"), any(), any(Locale.class))).thenReturn("Blocked");
        when(messageSource.getMessage(eq("account.blocked.err.msg"), any(), any(Locale.class))).thenReturn("Account Blocked");

        // Act & Assert
        ForbiddenRequestException exception = assertThrows(ForbiddenRequestException.class, () -> {
            authenticationService.loginWithEmailAndPassword(auditDto, staffDto, rawPassword);
        });

        assertEquals("Account Blocked", exception.getMessage());

        // Verify Account Status was set to INACTIVE
        assertEquals(StaffAccountStatus.INACTIVE, staffDto.getAccountStatus());
        verify(staffService).update(eq(staffDto.getId()), eq(staffDto));
    }

    // ==========================================================
    // TEST CASE: Password Update
    // ==========================================================

    @Test
    void updateUserPasswordByUserId_ShouldEncodeAndUpdate() {
        // Arrange
        UUID staffId = UUID.randomUUID();
        StaffDto staffDto = new StaffDto();
        String newPassword = "NewPassword123";

        when(passwordEncoder.encode(newPassword)).thenReturn("EncodedNewPassword");

        // Act
        authenticationService.updateUserPasswordByUserId(staffDto, newPassword, staffId);

        // Assert
        assertEquals("EncodedNewPassword", staffDto.getPassword()); // Verify it was encoded
        assertEquals(staffId, staffDto.getUpdatedBy());
        assertNotNull(staffDto.getUpdatedAt());
        verify(staffService).update(eq(staffId), eq(staffDto));
    }

    // ==========================================================
    // TEST CASE: Refresh Token
    // ==========================================================

    @Test
    void createAndSaveRefreshToken_ShouldGenerateAndSave() {
        // Arrange
        UUID userId = UUID.randomUUID();
        String expectedToken = "xyz-refresh-token";

        StaffRefreshTokenDto mockTokenDto = new StaffRefreshTokenDto();

        when(tokenService.generateRefreshToken()).thenReturn(expectedToken);
        when(staffRefreshTokenService.getStaffRefreshTokenById(userId)).thenReturn(mockTokenDto);

        // Act
        String result = authenticationService.createAndSaveRefreshToken(userId);

        // Assert
        assertEquals(expectedToken, result);
        assertEquals(expectedToken, mockTokenDto.getToken());
        assertNotNull(mockTokenDto.getExpiresAt());
        verify(staffRefreshTokenService).updateStaffRefreshToken(eq(userId), eq(mockTokenDto));
    }
}
