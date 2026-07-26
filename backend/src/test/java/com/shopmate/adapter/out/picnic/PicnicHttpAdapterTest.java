package com.shopmate.adapter.out.picnic;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.shopmate.domain.model.ArticleSuggestion;
import com.shopmate.domain.model.PicnicCredentials;
import com.shopmate.domain.model.PicnicLoginFailedException;
import com.shopmate.domain.model.PicnicLoginResult;
import com.shopmate.domain.model.PicnicSession;
import com.shopmate.domain.model.PicnicSessionExpiredException;
import com.shopmate.domain.model.PicnicUnavailableException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Plain JUnit 5 test — no Spring context, no Docker. WireMock stubs Picnic's storefront API
 * (see PicnicHttpAdapter's class javadoc for the reverse-engineered wire protocol).
 */
class PicnicHttpAdapterTest {

    private static final PicnicCredentials CREDENTIALS =
        new PicnicCredentials("user@example.com", "5f4dcc3b5aa765d61d8327deb882cf99");
    private static final String DEVICE_ID = "A1B2C3D4E5F60718";
    private static final PicnicSession SESSION = new PicnicSession("session-key", DEVICE_ID);

    /**
     * Trimmed from a real search response (2026-07-26). What matters and is reproduced exactly:
     * the root is an object, products are SELLING_UNIT_TILE nodes wrapping a "sellingUnit", and
     * the path down to them alternates between "child", "children" and "content" rather than
     * using one container key. The sibling SELLING_UNIT_MUTATION node is real too — it carries a
     * selling-unit id but no product data, which is precisely what a naive walk trips over.
     */
    private static final String REAL_SEARCH_PAGE = """
        {
          "script": {"id": "search"},
          "layout": {
            "id": "search-page",
            "body": {
              "child": {
                "children": [
                  {"type": "RICH_TEXT", "text": "Ergebnisse"},
                  {"child": {"children": [
                    {"children": [
                      {"content": {
                        "type": "SELLING_UNIT_TILE",
                        "sellingUnit": {
                          "id": "s1018863",
                          "name": "Edeka Bio Fettarme H-Milch 1,5%",
                          "display_price": 115,
                          "image_id": "951c3a9070a5cdd5",
                          "unit_quantity": "1L",
                          "max_count": 50
                        }
                      }},
                      {"type": "SELLING_UNIT_MUTATION", "mutation": "ADD",
                       "quantity": 1, "sellingUnitId": "s1018863"},
                      {"content": {
                        "type": "SELLING_UNIT_TILE",
                        "sellingUnit": {"id": "s1020462", "name": "Edeka Bio H-Vollmilch 3,8%"}
                      }}
                    ]}
                  ]}}
                ]
              }
            }
          }
        }
        """;

    private WireMockServer wireMockServer;
    private PicnicHttpAdapter adapter;

    @BeforeEach
    void setUp() {
        wireMockServer = new WireMockServer(0); // 0 = dynamic port
        wireMockServer.start();
        adapter = new PicnicHttpAdapter(new ObjectMapper(), "http://localhost:" + wireMockServer.port());
    }

    @AfterEach
    void tearDown() {
        wireMockServer.stop();
    }

    // --- login ---------------------------------------------------------------------------

    @Test
    void loginReturnsSessionWhenPicnicReturnsAuthHeader() {
        stubSuccessfulLogin();

        PicnicLoginResult result = adapter.login(CREDENTIALS, DEVICE_ID);

        assertThat(result.secondFactorRequired()).isFalse();
        assertThat(result.session().authKey()).isEqualTo("some-token");
        assertThat(result.session().deviceId()).isEqualTo(DEVICE_ID);
    }

    @Test
    void loginReportsWhenPicnicStillWantsASecondFactor() {
        // Picnic answers 200 *and* hands out a key here — reading this as a usable login is the
        // exact mistake that made a linked account fail on every later call.
        wireMockServer.stubFor(post(urlPathEqualTo("/user/login"))
            .willReturn(aResponse().withStatus(200)
                .withHeader("x-picnic-auth", "provisional-key")
                .withHeader("Content-Type", "application/json")
                .withBody("{\"second_factor_authentication_required\":true,\"user_id\":\"803\"}")));

        PicnicLoginResult result = adapter.login(CREDENTIALS, DEVICE_ID);

        assertThat(result.secondFactorRequired()).isTrue();
        assertThat(result.session().authKey()).isEqualTo("provisional-key");
    }

    @Test
    void loginThrowsLoginFailedOn400() {
        wireMockServer.stubFor(post(urlPathEqualTo("/user/login"))
            .willReturn(aResponse().withStatus(400).withBody("{}")));

        assertThatThrownBy(() -> adapter.login(CREDENTIALS, DEVICE_ID))
            .isInstanceOf(PicnicLoginFailedException.class);
    }

    @Test
    void loginThrowsLoginFailedOn200WithErrorCodeBody() {
        wireMockServer.stubFor(post(urlPathEqualTo("/user/login"))
            .willReturn(aResponse().withStatus(200)
                .withHeader("Content-Type", "application/json")
                .withBody("{\"error_code\": \"AUTH_INVALID_CRED\", \"error\": \"invalid credentials\"}")));

        assertThatThrownBy(() -> adapter.login(CREDENTIALS, DEVICE_ID))
            .isInstanceOf(PicnicLoginFailedException.class)
            .hasMessageContaining("AUTH_INVALID_CRED");
    }

    @Test
    void loginThrowsLoginFailedOnRealStorefrontErrorShape() {
        // Captured verbatim from the live DE storefront on 2026-07-26: "error" is an OBJECT
        // carrying code/message, not a string. asText() on a container node returns "", so an
        // unwrapping regression here would silently blank out the reason.
        wireMockServer.stubFor(post(urlPathEqualTo("/user/login"))
            .willReturn(aResponse().withStatus(401)
                .withHeader("Content-Type", "application/json")
                .withBody("{\"error\":{\"code\":\"AUTH_INVALID_CRED\","
                    + "\"message\":\"Invalid credentials\",\"details\":{}}}")));

        assertThatThrownBy(() -> adapter.login(CREDENTIALS, DEVICE_ID))
            .isInstanceOf(PicnicLoginFailedException.class)
            .hasMessageContaining("AUTH_INVALID_CRED");
    }

    @Test
    void loginThrowsLoginFailedWhenNestedErrorObjectHasNoCode() {
        wireMockServer.stubFor(post(urlPathEqualTo("/user/login"))
            .willReturn(aResponse().withStatus(401)
                .withHeader("Content-Type", "application/json")
                .withBody("{\"error\":{\"message\":\"Invalid credentials\"}}")));

        assertThatThrownBy(() -> adapter.login(CREDENTIALS, DEVICE_ID))
            .isInstanceOf(PicnicLoginFailedException.class)
            .hasMessageContaining("unknown error");
    }

    @Test
    void loginThrowsLoginFailedOn200WithPlainErrorBody() {
        wireMockServer.stubFor(post(urlPathEqualTo("/user/login"))
            .willReturn(aResponse().withStatus(200)
                .withHeader("Content-Type", "application/json")
                .withBody("{\"error\": \"invalid credentials\"}")));

        assertThatThrownBy(() -> adapter.login(CREDENTIALS, DEVICE_ID))
            .isInstanceOf(PicnicLoginFailedException.class)
            .hasMessageContaining("invalid credentials");
    }

    @Test
    void loginThrowsUnavailableOn5xx() {
        wireMockServer.stubFor(post(urlPathEqualTo("/user/login"))
            .willReturn(aResponse().withStatus(500)));

        assertThatThrownBy(() -> adapter.login(CREDENTIALS, DEVICE_ID))
            .isInstanceOf(PicnicUnavailableException.class);
    }

    @Test
    void loginThrowsUnavailableWhenAuthHeaderMissingOn200() {
        wireMockServer.stubFor(post(urlPathEqualTo("/user/login"))
            .willReturn(aResponse().withStatus(200).withBody("{}")));

        assertThatThrownBy(() -> adapter.login(CREDENTIALS, DEVICE_ID))
            .isInstanceOf(PicnicUnavailableException.class);
    }

    @Test
    void loginThrowsUnavailableWhenLoginResponseBodyIsMalformed() {
        // A malformed body makes the error-indicator pre-check give up (tryParseJson returns
        // null) and fall through to the status/header checks — here a 200 with no auth header.
        wireMockServer.stubFor(post(urlPathEqualTo("/user/login"))
            .willReturn(aResponse().withStatus(200).withBody("not json at all")));

        assertThatThrownBy(() -> adapter.login(CREDENTIALS, DEVICE_ID))
            .isInstanceOf(PicnicUnavailableException.class);
    }

    @Test
    void loginThrowsUnavailableWhenPicnicIsUnreachable() {
        // No stub: point the adapter at a port nothing listens on so HttpClient.send() throws a
        // real IOException, exercising the send() catch block rather than response handling.
        PicnicHttpAdapter unreachable = new PicnicHttpAdapter(new ObjectMapper(), "http://localhost:1");

        assertThatThrownBy(() -> unreachable.login(CREDENTIALS, DEVICE_ID))
            .isInstanceOf(PicnicUnavailableException.class)
            .hasMessageContaining("Failed to reach Picnic");
    }

    // --- second factor -------------------------------------------------------------------

    @Test
    void sendSecondFactorAsksPicnicForAnSmsCode() {
        wireMockServer.stubFor(post(urlPathEqualTo("/user/2fa/generate"))
            .willReturn(aResponse().withStatus(204)));

        assertThatCode(() -> adapter.sendSecondFactor(SESSION)).doesNotThrowAnyException();

        wireMockServer.verify(postRequestedFor(urlPathEqualTo("/user/2fa/generate"))
            .withHeader("x-picnic-auth", equalTo("session-key"))
            .withHeader("x-picnic-did", equalTo(DEVICE_ID)));
    }

    @Test
    void sendSecondFactorThrowsUnavailableWhenPicnicRefuses() {
        wireMockServer.stubFor(post(urlPathEqualTo("/user/2fa/generate"))
            .willReturn(aResponse().withStatus(500)));

        assertThatThrownBy(() -> adapter.sendSecondFactor(SESSION))
            .isInstanceOf(PicnicUnavailableException.class);
    }

    @Test
    void verifySecondFactorReturnsTheUpgradedSession() {
        // Verification mints a NEW key; keeping the provisional one would leave us storing a
        // session that can never work, which is invisible until the next call 403s.
        wireMockServer.stubFor(post(urlPathEqualTo("/user/2fa/verify"))
            .willReturn(aResponse().withStatus(204).withHeader("x-picnic-auth", "verified-key")));

        PicnicSession upgraded = adapter.verifySecondFactor(SESSION, "252000");

        assertThat(upgraded.authKey()).isEqualTo("verified-key");
        assertThat(upgraded.deviceId()).isEqualTo(DEVICE_ID);
    }

    @Test
    void verifySecondFactorThrowsLoginFailedOnAWrongCode() {
        wireMockServer.stubFor(post(urlPathEqualTo("/user/2fa/verify"))
            .willReturn(aResponse().withStatus(400)
                .withHeader("Content-Type", "application/json")
                .withBody("{\"error\":{\"code\":\"AUTH_INVALID_CODE\"}}")));

        assertThatThrownBy(() -> adapter.verifySecondFactor(SESSION, "000000"))
            .isInstanceOf(PicnicLoginFailedException.class)
            .hasMessageContaining("AUTH_INVALID_CODE");
    }

    @Test
    void verifySecondFactorThrowsLoginFailedOn4xxWithNoParseableBody() {
        wireMockServer.stubFor(post(urlPathEqualTo("/user/2fa/verify"))
            .willReturn(aResponse().withStatus(403).withBody("nope")));

        assertThatThrownBy(() -> adapter.verifySecondFactor(SESSION, "000000"))
            .isInstanceOf(PicnicLoginFailedException.class);
    }

    @Test
    void verifySecondFactorThrowsUnavailableOn5xx() {
        wireMockServer.stubFor(post(urlPathEqualTo("/user/2fa/verify"))
            .willReturn(aResponse().withStatus(503)));

        assertThatThrownBy(() -> adapter.verifySecondFactor(SESSION, "252000"))
            .isInstanceOf(PicnicUnavailableException.class);
    }

    @Test
    void verifySecondFactorThrowsUnavailableWhenNoUpgradedKeyComesBack() {
        wireMockServer.stubFor(post(urlPathEqualTo("/user/2fa/verify"))
            .willReturn(aResponse().withStatus(204)));

        assertThatThrownBy(() -> adapter.verifySecondFactor(SESSION, "252000"))
            .isInstanceOf(PicnicUnavailableException.class)
            .hasMessageContaining("x-picnic-auth");
    }

    // --- search --------------------------------------------------------------------------

    @Test
    void searchArticlesExtractsProductsFromTheRealPageTree() {
        stubSearch(REAL_SEARCH_PAGE);

        List<ArticleSuggestion> results = adapter.searchArticles(SESSION, "Milch");

        // The SELLING_UNIT_MUTATION sibling carries a selling-unit id but no product — it must
        // not show up as a third, nameless suggestion.
        assertThat(results).hasSize(2);

        ArticleSuggestion first = results.getFirst();
        assertThat(first.id()).isEqualTo("s1018863");
        assertThat(first.name()).isEqualTo("Edeka Bio Fettarme H-Milch 1,5%");
        assertThat(first.priceCents()).isEqualTo(115);
        assertThat(first.unit()).isEqualTo("1L");
        assertThat(first.imageUrl()).endsWith("/static/images/951c3a9070a5cdd5/medium.png");

        // Optional fields stay optional even though the live API populated all of them.
        ArticleSuggestion second = results.get(1);
        assertThat(second.id()).isEqualTo("s1020462");
        assertThat(second.priceCents()).isNull();
        assertThat(second.unit()).isNull();
        assertThat(second.imageUrl()).isNull();
    }

    @Test
    void searchArticlesSendsTheSessionAndItsDeviceId() {
        stubSearch(REAL_SEARCH_PAGE);

        adapter.searchArticles(SESSION, "Milch");

        wireMockServer.verify(com.github.tomakehurst.wiremock.client.WireMock
            .getRequestedFor(urlPathEqualTo("/pages/search-page-results"))
            .withHeader("x-picnic-auth", equalTo("session-key"))
            .withHeader("x-picnic-did", equalTo(DEVICE_ID)));
    }

    @Test
    void searchArticlesSkipsTilesWithoutUsableProductData() {
        stubSearch("""
            {"layout": {"children": [
              {"type": "SELLING_UNIT_TILE"},
              {"type": "SELLING_UNIT_TILE", "sellingUnit": {"id": "s1", "name": "Real"}},
              {"type": "SELLING_UNIT_TILE", "sellingUnit": {"id": "s2"}},
              {"type": "SELLING_UNIT_TILE", "sellingUnit": "not-an-object"}
            ]}}
            """);

        List<ArticleSuggestion> results = adapter.searchArticles(SESSION, "Milch");

        assertThat(results).extracting(ArticleSuggestion::id).containsExactly("s1");
    }

    @Test
    void searchArticlesCapsResultsAtTwenty() {
        StringBuilder tiles = new StringBuilder("{\"layout\":{\"children\":[");
        for (int i = 0; i < 25; i++) {
            if (i > 0) tiles.append(",");
            tiles.append("{\"type\":\"SELLING_UNIT_TILE\",\"sellingUnit\":{\"id\":\"s")
                 .append(i).append("\",\"name\":\"Item ").append(i).append("\"}}");
        }
        tiles.append("]}}");
        stubSearch(tiles.toString());

        List<ArticleSuggestion> results = adapter.searchArticles(SESSION, "Milch");

        // A live response carried 120 products; the adapter bounds what it collects and the
        // application layer trims further to a top-5 picker.
        assertThat(results).hasSize(20);
    }

    @Test
    void searchArticlesIgnoresScalarsInTheTreeDefensively() {
        stubSearch("""
            {"layout": ["unexpected scalar", 42, null,
              {"type": "SELLING_UNIT_TILE", "sellingUnit": {"id": "s1", "name": "Real Item"}}]}
            """);

        List<ArticleSuggestion> results = adapter.searchArticles(SESSION, "Milch");

        assertThat(results).hasSize(1);
        assertThat(results.getFirst().name()).isEqualTo("Real Item");
    }

    @Test
    void searchArticlesThrowsSessionExpiredWhenPicnicRefusesTheSession() {
        stubSearchStatus(403);

        assertThatThrownBy(() -> adapter.searchArticles(SESSION, "Milch"))
            .isInstanceOf(PicnicSessionExpiredException.class);
    }

    @Test
    void searchArticlesThrowsSessionExpiredOn401() {
        stubSearchStatus(401);

        assertThatThrownBy(() -> adapter.searchArticles(SESSION, "Milch"))
            .isInstanceOf(PicnicSessionExpiredException.class);
    }

    @Test
    void searchArticlesThrowsUnavailableOn5xx() {
        stubSearchStatus(500);

        assertThatThrownBy(() -> adapter.searchArticles(SESSION, "Milch"))
            .isInstanceOf(PicnicUnavailableException.class);
    }

    @Test
    void searchArticlesThrowsUnavailableOnMalformedJsonBody() {
        stubSearch("this is not { json");

        assertThatThrownBy(() -> adapter.searchArticles(SESSION, "Milch"))
            .isInstanceOf(PicnicUnavailableException.class);
    }

    // --- cart ----------------------------------------------------------------------------

    @Test
    void addToCartPostsTheSellingUnitId() {
        wireMockServer.stubFor(post(urlPathEqualTo("/cart/add_product"))
            .willReturn(aResponse().withStatus(200)));

        assertThatCode(() -> adapter.addToCart(SESSION, "s1018863", 2)).doesNotThrowAnyException();

        wireMockServer.verify(postRequestedFor(urlPathEqualTo("/cart/add_product"))
            .withRequestBody(com.github.tomakehurst.wiremock.client.WireMock
                .equalToJson("{\"product_id\":\"s1018863\",\"count\":2}")));
    }

    @Test
    void addToCartThrowsSessionExpiredWhenPicnicRefusesTheSession() {
        wireMockServer.stubFor(post(urlPathEqualTo("/cart/add_product"))
            .willReturn(aResponse().withStatus(403)));

        assertThatThrownBy(() -> adapter.addToCart(SESSION, "s1", 1))
            .isInstanceOf(PicnicSessionExpiredException.class);
    }

    @Test
    void addToCartThrowsUnavailableOnFailure() {
        wireMockServer.stubFor(post(urlPathEqualTo("/cart/add_product"))
            .willReturn(aResponse().withStatus(500)));

        assertThatThrownBy(() -> adapter.addToCart(SESSION, "s1", 2))
            .isInstanceOf(PicnicUnavailableException.class);
    }

    // --- helpers -------------------------------------------------------------------------

    private void stubSuccessfulLogin() {
        wireMockServer.stubFor(post(urlPathEqualTo("/user/login"))
            .willReturn(aResponse().withStatus(200).withHeader("x-picnic-auth", "some-token")));
    }

    private void stubSearch(String body) {
        wireMockServer.stubFor(get(urlPathEqualTo("/pages/search-page-results"))
            .willReturn(aResponse().withStatus(200)
                .withHeader("Content-Type", "application/json")
                .withBody(body)));
    }

    private void stubSearchStatus(int status) {
        wireMockServer.stubFor(get(urlPathEqualTo("/pages/search-page-results"))
            .willReturn(aResponse().withStatus(status)));
    }
}
