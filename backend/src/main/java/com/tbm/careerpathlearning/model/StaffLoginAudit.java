package com.tbm.careerpathlearning.model;

import jakarta.persistence.*;
import lombok.Data;

import java.time.OffsetDateTime;
import java.util.UUID;

@Data
@Entity
@Table(name = "staff_login_audit")
public class StaffLoginAudit {

    @Id
    @Column(name = "staff_id")
    private UUID staffId;

    @Column(name = "last_login_at")
    private OffsetDateTime lastLoginAt;

    @Column(name = "last_login_failed_at")
    private OffsetDateTime lastLoginFailedAt;

    @Column(name = "login_failed_attempts", nullable = false)
    private int loginFailedAttempts;

    @Column(name = "last_forgot_password_at")
    private OffsetDateTime lastForgotPasswordAt;

    @Column(name = "forgot_password_attempts", nullable = false)
    private int forgotPasswordAttempts;

    @Column(name = "last_reset_password_at")
    private OffsetDateTime lastResetPasswordAt;

    public StaffLoginAudit() {
    }

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
