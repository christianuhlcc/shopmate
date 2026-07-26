package com.shopmate.adapter.out.picnic;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.shopmate.domain.model.ArticleSuggestion;
import com.shopmate.domain.model.PicnicCredentials;
import com.shopmate.domain.model.PicnicLoginFailedException;
import com.shopmate.domain.model.PicnicUnavailableException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Plain JUnit 5 test — no Spring context, no Docker. WireMock stubs Picnic's storefront API
 * (see PicnicHttpAdapter's class javadoc for the reverse-engineered wire protocol).
 */
class PicnicHttpAdapterTest {

    private static final PicnicCredentials CREDENTIALS = new PicnicCredentials("user@example.com", "5f4dcc3b5aa765d61d8327deb882cf99");

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

    @Test
    void verifyLoginSucceedsWhenPicnicReturnsAuthHeader() {
        wireMockServer.stubFor(post(urlPathEqualTo("/user/login"))
            .willReturn(aResponse().withStatus(200).withHeader("x-picnic-auth", "some-token")));

        assertThatCode(() -> adapter.verifyLogin(CREDENTIALS)).doesNotThrowAnyException();
    }

    @Test
    void verifyLoginThrowsLoginFailedOn400() {
        wireMockServer.stubFor(post(urlPathEqualTo("/user/login"))
            .willReturn(aResponse().withStatus(400).withBody("{}")));

        assertThatThrownBy(() -> adapter.verifyLogin(CREDENTIALS))
            .isInstanceOf(PicnicLoginFailedException.class);
    }

    @Test
    void verifyLoginThrowsLoginFailedOn200WithErrorCodeBody() {
        wireMockServer.stubFor(post(urlPathEqualTo("/user/login"))
            .willReturn(aResponse().withStatus(200)
                .withHeader("Content-Type", "application/json")
                .withBody("{\"error_code\": \"AUTH_INVALID_CRED\", \"error\": \"invalid credentials\"}")));

        assertThatThrownBy(() -> adapter.verifyLogin(CREDENTIALS))
            .isInstanceOf(PicnicLoginFailedException.class)
            .hasMessageContaining("AUTH_INVALID_CRED");
    }

    @Test
    void verifyLoginThrowsUnavailableOn5xx() {
        wireMockServer.stubFor(post(urlPathEqualTo("/user/login"))
            .willReturn(aResponse().withStatus(500)));

        assertThatThrownBy(() -> adapter.verifyLogin(CREDENTIALS))
            .isInstanceOf(PicnicUnavailableException.class);
    }

    @Test
    void verifyLoginThrowsUnavailableWhenAuthHeaderMissingOn200() {
        wireMockServer.stubFor(post(urlPathEqualTo("/user/login"))
            .willReturn(aResponse().withStatus(200).withBody("{}")));

        assertThatThrownBy(() -> adapter.verifyLogin(CREDENTIALS))
            .isInstanceOf(PicnicUnavailableException.class);
    }

    @Test
    void searchArticlesFlattensNestedCategoriesAndHandlesMissingFieldsDefensively() {
        stubSuccessfulLogin();

        String searchResponse = """
            [
              {
                "name": "Zuivel",
                "items": [
                  {
                    "name": "Melk",
                    "items": [
                      {
                        "id": "10001",
                        "name": "Bio Vollmilch 1L",
                        "unit_quantity": "1L",
                        "display_price": 129,
                        "image_id": "abc123"
                      },
                      {
                        "id": "10002",
                        "name": "Haferdrink 1L"
                      }
                    ]
                  }
                ]
              }
            ]
            """;

        wireMockServer.stubFor(get(urlPathEqualTo("/search"))
            .willReturn(aResponse().withStatus(200)
                .withHeader("Content-Type", "application/json")
                .withBody(searchResponse)));

        List<ArticleSuggestion> results = adapter.searchArticles(CREDENTIALS, "Milch");

        assertThat(results).hasSize(2);

        ArticleSuggestion withAllFields = results.stream().filter(a -> a.id().equals("10001")).findFirst().orElseThrow();
        assertThat(withAllFields.name()).isEqualTo("Bio Vollmilch 1L");
        assertThat(withAllFields.unit()).isEqualTo("1L");
        assertThat(withAllFields.priceCents()).isEqualTo(129);
        assertThat(withAllFields.imageUrl()).contains("abc123");

        ArticleSuggestion missingFields = results.stream().filter(a -> a.id().equals("10002")).findFirst().orElseThrow();
        assertThat(missingFields.name()).isEqualTo("Haferdrink 1L");
        assertThat(missingFields.unit()).isNull();
        assertThat(missingFields.priceCents()).isNull();
        assertThat(missingFields.imageUrl()).isNull();
    }

    @Test
    void searchArticlesThrowsUnavailableOn5xx() {
        stubSuccessfulLogin();

        wireMockServer.stubFor(get(urlPathEqualTo("/search"))
            .willReturn(aResponse().withStatus(500)));

        assertThatThrownBy(() -> adapter.searchArticles(CREDENTIALS, "Milch"))
            .isInstanceOf(PicnicUnavailableException.class);
    }

    @Test
    void searchArticlesThrowsUnavailableOnMalformedJsonBody() {
        stubSuccessfulLogin();

        wireMockServer.stubFor(get(urlPathEqualTo("/search"))
            .willReturn(aResponse().withStatus(200)
                .withHeader("Content-Type", "application/json")
                .withBody("this is not { json")));

        assertThatThrownBy(() -> adapter.searchArticles(CREDENTIALS, "Milch"))
            .isInstanceOf(PicnicUnavailableException.class);
    }

    @Test
    void addToCartSucceedsOn200() {
        stubSuccessfulLogin();

        wireMockServer.stubFor(post(urlPathEqualTo("/cart/add_product"))
            .willReturn(aResponse().withStatus(200)));

        assertThatCode(() -> adapter.addToCart(CREDENTIALS, "10001", 2)).doesNotThrowAnyException();
    }

    @Test
    void addToCartThrowsUnavailableOnFailure() {
        stubSuccessfulLogin();

        wireMockServer.stubFor(post(urlPathEqualTo("/cart/add_product"))
            .willReturn(aResponse().withStatus(500)));

        assertThatThrownBy(() -> adapter.addToCart(CREDENTIALS, "10001", 2))
            .isInstanceOf(PicnicUnavailableException.class);
    }

    private void stubSuccessfulLogin() {
        wireMockServer.stubFor(post(urlPathEqualTo("/user/login"))
            .willReturn(aResponse().withStatus(200).withHeader("x-picnic-auth", "some-token")));
    }
}
