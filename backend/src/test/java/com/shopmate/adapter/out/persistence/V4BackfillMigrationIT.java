package com.shopmate.adapter.out.persistence;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies V4__groups_and_invites.sql in isolation via the Flyway Java API, driving
 * migrations to a specific target version rather than through Spring Boot's
 * auto-configured Flyway (which always migrates straight to latest). This lets the test
 * seed pre-V4 (V3) data with plain JDBC, apply V4 on top, and assert the backfill.
 *
 * Reuses the postgres:16 Testcontainers setup from ShoppingListRepositoryAdapterIT.
 */
@Testcontainers
class V4BackfillMigrationIT {

    static final UUID DEFAULT_GROUP_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16")
        .withDatabaseName("shopmate_v4_test")
        .withUsername("test")
        .withPassword("test");

    private static Flyway flywayTo(MigrationVersion target) {
        return Flyway.configure()
            .dataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())
            .locations("classpath:db/migration")
            .cleanDisabled(false)
            .target(target)
            .load();
    }

    private static Connection connect() throws SQLException {
        return DriverManager.getConnection(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
    }

    @BeforeEach
    void resetSchema() {
        // Start every test from a blank schema so the two scenarios (users pre-exist / db empty)
        // don't interfere with each other regardless of execution order.
        flywayTo(MigrationVersion.LATEST).clean();
    }

    @Test
    void backfillsDefaultGroupWhenUsersAlreadyExist() throws SQLException {
        flywayTo(MigrationVersion.fromVersion("3")).migrate();

        UUID userId = UUID.randomUUID();
        UUID listId = UUID.randomUUID();
        seedV3UserListAndMembership(userId, listId);

        flywayTo(MigrationVersion.fromVersion("4")).migrate();

        try (Connection conn = connect()) {
            try (PreparedStatement groups = conn.prepareStatement("SELECT id, name FROM groups")) {
                ResultSet rs = groups.executeQuery();
                assertThat(rs.next()).as("exactly one default group must exist").isTrue();
                assertThat(rs.getObject("id", UUID.class)).isEqualTo(DEFAULT_GROUP_ID);
                assertThat(rs.getString("name")).isEqualTo("ShopMate");
                assertThat(rs.next()).as("no second group row").isFalse();
            }

            assertThat(groupIdOf(conn, "users", "id", userId)).isEqualTo(DEFAULT_GROUP_ID);
            assertThat(groupIdOf(conn, "shopping_lists", "id", listId)).isEqualTo(DEFAULT_GROUP_ID);
        }
    }

    @Test
    void emptyDatabaseGetsNoDefaultGroupOnV4() throws SQLException {
        flywayTo(MigrationVersion.fromVersion("4")).migrate();

        try (Connection conn = connect();
             PreparedStatement count = conn.prepareStatement("SELECT count(*) FROM groups")) {
            ResultSet rs = count.executeQuery();
            rs.next();
            assertThat(rs.getLong(1)).isZero();
        }
    }

    private void seedV3UserListAndMembership(UUID userId, UUID listId) throws SQLException {
        try (Connection conn = connect()) {
            try (PreparedStatement insertUser = conn.prepareStatement(
                "INSERT INTO users (id, email, display_name, created_at) VALUES (?, ?, ?, ?)")) {
                insertUser.setObject(1, userId);
                insertUser.setString(2, "backfill-user@test.com");
                insertUser.setString(3, "Backfill User");
                insertUser.setObject(4, Instant.now().atOffset(java.time.ZoneOffset.UTC));
                insertUser.executeUpdate();
            }
            try (PreparedStatement insertList = conn.prepareStatement(
                "INSERT INTO shopping_lists (id, name, owner_id, created_at) VALUES (?, ?, ?, ?)")) {
                insertList.setObject(1, listId);
                insertList.setString(2, "Pre-V4 List");
                insertList.setObject(3, userId);
                insertList.setObject(4, Instant.now().atOffset(java.time.ZoneOffset.UTC));
                insertList.executeUpdate();
            }
            try (PreparedStatement insertMember = conn.prepareStatement(
                "INSERT INTO list_members (list_id, user_id) VALUES (?, ?)")) {
                insertMember.setObject(1, listId);
                insertMember.setObject(2, userId);
                insertMember.executeUpdate();
            }
        }
    }

    private UUID groupIdOf(Connection conn, String table, String idColumn, UUID id) throws SQLException {
        try (PreparedStatement stmt = conn.prepareStatement(
            "SELECT group_id FROM " + table + " WHERE " + idColumn + " = ?")) {
            stmt.setObject(1, id);
            ResultSet rs = stmt.executeQuery();
            assertThat(rs.next()).as(table + " row must exist").isTrue();
            return rs.getObject("group_id", UUID.class);
        }
    }
}
