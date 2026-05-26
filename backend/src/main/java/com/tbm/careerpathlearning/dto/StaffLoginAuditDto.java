package com.tbm.careerpathlearning.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;
import java.util.UUID;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class StaffLoginAuditDto {

    private UUID staffId;
    private OffsetDateTime lastLoginAt = null;
    private OffsetDateTime lastLoginFailedAt = null;
    private int loginFailedAttempts = 0;
    private OffsetDateTime lastForgotPasswordAt = null;
    private int forgotPasswordAttempts = 0;
    private OffsetDateTime lastResetPasswordAt = null;

    public UUID getStaffId() {
        return staffId;
    }

    public void setStaffId(UUID staffId) {
        this.staffId = staffId;
    }

    public OffsetDateTime getLastLoginAt() {
        return lastLoginAt;
    }

    public void setLastLoginAt(OffsetDateTime lastLoginAt) {
        this.lastLoginAt = lastLoginAt;
    }

    public OffsetDateTime getLastLoginFailedAt() {
        return lastLoginFailedAt;
    }

    public void setLastLoginFailedAt(OffsetDateTime lastLoginFailedAt) {
        this.lastLoginFailedAt = lastLoginFailedAt;
    }

    public int getLoginFailedAttempts() {
        return loginFailedAttempts;
    }

    public void setLoginFailedAttempts(int loginFailedAttempts) {
        this.loginFailedAttempts = loginFailedAttempts;
    }

    public OffsetDateTime getLastForgotPasswordAt() {
        return lastForgotPasswordAt;
    }

    public void setLastForgotPasswordAt(OffsetDateTime lastForgotPasswordAt) {
        this.lastForgotPasswordAt = lastForgotPasswordAt;
    }

    public int getForgotPasswordAttempts() {
        return forgotPasswordAttempts;
    }

    public void setForgotPasswordAttempts(int forgotPasswordAttempts) {
        this.forgotPasswordAttempts = forgotPasswordAttempts;
    }

    public OffsetDateTime getLastResetPasswordAt() {
        return lastResetPasswordAt;
    }

    public void setLastResetPasswordAt(OffsetDateTime lastResetPasswordAt) {
        this.lastResetPasswordAt = lastResetPasswordAt;
    }
}
