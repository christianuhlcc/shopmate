package com.shopmate.adapter.out.persistence;

import com.shopmate.domain.model.PicnicCredentials;
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

    @Test
    void saveThenFindByUserIdRoundTripsDecryptedDigest() {
        UUID userId = newUser("picnic-roundtrip@test.com");
        PicnicCredentials credentials = new PicnicCredentials("picnic@example.com", "5f4dcc3b5aa765d61d8327deb882cf99");

        picnicCredentialsRepository.save(userId, credentials);

        Optional<PicnicCredentials> found = picnicCredentialsRepository.findByUserId(userId);
        assertThat(found).isPresent();
        assertThat(found.get().email()).isEqualTo("picnic@example.com");
        assertThat(found.get().passwordMd5Hex()).isEqualTo("5f4dcc3b5aa765d61d8327deb882cf99");
    }

    @Test
    void storedBytesAreNotThePlaintextDigest() throws Exception {
        UUID userId = newUser("picnic-encrypted@test.com");
        String plaintextDigest = "098f6bcd4621d373cade4e832627b4f6";
        picnicCredentialsRepository.save(userId, new PicnicCredentials("picnic2@example.com", plaintextDigest));

        byte[] rawStored = queryRawEncryptedBytes(userId);

        assertThat(rawStored).isNotNull();
        assertThat(rawStored).isNotEqualTo(plaintextDigest.getBytes(StandardCharsets.UTF_8));
    }

    @Test
    void saveTwiceForSameUserUpsertsRatherThanDuplicating() {
        UUID userId = newUser("picnic-upsert@test.com");
        picnicCredentialsRepository.save(userId, new PicnicCredentials("first@example.com", "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"));
        picnicCredentialsRepository.save(userId, new PicnicCredentials("second@example.com", "bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb"));

        Optional<PicnicCredentials> found = picnicCredentialsRepository.findByUserId(userId);
        assertThat(found).isPresent();
        assertThat(found.get().email()).isEqualTo("second@example.com");
        assertThat(found.get().passwordMd5Hex()).isEqualTo("bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb");

        assertThat(countRows(userId)).isEqualTo(1);
    }

    @Test
    void deleteRemovesRowAndSubsequentFindIsEmpty() {
        UUID userId = newUser("picnic-delete@test.com");
        picnicCredentialsRepository.save(userId, new PicnicCredentials("todelete@example.com", "cccccccccccccccccccccccccccccccc"));

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
                 "SELECT password_md5_encrypted FROM picnic_credentials WHERE user_id = ?")) {
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
