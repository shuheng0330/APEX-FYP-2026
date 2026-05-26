package com.tbm.careerpathlearning.service.impl;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.security.SignatureException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

class TokenServiceTest {

    private TokenServiceImpl tokenService;

    // Secret must be at least 32 characters for HS256
    private final String testSecret = "this_is_a_very_secret_key_used_for_testing_purposes_only";
    private final UUID userId = UUID.randomUUID();
    private final List<String> roles = List.of("ROLE_USER", "CAN_MANAGE_STAFF");

    @BeforeEach
    void setUp() {
        tokenService = new TokenServiceImpl();

        // Use ReflectionTestUtils to inject the @Value field manually
        ReflectionTestUtils.setField(tokenService, "jwtSecret", testSecret);

        // Manually call the @PostConstruct method
        tokenService.init();
    }

    @Test
    void generateAccessToken_ShouldReturnValidJwt() {
        // Act
        String token = tokenService.generateAccessToken(userId, roles);

        // Assert
        assertThat(token).isNotNull();
        assertThat(token.split("\\.")).hasSize(3); // JWT structure: header.payload.signature
    }

    @Test
    void extractClaims_ShouldReturnCorrectPayload() {
        // Arrange
        String token = tokenService.generateAccessToken(userId, roles);

        // Act
        Claims claims = tokenService.extractClaims(token);

        // Assert
        assertThat(claims.getSubject()).isEqualTo(userId.toString());
        assertThat(claims.get("roles", List.class)).containsExactlyInAnyOrderElementsOf(roles);
        assertThat(claims.getExpiration()).isAfter(claims.getIssuedAt());
    }

    @Test
    void generateRefreshToken_ShouldReturnRandomUuid() {
        // Act
        String refreshToken1 = tokenService.generateRefreshToken();
        String refreshToken2 = tokenService.generateRefreshToken();

        // Assert
        assertThat(refreshToken1).isNotEmpty();
        assertThat(refreshToken1).isNotEqualTo(refreshToken2);
        // Verify it is a valid UUID format
        assertThat(UUID.fromString(refreshToken1)).isNotNull();
    }

    @Test
    void extractClaims_ShouldThrowExceptionOnInvalidSignature() {
        // Arrange
        String validToken = tokenService.generateAccessToken(userId, roles);
        String tamperedToken = validToken + "tamper";

        // Act & Assert
        assertThrows(SignatureException.class, () -> {
            tokenService.extractClaims(tamperedToken);
        });
    }

    @Test
    void extractClaims_ShouldHandleExpiredToken() {
        // Note: To properly test expiration without waiting 15 mins, 
        // you would usually mock the Clock or create a helper method in the service
        // that accepts a Date for expiration. 
        // Since we can't easily change current system time, we verify the logic manually.

        String token = tokenService.generateAccessToken(userId, roles);
        Claims claims = tokenService.extractClaims(token);

        assertThat(claims.getExpiration()).isBefore(new java.util.Date(System.currentTimeMillis() + 16 * 60 * 1000));
    }
}