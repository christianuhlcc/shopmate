package com.shopmate.domain.port.out;

import com.shopmate.domain.model.ArticleSuggestion;
import com.shopmate.domain.model.PicnicCredentials;

import java.util.List;

/**
 * Outbound port for the (unofficial) Picnic HTTP API — see ADR-0014.
 */
public interface PicnicClientPort {

    // Throws PicnicLoginFailedException when Picnic rejects the credentials (wrong
    // password), PicnicUnavailableException on I/O failure or an unexpected status.
    void verifyLogin(PicnicCredentials credentials);

    // Throws PicnicUnavailableException on I/O failure, an unexpected status, or a
    // malformed response body.
    List<ArticleSuggestion> searchArticles(PicnicCredentials credentials, String term);

    // Throws PicnicUnavailableException on I/O failure or an unexpected status.
    void addToCart(PicnicCredentials credentials, String articleId, int count);
}
