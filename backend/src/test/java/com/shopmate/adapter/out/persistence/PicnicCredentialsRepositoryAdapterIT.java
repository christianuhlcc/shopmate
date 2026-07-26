package com.shopmate.adapter.out.persistence;

import com.shopmate.domain.model.PicnicAccountLink;
import com.shopmate.domain.model.PicnicLinkState;
import com.shopmate.domain.model.PicnicSession;
import com.shopmate.domain.model.User;
import com.shopmate.domain.port.out.PicnicCredentialsRepository;
import com.shopmate.domain.port.out.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.Optional;
import java.util.UUID;

import javax.sql.DataSource;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Testcontainers
@ActiveProfiles("integration-test")
class PicnicCredentialsRepositoryAdapterIT {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16")
        .withDatabaseName("shopmate_test")
        .withUsername("test")
        .withPassword("test");

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @Autowired PicnicCredentialsRepository picnicCredentialsRepository;
    @Autowired UserRepository userRepository;
    @Autowired DataSource dataSource;

    private UUID newUser(String email) {
        return userRepository.save(new User(UUID.randomUUID(), email, "Test User", null, null)).id();
    }

    private static PicnicAccountLink link(String email, String authKey, PicnicLinkState state) {
        return new PicnicAccountLink(email, new PicnicSession(authKey, "A1B2C3D4E5F60718"), state);
    }

    @Test
    void saveThenFindByUserIdRoundTripsTheDecryptedSession() {
        UUID userId = newUser("picnic-roundtrip@test.com");

        picnicCredentialsRepository.save(userId, link("picnic@example.com", "auth-key-123", PicnicLinkState.LINKED));

        Optional<PicnicAccountLink> found = picnicCredentialsRepository.findByUserId(userId);
        assertThat(found).isPresent();
        assertThat(found.get().email()).isEqualTo("picnic@example.com");
        assertThat(found.get().session().authKey()).isEqualTo("auth-key-123");
        assertThat(found.get().session().deviceId()).isEqualTo("A1B2C3D4E5F60718");
        assertThat(found.get().isLinked()).isTrue();
    }

    @Test
    void aPendingLinkSurvivesTheRoundTripAsPending() {
        // The provisional session has to be readable after a restart or the user is stranded
        // mid-link with no way to finish 2FA.
        UUID userId = newUser("picnic-pending@test.com");

        picnicCredentialsRepository.save(userId,
            link("pending@example.com", "provisional-key", PicnicLinkState.PENDING_SECOND_FACTOR));

        Optional<PicnicAccountLink> found = picnicCredentialsRepository.findByUserId(userId);
        assertThat(found).isPresent();
        assertThat(found.get().state()).isEqualTo(PicnicLinkState.PENDING_SECOND_FACTOR);
        assertThat(found.get().isLinked()).isFalse();
        assertThat(found.get().session().authKey()).isEqualTo("provisional-key");
    }

    @Test
    void storedBytesAreNotThePlaintextSessionKey() throws Exception {
        UUID userId = newUser("picnic-encrypted@test.com");
        String plaintextKey = "a-very-real-looking-picnic-auth-key";
        picnicCredentialsRepository.save(userId, link("picnic2@example.com", plaintextKey, PicnicLinkState.LINKED));

        byte[] rawStored = queryRawEncryptedBytes(userId);

        assertThat(rawStored).isNotNull();
        assertThat(rawStored).isNotEqualTo(plaintextKey.getBytes(StandardCharsets.UTF_8));
    }

    @Test
    void saveTwiceForSameUserUpsertsRatherThanDuplicating() {
        // Completing 2FA overwrites a pending row with the verified session — if that duplicated
        // instead, the pending row could win a later read and the link would look broken.
        UUID userId = newUser("picnic-upsert@test.com");
        picnicCredentialsRepository.save(userId,
            link("first@example.com", "provisional", PicnicLinkState.PENDING_SECOND_FACTOR));
        picnicCredentialsRepository.save(userId,
            link("second@example.com", "verified", PicnicLinkState.LINKED));

        Optional<PicnicAccountLink> found = picnicCredentialsRepository.findByUserId(userId);
        assertThat(found).isPresent();
        assertThat(found.get().email()).isEqualTo("second@example.com");
        assertThat(found.get().session().authKey()).isEqualTo("verified");
        assertThat(found.get().state()).isEqualTo(PicnicLinkState.LINKED);

        assertThat(countRows(userId)).isEqualTo(1);
    }

    @Test
    void deleteRemovesRowAndSubsequentFindIsEmpty() {
        UUID userId = newUser("picnic-delete@test.com");
        picnicCredentialsRepository.save(userId, link("todelete@example.com", "key", PicnicLinkState.LINKED));

        picnicCredentialsRepository.delete(userId);

        assertThat(picnicCredentialsRepository.findByUserId(userId)).isEmpty();
        assertThat(countRows(userId)).isZero();
    }

    @Test
    void deleteOnUserWithNoCredentialsDoesNotThrow() {
        UUID userId = newUser("picnic-nocreds@test.com");

        picnicCredentialsRepository.delete(userId);

        assertThat(picnicCredentialsRepository.findByUserId(userId)).isEmpty();
    }

    private byte[] queryRawEncryptedBytes(UUID userId) throws Exception {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                 "SELECT auth_key_encrypted FROM picnic_credentials WHERE user_id = ?")) {
            statement.setObject(1, userId);
            try (ResultSet resultSet = statement.executeQuery()) {
                assertThat(resultSet.next()).isTrue();
                return resultSet.getBytes(1);
            }
        }
    }

    private int countRows(UUID userId) {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                 "SELECT COUNT(*) FROM picnic_credentials WHERE user_id = ?")) {
            statement.setObject(1, userId);
            try (ResultSet resultSet = statement.executeQuery()) {
                resultSet.next();
                return resultSet.getInt(1);
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
