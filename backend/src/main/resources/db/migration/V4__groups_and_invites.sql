CREATE TABLE groups (
    id         UUID PRIMARY KEY,
    name       VARCHAR(100) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE invite_codes (
    id         UUID PRIMARY KEY,
    code       VARCHAR(16) NOT NULL UNIQUE,
    type       VARCHAR(20) NOT NULL CHECK (type IN ('JOIN_GROUP', 'NEW_GROUP')),
    group_id   UUID REFERENCES groups(id),
    created_by UUID NOT NULL REFERENCES users(id),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    expires_at TIMESTAMPTZ NOT NULL,
    used_by    UUID REFERENCES users(id),
    used_at    TIMESTAMPTZ
);

ALTER TABLE users ADD COLUMN group_id UUID REFERENCES groups(id);
ALTER TABLE shopping_lists ADD COLUMN group_id UUID REFERENCES groups(id);

CREATE INDEX idx_users_group_id ON users(group_id);
CREATE INDEX idx_shopping_lists_group_id ON shopping_lists(group_id);

-- Backfill: prod already has a live household. If any users exist, create a single
-- default group and assign every existing user/list to it. Fresh (empty) databases
-- get no default group — the first invite redemption creates one via NEW_GROUP.
DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM users) THEN
        INSERT INTO groups (id, name, created_at)
        VALUES ('00000000-0000-0000-0000-000000000001', 'ShopMate', now());

        UPDATE users
        SET group_id = '00000000-0000-0000-0000-000000000001'
        WHERE group_id IS NULL;

        UPDATE shopping_lists
        SET group_id = '00000000-0000-0000-0000-000000000001'
        WHERE group_id IS NULL;
    END IF;
END $$;
