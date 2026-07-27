package com.shopmate.adapter.out.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "picnic_credentials")
public class PicnicCredentialsEntity {

    @Id
    private UUID userId;

    @Column(nullable = false)
    private String email;

    @Column(name = "device_id", nullable = false)
    private String deviceId;

    @Column(name = "auth_key_encrypted", nullable = false)
    private byte[] authKeyEncrypted;

    @Column(name = "status", nullable = false)
    private String status;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected PicnicCredentialsEntity() {}

    public PicnicCredentialsEntity(UUID userId, String email, String deviceId, byte[] authKeyEncrypted,
                                    String status, Instant createdAt, Instant updatedAt) {
        this.userId = userId;
        this.email = email;
        this.deviceId = deviceId;
        this.authKeyEncrypted = authKeyEncrypted;
        this.status = status;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public UUID getUserId() { return userId; }
    public String getEmail() { return email; }
    public String getDeviceId() { return deviceId; }
    public byte[] getAuthKeyEncrypted() { return authKeyEncrypted; }
    public String getStatus() { return status; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }

    public void setEmail(String email) { this.email = email; }
    public void setDeviceId(String deviceId) { this.deviceId = deviceId; }
    public void setAuthKeyEncrypted(byte[] authKeyEncrypted) { this.authKeyEncrypted = authKeyEncrypted; }
    public void setStatus(String status) { this.status = status; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
