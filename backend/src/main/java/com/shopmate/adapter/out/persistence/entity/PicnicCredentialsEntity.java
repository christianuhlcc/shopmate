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

    @Column(name = "password_md5_encrypted", nullable = false)
    private byte[] passwordMd5Encrypted;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected PicnicCredentialsEntity() {}

    public PicnicCredentialsEntity(UUID userId, String email, byte[] passwordMd5Encrypted,
                                    Instant createdAt, Instant updatedAt) {
        this.userId = userId;
        this.email = email;
        this.passwordMd5Encrypted = passwordMd5Encrypted;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public UUID getUserId() { return userId; }
    public String getEmail() { return email; }
    public byte[] getPasswordMd5Encrypted() { return passwordMd5Encrypted; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }

    public void setEmail(String email) { this.email = email; }
    public void setPasswordMd5Encrypted(byte[] passwordMd5Encrypted) { this.passwordMd5Encrypted = passwordMd5Encrypted; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
