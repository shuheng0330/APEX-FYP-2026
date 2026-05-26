package com.tbm.careerpathlearning.model;

import jakarta.persistence.*;
import lombok.Data;
import org.springframework.beans.factory.annotation.Value;

import java.time.OffsetDateTime;
import java.util.UUID;

@Data
@Entity
@Table(name = "staff_refresh_token ")
public class StaffRefreshToken {

    @Id
    @Column(name = "staff_id")
    private UUID staffId;

    @Column(length = 255)
    private String token;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "expires_at", nullable = false)
    private OffsetDateTime expiresAt;

    public StaffRefreshToken() {
    }

    public UUID getStaffId() {
        return staffId;
    }

    public void setStaffId(UUID staffId) {
        this.staffId = staffId;
    }

    public String getToken() {
        return token;
    }

    public void setToken(String token) {
        this.token = token;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(OffsetDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public OffsetDateTime getExpiresAt() {
        return expiresAt;
    }

    public void setExpiresAt(OffsetDateTime expiryAt) {
        this.expiresAt = expiryAt;
    }

    @PrePersist
    protected void onCreate() {
        if (this.createdAt == null) {
            this.createdAt = OffsetDateTime.now();
        }

        if (this.expiresAt == null) {
            this.expiresAt = OffsetDateTime.now().plusDays(7);
        }
    }
}
