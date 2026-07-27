package com.shopmate.adapter.out.picnic;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.shopmate.domain.model.ArticleSuggestion;
import com.shopmate.domain.model.PicnicCredentials;
import com.shopmate.domain.model.PicnicLoginFailedException;
import com.shopmate.domain.model.PicnicLoginResult;
import com.shopmate.domain.model.PicnicSession;
import com.shopmate.domain.model.PicnicSessionExpiredException;
import com.shopmate.domain.model.PicnicUnavailableException;
import com.shopmate.domain.port.out.PicnicClientPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.zip.GZIPInputStream;

/**
 * Outbound adapter for Picnic's unofficial storefront API — see ADR-0014. The wire protocol
 * below is reverse-engineered from community wrappers (python-picnic-api / picnic-api), not an
 * official contract; Picnic can change it without notice.
 *
 * <p>Sessions are obtained once and reused, because they have to be: a login always demands a
 * second factor (verified 2026-07-26), so there is no way to re-authenticate without a human.
 * See the ADR-0014 amendment.
 */
@Component
public class PicnicHttpAdapter implements PicnicClientPort {

    // Germany only, per ADR-0014's scope. Not meant to be genuinely reconfigured in prod — the
    // @Value indirection exists purely so tests can point this at a local WireMock server.
    static final String DEFAULT_BASE_URL = "https://storefront-prod.de.picnicinternational.com/api/15";

    // Community wrappers don't document a hard cap; this just bounds pathological response sizes.
    // The application layer (a later phase) further truncates to top 5 for the picker.
    private static final int MAX_SEARCH_RESULTS = 20;

    // Verified against a live response (2026-07-26): image_id is a 64-hex id and this template
    // resolves to a real PNG, matching how the reference client derives image URLs.
    private static final String IMAGE_URL_TEMPLATE =
        "https://storefront-prod.de.picnicinternational.com/static/images/%s/medium.png";

    // Verified against the live DE storefront on 2026-07-26: the flat "/search" endpoint the
    // community wrappers document is GONE — it 404s on api/15, /17 and /19 alike, while
    // "/pages/search-page-results" answers 401 (i.e. exists, wants auth) on the same versions.
    // Picnic moved search behind its page-block API; this is the endpoint that still resolves.
    // Confirmed working end-to-end once the session has cleared 2FA; the 403 this returned
    // before that was the unverified session being refused, not the endpoint being wrong.
    private static final String SEARCH_PATH = "/pages/search-page-results?search_term=";

    // Unlike search, this one is still a plain flat endpoint: it answers with a bare array of
    // {type,id,suggestion} objects and nothing else. Matches the maintained reference client
    // (MRVDH/picnic-api, getSuggestions), and a live response for "Milch" was 524 bytes.
    private static final String SUGGEST_PATH = "/suggest?search_term=";

    // Picnic returns around seven; this only bounds a pathological response, since these are
    // rendered as a list under a text field.
    private static final int MAX_TERM_SUGGESTIONS = 10;

    // Header set mirrored from the maintained Node reference client (MRVDH/picnic-api,
    // src/http-client.ts) — Picnic's page endpoints 500 or 403 without them. The agent string
    // encodes an app version; a stale one is a plausible rejection cause, so it is kept current
    // with that client rather than frozen. Accept-Language is fixed to "de": Germany-only scope
    // per ADR-0014.
    private static final String USER_AGENT = "okhttp/4.9.0";
    private static final String CONTENT_TYPE = "application/json; charset=UTF-8";
    private static final String ACCEPT_LANGUAGE = "de";
    private static final String PICNIC_AGENT = "30100;1.236.1-15553;";
    private static final String AUTH_HEADER = "x-picnic-auth";

    // The node type wrapping each product in the search page tree; its "sellingUnit" child
    // holds the product itself, and its id is also what /cart/add_product takes.
    private static final String SELLING_UNIT_TILE_TYPE = "SELLING_UNIT_TILE";

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
    public void sendSecondFactor(PicnicSession session) {
        HttpRequest request = authedRequest(baseUrl + "/user/2fa/generate", session)
            .POST(HttpRequest.BodyPublishers.ofString(writeJson(Map.of("channel", "SMS"))))
            .build();

        HttpResponse<byte[]> response = send(request);
        if (!isSuccess(response.statusCode())) {
            throw new PicnicUnavailableException(
                "Picnic refused to send a second-factor code (HTTP " + response.statusCode() + ")");
        }
    }

    @Override
    public PicnicSession verifySecondFactor(PicnicSession session, String code) {
        HttpRequest request = authedRequest(baseUrl + "/user/2fa/verify", session)
            .POST(HttpRequest.BodyPublishers.ofString(writeJson(Map.of("otp", code))))
            .build();

        HttpResponse<byte[]> response = send(request);

        // A wrong or expired code is the user's problem to fix, not an outage.
        if (response.statusCode() >= 400 && response.statusCode() < 500) {
            JsonNode body = tryParseJson(bodyOf(response));
            throw new PicnicLoginFailedException("Picnic rejected the second-factor code"
                + (hasErrorIndicator(body) ? ": " + errorMessage(body) : ""));
        }
        if (!isSuccess(response.statusCode())) {
            throw new PicnicUnavailableException(
                "Picnic second-factor verification failed with unexpected status " + response.statusCode());
        }

        // Verification mints a *new* key; the provisional one stays unusable, so missing this
        // header would leave us storing a session that can never work.
        String upgraded = response.headers().firstValue(AUTH_HEADER)
            .orElseThrow(() -> new PicnicUnavailableException(
                "Picnic second-factor verification returned no " + AUTH_HEADER + " header"));
        return new PicnicSession(upgraded, session.deviceId());
    }

    @Override
    public List<ArticleSuggestion> searchArticles(PicnicSession session, String term) {
        JsonNode root = getJson(session, SEARCH_PATH + encode(term), "search");
        List<ArticleSuggestion> results = new ArrayList<>();
        collectSellingUnits(root, results);
        return results;
    }

    /**
     * Reads the flat {@code [{type,id,suggestion}, …]} array Picnic answers autocomplete with.
     * Only the {@code suggestion} text is kept: the ids are Picnic's own and mean nothing to a
     * later search, which takes the term as free text.
     *
     * <p>An unrecognised shape yields no suggestions rather than an error. Autocomplete is a
     * convenience on top of a field the user can always type into, so drift here should cost
     * them the hints, not the search.
     */
    @Override
    public List<String> suggestSearchTerms(PicnicSession session, String partialTerm) {
        JsonNode root = getJson(session, SUGGEST_PATH + encode(partialTerm), "suggest");
        if (root == null || !root.isArray()) {
            return List.of();
        }

        List<String> terms = new ArrayList<>();
        for (JsonNode node : root) {
            String suggestion = textOrNull(node.get("suggestion"));
            if (suggestion != null && !terms.contains(suggestion)) {
                terms.add(suggestion);
            }
            if (terms.size() >= MAX_TERM_SUGGESTIONS) {
                break;
            }
        }
        return terms;
    }

    @Override
    public void addToCart(PicnicSession session, String articleId, int count) {
        // product_id takes the selling-unit id from search (e.g. "s1018863") — the page's own
        // ADD action carries exactly that id, so the two surfaces agree.
        String requestBody = writeJson(Map.of("product_id", articleId, "count", count));
        HttpRequest request = authedRequest(baseUrl + "/cart/add_product", session)
            .POST(HttpRequest.BodyPublishers.ofString(requestBody))
            .build();

        HttpResponse<byte[]> response = send(request);
        rejectIfSessionRefused(response.statusCode(), "add-to-cart");
        if (!isSuccess(response.statusCode())) {
            throw new PicnicUnavailableException("Picnic add-to-cart failed with unexpected status " + response.statusCode());
        }
    }

    /**
     * Performs the login call. Returns the issued session together with whether Picnic still
     * wants a second factor — it hands out a key in either case, so the caller must not read a
     * successful response as "this account is ready to use".
     */
    @Override
    public PicnicLoginResult login(PicnicCredentials credentials, String deviceId) {
        String requestBody = writeJson(Map.of(
            "key", credentials.email(),
            "secret", credentials.passwordMd5Hex(),
            "client_id", PICNIC_CLIENT_ID));

        // Deliberately NOT sending the x-picnic-agent/did headers here: the reference client
        // omits them at login and only adds them to authenticated calls.
        HttpRequest request = baseRequest(baseUrl + "/user/login")
            .POST(HttpRequest.BodyPublishers.ofString(requestBody))
            .build();

        HttpResponse<byte[]> response = send(request);

        // A rejected login can show up as a 200 with an error indicator in the body (community
        // clients check error_code values like AUTH_ERROR/AUTH_INVALID_CRED) as well as a plain
        // non-2xx status, so the body check runs regardless of status code.
        JsonNode body = tryParseJson(bodyOf(response));
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

        String authKey = response.headers().firstValue(AUTH_HEADER)
            .orElseThrow(() -> new PicnicUnavailableException("Picnic login response is missing the " + AUTH_HEADER + " header"));

        boolean secondFactorRequired =
            body != null && body.path("second_factor_authentication_required").asBoolean(false);

        return new PicnicLoginResult(new PicnicSession(authKey, deviceId), secondFactorRequired);
    }

    /**
     * 401/403 on an authenticated call means the session is dead, not that Picnic is down —
     * and the two need different handling, since only one of them can be fixed by waiting.
     */
    private static void rejectIfSessionRefused(int statusCode, String operation) {
        if (statusCode == 401 || statusCode == 403) {
            throw new PicnicSessionExpiredException(
                "Picnic refused the stored session on " + operation + " (HTTP " + statusCode + ")");
        }
    }

    private static HttpRequest.Builder baseRequest(String uri) {
        return HttpRequest.newBuilder()
            .uri(URI.create(uri))
            .header("User-Agent", USER_AGENT)
            .header("Content-Type", CONTENT_TYPE)
            .header("Accept-Language", ACCEPT_LANGUAGE)
            // Measured 2026-07-26: a search page is 1.5 MB raw and 59 KB gzipped. Java's
            // HttpClient neither asks for nor decodes gzip on its own, so without this we pay
            // 26x the bandwidth for every search.
            .header("Accept-Encoding", "gzip");
    }

    private static HttpRequest.Builder authedRequest(String uri, PicnicSession session) {
        return baseRequest(uri)
            .header(AUTH_HEADER, session.authKey())
            .header("x-picnic-agent", PICNIC_AGENT)
            .header("x-picnic-did", session.deviceId());
    }

    private HttpResponse<byte[]> send(HttpRequest request) {
        try {
            // Bytes, not a String: the body may be gzipped, and decoding as text first would
            // corrupt it.
            return httpClient.send(request, HttpResponse.BodyHandlers.ofByteArray());
        } catch (IOException e) {
            throw new PicnicUnavailableException("Failed to reach Picnic: " + e.getMessage(), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new PicnicUnavailableException("Interrupted while calling Picnic", e);
        }
    }

    /**
     * The shared shape of both read calls: authenticated GET, session-refusal and status
     * checks, then a parsed body. {@code operation} names the call in whatever error comes out.
     */
    private JsonNode getJson(PicnicSession session, String pathAndQuery, String operation) {
        HttpResponse<byte[]> response = send(
            authedRequest(baseUrl + pathAndQuery, session).GET().build());

        rejectIfSessionRefused(response.statusCode(), operation);
        if (!isSuccess(response.statusCode())) {
            throw new PicnicUnavailableException(
                "Picnic " + operation + " failed with unexpected status " + response.statusCode());
        }

        try {
            return objectMapper.readTree(bodyOf(response));
        } catch (JsonProcessingException e) {
            throw new PicnicUnavailableException(
                "Picnic " + operation + " returned a malformed JSON body", e);
        }
    }

    private static String encode(String term) {
        return URLEncoder.encode(term, StandardCharsets.UTF_8);
    }

    /**
     * Decodes the response body, transparently un-gzipping when Picnic honoured our
     * Accept-Encoding. Picnic is not required to compress, so both shapes must work.
     */
    private static String bodyOf(HttpResponse<byte[]> response) {
        byte[] raw = response.body();
        if (raw == null || raw.length == 0) {
            return "";
        }
        boolean gzipped = response.headers().firstValue("Content-Encoding")
            .map(value -> value.toLowerCase(Locale.ROOT).contains("gzip"))
            .orElse(false);
        if (!gzipped) {
            return new String(raw, StandardCharsets.UTF_8);
        }
        try (GZIPInputStream gzip = new GZIPInputStream(new ByteArrayInputStream(raw))) {
            return new String(gzip.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new PicnicUnavailableException("Picnic returned an undecodable gzip body", e);
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
     * Picnic answers search with a rendered page tree ({@code {script, layout}}), not a product
     * list: products are {@code SELLING_UNIT_TILE} nodes buried in a deep layout whose container
     * keys vary ({@code child}, {@code children}, {@code content}, …). Observed depth to the
     * first tile on a live response was 14 levels through three different key names, so this
     * walks *every* value rather than following a known path — layout churn then costs nothing.
     *
     * <p>Matching on the tile type rather than sniffing for id+name matters: sibling
     * {@code SELLING_UNIT_MUTATION} nodes also carry a selling-unit id but no product data, and
     * a generic sniff picks them up as phantom results.
     */
    private void collectSellingUnits(JsonNode node, List<ArticleSuggestion> results) {
        if (node == null || !node.isContainerNode() || results.size() >= MAX_SEARCH_RESULTS) {
            return;
        }

        if (node.isObject() && SELLING_UNIT_TILE_TYPE.equals(node.path("type").asText(null))) {
            ArticleSuggestion suggestion = toArticleSuggestion(node.get("sellingUnit"));
            if (suggestion != null) {
                results.add(suggestion);
                // A tile's own subtree is presentation (labels, images, the ADD mutation) — the
                // product is fully described by sellingUnit, so there is nothing below to find.
                return;
            }
        }

        // Iterating a JsonNode yields array elements or object *values*, which is exactly the
        // "descend through anything" behaviour this needs.
        for (JsonNode child : node) {
            if (results.size() >= MAX_SEARCH_RESULTS) {
                return;
            }
            collectSellingUnits(child, results);
        }
    }

    /**
     * Field names verified against a live response (2026-07-26): all of id, name,
     * display_price, image_id and unit_quantity were present on every one of the 120 products
     * returned. They stay defensively optional anyway — this is still an unofficial API.
     * Returns null when the node cannot identify a product at all.
     */
    private static ArticleSuggestion toArticleSuggestion(JsonNode sellingUnit) {
        if (sellingUnit == null || !sellingUnit.isObject()) {
            return null;
        }
        String id = textOrNull(sellingUnit.get("id"));
        String name = textOrNull(sellingUnit.get("name"));
        if (id == null || name == null) {
            return null;
        }
        String imageId = textOrNull(sellingUnit.get("image_id"));
        return new ArticleSuggestion(
            id,
            name,
            imageId != null ? IMAGE_URL_TEMPLATE.formatted(imageId) : null,
            intOrNull(sellingUnit.get("display_price")),
            textOrNull(sellingUnit.get("unit_quantity")));
    }

    private static String textOrNull(JsonNode node) {
        return (node != null && node.isTextual()) ? node.asText() : null;
    }

    private static Integer intOrNull(JsonNode node) {
        return (node != null && node.isNumber()) ? node.intValue() : null;
    }
}
