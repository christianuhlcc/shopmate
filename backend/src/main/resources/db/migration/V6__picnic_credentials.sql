CREATE TABLE picnic_credentials (
    user_id                 UUID PRIMARY KEY REFERENCES users(id),
    email                   VARCHAR(255) NOT NULL,
    password_md5_encrypted  BYTEA NOT NULL,
    created_at              TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at              TIMESTAMPTZ NOT NULL DEFAULT now()
);
