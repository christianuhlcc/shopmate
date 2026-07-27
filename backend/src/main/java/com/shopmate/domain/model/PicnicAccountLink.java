package com.shopmate.domain.model;

/**
 * A user's stored Picnic link. The persisted secret is the session key, never the password
 * digest: a fresh login always re-triggers 2FA (verified 2026-07-26), so a stored password
 * could never be exchanged for a working session unattended, while being far more dangerous
 * to hold. See the ADR-0014 amendment.
 */
public record PicnicAccountLink(String email, PicnicSession session, PicnicLinkState state) {

    public boolean isLinked() {
        return state == PicnicLinkState.LINKED;
    }
}
