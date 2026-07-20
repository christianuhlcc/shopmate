package com.shopmate.adapter.out.persistence.entity;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "shopping_lists")
public class ShoppingListEntity {

    @Id
    private UUID id;

    @Column(nullable = false)
    private String name;

    @Column(name = "owner_id", nullable = false)
    private UUID ownerId;

    @Column(name = "group_id", nullable = false)
    private UUID groupId;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @OneToMany(mappedBy = "list", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private List<ShoppingItemEntity> items = new ArrayList<>();

    protected ShoppingListEntity() {}

    public ShoppingListEntity(UUID id, String name, UUID ownerId, UUID groupId, Instant createdAt) {
        this.id = id;
        this.name = name;
        this.ownerId = ownerId;
        this.groupId = groupId;
        this.createdAt = createdAt;
    }

    public UUID getId() { return id; }
    public String getName() { return name; }
    public UUID getOwnerId() { return ownerId; }
    public UUID getGroupId() { return groupId; }
    public Instant getCreatedAt() { return createdAt; }
    public List<ShoppingItemEntity> getItems() { return items; }

    public void setName(String name) { this.name = name; }
}
