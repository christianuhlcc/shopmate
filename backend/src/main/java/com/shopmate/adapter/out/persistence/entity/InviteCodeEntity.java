package com.shopmate.adapter.out.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "invite_codes")
public class InviteCodeEntity {

    @Id
    private UUID id;

    @Column(nullable = false, unique = true)
    private String code;

    @Column(nullable = false)
    private String type;

    @Column(name = "group_id")
    private UUID groupId;

    @Column(name = "created_by", nullable = false)
    private UUID createdBy;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "used_by")
    private UUID usedBy;

    @Column(name = "used_at")
    private Instant usedAt;

    protected InviteCodeEntity() {}

    public InviteCodeEntity(UUID id, String code, String type, UUID groupId, UUID createdBy,
                             Instant createdAt, Instant expiresAt, UUID usedBy, Instant usedAt) {
        this.id = id;
        this.code = code;
        this.type = type;
        this.groupId = groupId;
        this.createdBy = createdBy;
        this.createdAt = createdAt;
        this.expiresAt = expiresAt;
        this.usedBy = usedBy;
        this.usedAt = usedAt;
    }

    public UUID getId() { return id; }
    public String getCode() { return code; }
    public String getType() { return type; }
    public UUID getGroupId() { return groupId; }
    public UUID getCreatedBy() { return createdBy; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getExpiresAt() { return expiresAt; }
    public UUID getUsedBy() { return usedBy; }
    public Instant getUsedAt() { return usedAt; }
}
