-- ADR-0014 amendment: Picnic requires a second factor, so the stored secret becomes the
-- session key instead of the password digest. A fresh login always re-triggers 2FA, which
-- means a stored password can never be exchanged for a working session unattended — it
-- bought nothing while being the more dangerous thing to hold.

-- Every existing row holds a password digest and no session, so none of them can be upgraded
-- in place. They are also already useless: the model they were written under cannot obtain a
-- usable session. Affected users re-link, which they would have had to do regardless.
DELETE FROM picnic_credentials;

ALTER TABLE picnic_credentials
    DROP COLUMN password_md5_encrypted,
    -- Picnic ties a session to the device that obtained it, so the id is part of the
    -- credential, not a client detail we can regenerate at will.
    ADD COLUMN device_id VARCHAR(64) NOT NULL,
    ADD COLUMN auth_key_encrypted BYTEA NOT NULL,
    -- PENDING_SECOND_FACTOR holds a provisional session that can do nothing but complete 2FA.
    ADD COLUMN status VARCHAR(32) NOT NULL;
