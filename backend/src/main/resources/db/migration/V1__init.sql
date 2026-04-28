CREATE TABLE users (
    id           UUID PRIMARY KEY,
    email        VARCHAR(255) NOT NULL UNIQUE,
    display_name VARCHAR(255) NOT NULL,
    avatar_url   VARCHAR(1024),
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE shopping_lists (
    id         UUID PRIMARY KEY,
    name       VARCHAR(255) NOT NULL,
    owner_id   UUID NOT NULL REFERENCES users(id),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE list_members (
    list_id UUID NOT NULL REFERENCES shopping_lists(id) ON DELETE CASCADE,
    user_id UUID NOT NULL REFERENCES users(id),
    PRIMARY KEY (list_id, user_id)
);

CREATE TABLE shopping_items (
    id                   UUID PRIMARY KEY,
    list_id              UUID NOT NULL REFERENCES shopping_lists(id) ON DELETE CASCADE,

    -- LWW field: name
    name_value           VARCHAR(100) NOT NULL DEFAULT '',
    name_ts              BIGINT NOT NULL DEFAULT 0,
    name_modified_by     UUID NOT NULL REFERENCES users(id),

    -- LWW field: quantity
    quantity_value       VARCHAR(100) NOT NULL DEFAULT '1',
    quantity_ts          BIGINT NOT NULL DEFAULT 0,
    quantity_modified_by UUID NOT NULL REFERENCES users(id),

    -- LWW field: checked
    checked_value        BOOLEAN NOT NULL DEFAULT FALSE,
    checked_ts           BIGINT NOT NULL DEFAULT 0,
    checked_modified_by  UUID NOT NULL REFERENCES users(id),

    -- LWW field: deleted (tombstone)
    deleted_value        BOOLEAN NOT NULL DEFAULT FALSE,
    deleted_ts           BIGINT NOT NULL DEFAULT 0,
    deleted_modified_by  UUID NOT NULL REFERENCES users(id),

    -- LWW field: sort_key (fractional index for display ordering)
    sort_key_value       VARCHAR(255) NOT NULL DEFAULT 'a0',
    sort_key_ts          BIGINT NOT NULL DEFAULT 0,
    sort_key_modified_by UUID NOT NULL REFERENCES users(id)
);

CREATE INDEX idx_shopping_items_list_id ON shopping_items(list_id);
CREATE INDEX idx_shopping_items_sort_key ON shopping_items(list_id, sort_key_value);
CREATE INDEX idx_list_members_user_id ON list_members(user_id);
