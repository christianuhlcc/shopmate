package com.shopmate.adapter.out.picnic;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.shopmate.domain.model.ArticleSuggestion;
import com.shopmate.domain.model.PicnicCredentials;
import com.shopmate.domain.model.PicnicLoginFailedException;
import com.shopmate.domain.model.PicnicUnavailableException;
import com.shopmate.domain.port.out.PicnicClientPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Outbound adapter for Picnic's unofficial storefront API — see ADR-0014. The wire protocol
 * below is reverse-engineered from community wrappers (python-picnic-api / picnic-api), not an
 * official contract; Picnic can change it without notice. No session caching: every method
 * performs its own fresh login (ADR-0014, deliberate trade-off, not something to "fix").
 */
@Component
public class PicnicHttpAdapter implements PicnicClientPort {

    // Germany only, per ADR-0014's scope. Not meant to be genuinely reconfigured in prod — the
    // @Value indirection exists purely so tests can point this at a local WireMock server.
    static final String DEFAULT_BASE_URL = "https://storefront-prod.de.picnicinternational.com/api/15";

    // Community wrappers don't document a hard cap; this just bounds pathological response sizes.
    // The application layer (a later phase) further truncates to top 5 for the picker.
    private static final int MAX_SEARCH_RESULTS = 20;

    // UNVERIFIED GUESS: this image URL template is inferred from community Picnic integrations,
    // not confirmed against a live account. Spot-check (and likely correct) once this feature is
    // tested end-to-end against a real Picnic login.
    private static final String IMAGE_URL_TEMPLATE =
        "https://storefront-prod.de.picnicinternational.com/static/images/%s/medium.png";

    // Verified against the live DE storefront on 2026-07-26: the flat "/search" endpoint the
    // community wrappers document is GONE — it 404s on api/15, /17 and /19 alike, while
    // "/pages/search-page-results" answers 401 (i.e. exists, wants auth) on the same versions.
    // Picnic moved search behind its page-block API; this is the endpoint that still resolves.
    //
    // KNOWN BROKEN (2026-07-26): with a real, working account this still answers 403 with an
    // empty body, even sending the agent/did headers below on both login and search. Login
    // itself succeeds, so the credential path is fine — search specifically is refused. Picnic
    // appears to bind the session to a device registration we don't perform. Everything past
    // this call (suggestions, add-to-cart) is therefore unverified against the live API; the
    // response *shape* the flattener below expects is still the community-documented one and
    // has never been seen from the real endpoint. See docs/plans/picnic-export.md §6.
    private static final String SEARCH_PATH = "/pages/search-page-results?search_term=";

    // Header set mirrored from the maintained Node reference client (MRVDH/picnic-api,
    // src/http-client.ts) — Picnic's page endpoints 500 or 403 without them. The agent string
    // encodes an app version; a stale one is a plausible rejection cause, so it is kept current
    // with that client rather than frozen. Accept-Language is fixed to "de": Germany-only scope
    // per ADR-0014.
    private static final String USER_AGENT = "okhttp/4.9.0";
    private static final String CONTENT_TYPE = "application/json; charset=UTF-8";
    private static final String ACCEPT_LANGUAGE = "de";
    private static final String PICNIC_AGENT = "30100;1.236.1-15553;";
    private static final String PICNIC_DID = "3C417201548B2E3B";

    private static final int PICNIC_CLIENT_ID = 30100;

    private static final Logger log = LoggerFactory.getLogger(PicnicHttpAdapter.class);

    private final HttpClient httpClient = HttpClient.newHttpClient();
    private final ObjectMapper objectMapper;
    private final String baseUrl;

    public PicnicHttpAdapter(
            ObjectMapper objectMapper,
            @Value("${shopmate.picnic.base-url:" + DEFAULT_BASE_URL + "}") String baseUrl) {
        this.objectMapper = objectMapper;
        this.baseUrl = baseUrl;
    }

    @Override
    public void verifyLogin(PicnicCredentials credentials) {
        // Only job here is to throw (or not) — the token itself is discarded.
        login(credentials);
    }

    @Override
    public List<ArticleSuggestion> searchArticles(PicnicCredentials credentials, String term) {
        String token = login(credentials);

        String encodedTerm = URLEncoder.encode(term, StandardCharsets.UTF_8);
        HttpRequest request = authedRequest(baseUrl + SEARCH_PATH + encodedTerm, token)
            .GET()
            .build();

        HttpResponse<String> response = send(request);
        if (!isSuccess(response.statusCode())) {
            throw new PicnicUnavailableException("Picnic search failed with unexpected status " + response.statusCode());
        }

        JsonNode root;
        try {
            root = objectMapper.readTree(response.body());
        } catch (JsonProcessingException e) {
            throw new PicnicUnavailableException("Picnic search returned a malformed JSON body", e);
        }

        List<ArticleSuggestion> results = new ArrayList<>();
        flatten(root, results);
        return results;
    }

    @Override
    public void addToCart(PicnicCredentials credentials, String articleId, int count) {
        String token = login(credentials);

        String requestBody = writeJson(Map.of("product_id", articleId, "count", count));
        HttpRequest request = authedRequest(baseUrl + "/cart/add_product", token)
            .POST(HttpRequest.BodyPublishers.ofString(requestBody))
            .build();

        HttpResponse<String> response = send(request);
        if (!isSuccess(response.statusCode())) {
            throw new PicnicUnavailableException("Picnic add-to-cart failed with unexpected status " + response.statusCode());
        }
    }

    /**
     * Performs the login call and returns the {@code x-picnic-auth} token. Every public method
     * calls this fresh — see class javadoc on session caching.
     */
    private String login(PicnicCredentials credentials) {
        String requestBody = writeJson(Map.of(
            "key", credentials.email(),
            "secret", credentials.passwordMd5Hex(),
            "client_id", PICNIC_CLIENT_ID));

        // Deliberately NOT sending the x-picnic-agent/did headers here: the reference client
        // omits them at login and only adds them to authenticated calls.
        HttpRequest request = baseRequest(baseUrl + "/user/login")
            .POST(HttpRequest.BodyPublishers.ofString(requestBody))
            .build();

        HttpResponse<String> response = send(request);

        // A rejected login can show up as a 200 with an error indicator in the body (community
        // clients check error_code values like AUTH_ERROR/AUTH_INVALID_CRED) as well as a plain
        // non-2xx status, so the body check runs regardless of status code.
        JsonNode body = tryParseJson(response.body());
        if (hasErrorIndicator(body)) {
            throw new PicnicLoginFailedException("Picnic rejected the login: " + errorMessage(body));
        }

        int status = response.statusCode();
        if (status >= 400 && status < 500) {
            throw new PicnicLoginFailedException("Picnic rejected the login credentials (HTTP " + status + ")");
        }
        if (!isSuccess(status)) {
            throw new PicnicUnavailableException("Picnic login failed with unexpected status " + status);
        }

        // Picnic hands out an auth key even when the account still owes a second factor; that key
        // is then refused (403) by every real endpoint. ADR-0014 never considered 2FA, and this
        // is indistinguishable from other 403s at the call site, so flag it here.
        if (body != null && body.path("second_factor_authentication_required").asBoolean(false)) {
            log.warn("Picnic login returned second_factor_authentication_required=true — the "
                + "auth key will be refused by search/cart until 2FA is completed.");
        }

        return response.headers().firstValue("x-picnic-auth")
            .orElseThrow(() -> new PicnicUnavailableException("Picnic login response is missing the x-picnic-auth header"));
    }

    private static HttpRequest.Builder baseRequest(String uri) {
        return HttpRequest.newBuilder()
            .uri(URI.create(uri))
            .header("User-Agent", USER_AGENT)
            .header("Content-Type", CONTENT_TYPE)
            .header("Accept-Language", ACCEPT_LANGUAGE);
    }

    private static HttpRequest.Builder authedRequest(String uri, String token) {
        return baseRequest(uri)
            .header("x-picnic-auth", token)
            .header("x-picnic-agent", PICNIC_AGENT)
            .header("x-picnic-did", PICNIC_DID);
    }

    private HttpResponse<String> send(HttpRequest request) {
        try {
            return httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        } catch (IOException e) {
            throw new PicnicUnavailableException("Failed to reach Picnic: " + e.getMessage(), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new PicnicUnavailableException("Interrupted while calling Picnic", e);
        }
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new PicnicUnavailableException("Failed to build Picnic request body", e);
        }
    }

    private static boolean isSuccess(int statusCode) {
        return statusCode >= 200 && statusCode < 300;
    }

    private JsonNode tryParseJson(String body) {
        if (body == null || body.isBlank()) {
            return null;
        }
        try {
            return objectMapper.readTree(body);
        } catch (JsonProcessingException e) {
            return null;
        }
    }

    private static boolean hasErrorIndicator(JsonNode body) {
        return body != null && body.isObject() && (body.has("error") || body.has("error_code"));
    }

    /**
     * Verified against the live DE storefront (2026-07-26): a rejected login answers 401 with
     * {@code {"error":{"code":"AUTH_INVALID_CRED","message":"Invalid credentials","details":{}}}},
     * i.e. "error" is an object, not a string. Reading it with asText() yields "" on a container
     * node, so the nested "code"/"message" are unwrapped explicitly. The flat string and
     * "error_code" shapes reported by community clients are still handled as fallbacks.
     */
    private static String errorMessage(JsonNode body) {
        if (body.has("error_code")) {
            return body.get("error_code").asText();
        }
        // Non-null: hasErrorIndicator only lets a body through if it has "error_code" (returned
        // above) or "error".
        JsonNode error = body.get("error");
        if (error.isObject()) {
            JsonNode code = error.get("code");
            return code != null ? code.asText() : "unknown error";
        }
        return error.asText();
    }

    /**
     * Picnic's search response is a JSON array of nested category objects, each optionally
     * carrying an "items" array of children (recursing arbitrarily deep). A leaf/product-level
     * object is one with both "id" and "name"; every other field is optional and defensively
     * null-mapped, since community clients report inconsistent presence of leaf fields.
     */
    private void flatten(JsonNode node, List<ArticleSuggestion> results) {
        if (node == null || node.isMissingNode() || node.isNull() || results.size() >= MAX_SEARCH_RESULTS) {
            return;
        }

        if (node.isArray()) {
            for (JsonNode child : node) {
                if (results.size() >= MAX_SEARCH_RESULTS) {
                    return;
                }
                flatten(child, results);
            }
            return;
        }

        if (!node.isObject()) {
            return;
        }

        JsonNode id = node.get("id");
        JsonNode name = node.get("name");
        if (id != null && id.isTextual() && name != null && name.isTextual()) {
            results.add(toArticleSuggestion(id.asText(), name.asText(), node));
        }

        JsonNode items = node.get("items");
        if (items != null && items.isArray() && results.size() < MAX_SEARCH_RESULTS) {
            flatten(items, results);
        }
    }

    private static ArticleSuggestion toArticleSuggestion(String id, String name, JsonNode node) {
        String unit = textOrNull(node.get("unit_quantity"));
        Integer priceCents = intOrNull(node.get("display_price"));
        String imageId = textOrNull(node.get("image_id"));
        String imageUrl = imageId != null ? IMAGE_URL_TEMPLATE.formatted(imageId) : null;
        return new ArticleSuggestion(id, name, imageUrl, priceCents, unit);
    }

    private static String textOrNull(JsonNode node) {
        return (node != null && node.isTextual()) ? node.asText() : null;
    }

    private static Integer intOrNull(JsonNode node) {
        return (node != null && node.isNumber()) ? node.intValue() : null;
    }
}
