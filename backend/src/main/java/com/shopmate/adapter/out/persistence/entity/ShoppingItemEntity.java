package com.shopmate.adapter.out.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.util.UUID;

@Entity
@Table(name = "shopping_items")
public class ShoppingItemEntity {

    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "list_id", nullable = false)
    private ShoppingListEntity list;

    // name LWW field
    @Column(name = "name_value", nullable = false)
    private String nameValue;
    @Column(name = "name_ts", nullable = false)
    private long nameTs;
    @Column(name = "name_modified_by", nullable = false)
    private UUID nameModifiedBy;

    // quantity LWW field
    @Column(name = "quantity_value", nullable = false)
    private String quantityValue;
    @Column(name = "quantity_ts", nullable = false)
    private long quantityTs;
    @Column(name = "quantity_modified_by", nullable = false)
    private UUID quantityModifiedBy;

    // checked LWW field
    @Column(name = "checked_value", nullable = false)
    private boolean checkedValue;
    @Column(name = "checked_ts", nullable = false)
    private long checkedTs;
    @Column(name = "checked_modified_by", nullable = false)
    private UUID checkedModifiedBy;

    // deleted LWW field
    @Column(name = "deleted_value", nullable = false)
    private boolean deletedValue;
    @Column(name = "deleted_ts", nullable = false)
    private long deletedTs;
    @Column(name = "deleted_modified_by", nullable = false)
    private UUID deletedModifiedBy;

    // sort_key LWW field
    @Column(name = "sort_key_value", nullable = false)
    private String sortKeyValue;
    @Column(name = "sort_key_ts", nullable = false)
    private long sortKeyTs;
    @Column(name = "sort_key_modified_by", nullable = false)
    private UUID sortKeyModifiedBy;

    protected ShoppingItemEntity() {}

    public ShoppingItemEntity(UUID id, ShoppingListEntity list,
                               String nameValue, long nameTs, UUID nameModifiedBy,
                               String quantityValue, long quantityTs, UUID quantityModifiedBy,
                               boolean checkedValue, long checkedTs, UUID checkedModifiedBy,
                               boolean deletedValue, long deletedTs, UUID deletedModifiedBy,
                               String sortKeyValue, long sortKeyTs, UUID sortKeyModifiedBy) {
        this.id = id;
        this.list = list;
        this.nameValue = nameValue;
        this.nameTs = nameTs;
        this.nameModifiedBy = nameModifiedBy;
        this.quantityValue = quantityValue;
        this.quantityTs = quantityTs;
        this.quantityModifiedBy = quantityModifiedBy;
        this.checkedValue = checkedValue;
        this.checkedTs = checkedTs;
        this.checkedModifiedBy = checkedModifiedBy;
        this.deletedValue = deletedValue;
        this.deletedTs = deletedTs;
        this.deletedModifiedBy = deletedModifiedBy;
        this.sortKeyValue = sortKeyValue;
        this.sortKeyTs = sortKeyTs;
        this.sortKeyModifiedBy = sortKeyModifiedBy;
    }

    public UUID getId() { return id; }
    public ShoppingListEntity getList() { return list; }

    public String getNameValue() { return nameValue; }
    public long getNameTs() { return nameTs; }
    public UUID getNameModifiedBy() { return nameModifiedBy; }

    public String getQuantityValue() { return quantityValue; }
    public long getQuantityTs() { return quantityTs; }
    public UUID getQuantityModifiedBy() { return quantityModifiedBy; }

    public boolean isCheckedValue() { return checkedValue; }
    public long getCheckedTs() { return checkedTs; }
    public UUID getCheckedModifiedBy() { return checkedModifiedBy; }

    public boolean isDeletedValue() { return deletedValue; }
    public long getDeletedTs() { return deletedTs; }
    public UUID getDeletedModifiedBy() { return deletedModifiedBy; }

    public String getSortKeyValue() { return sortKeyValue; }
    public long getSortKeyTs() { return sortKeyTs; }
    public UUID getSortKeyModifiedBy() { return sortKeyModifiedBy; }

    public void setNameValue(String v) { nameValue = v; }
    public void setNameTs(long v) { nameTs = v; }
    public void setNameModifiedBy(UUID v) { nameModifiedBy = v; }
    public void setQuantityValue(String v) { quantityValue = v; }
    public void setQuantityTs(long v) { quantityTs = v; }
    public void setQuantityModifiedBy(UUID v) { quantityModifiedBy = v; }
    public void setCheckedValue(boolean v) { checkedValue = v; }
    public void setCheckedTs(long v) { checkedTs = v; }
    public void setCheckedModifiedBy(UUID v) { checkedModifiedBy = v; }
    public void setDeletedValue(boolean v) { deletedValue = v; }
    public void setDeletedTs(long v) { deletedTs = v; }
    public void setDeletedModifiedBy(UUID v) { deletedModifiedBy = v; }
    public void setSortKeyValue(String v) { sortKeyValue = v; }
    public void setSortKeyTs(long v) { sortKeyTs = v; }
    public void setSortKeyModifiedBy(UUID v) { sortKeyModifiedBy = v; }
}
