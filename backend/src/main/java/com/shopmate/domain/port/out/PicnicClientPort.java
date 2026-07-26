package com.shopmate.domain.port.out;

import com.shopmate.domain.model.ArticleSuggestion;
import com.shopmate.domain.model.PicnicCredentials;
import com.shopmate.domain.model.PicnicLoginResult;
import com.shopmate.domain.model.PicnicSession;

import java.util.List;

/**
 * Outbound port for the (unofficial) Picnic HTTP API — see ADR-0014 and its 2FA amendment.
 *
 * <p>Credentials appear only in {@link #login}: everything afterwards takes a
 * {@link PicnicSession}, because that session — not the password — is what the rest of the
 * integration is built on.
 */
public interface PicnicClientPort {

    /**
     * Exchanges credentials for a session. Throws PicnicLoginFailedException when Picnic
     * rejects them, PicnicUnavailableException on I/O failure or an unexpected status.
     *
     * <p>A returned session may still be unusable: check
     * {@link PicnicLoginResult#secondFactorRequired()}.
     */
    PicnicLoginResult login(PicnicCredentials credentials, String deviceId);

    /**
     * Asks Picnic to SMS a second-factor code for {@code session}. Throws
     * PicnicUnavailableException on I/O failure or an unexpected status.
     */
    void sendSecondFactor(PicnicSession session);

    /**
     * Completes 2FA and returns the upgraded session. Throws PicnicLoginFailedException when
     * Picnic rejects the code.
     */
    PicnicSession verifySecondFactor(PicnicSession session, String code);

    /**
     * Throws PicnicSessionExpiredException when the session is refused,
     * PicnicUnavailableException on I/O failure, an unexpected status, or a malformed body.
     */
    List<ArticleSuggestion> searchArticles(PicnicSession session, String term);

    /**
     * Throws PicnicSessionExpiredException when the session is refused,
     * PicnicUnavailableException on I/O failure or an unexpected status.
     */
    void addToCart(PicnicSession session, String articleId, int count);
}
