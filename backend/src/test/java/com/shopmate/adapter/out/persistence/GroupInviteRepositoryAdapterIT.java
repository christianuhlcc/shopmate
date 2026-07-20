package com.shopmate.adapter.out.persistence;

import com.shopmate.domain.model.Group;
import com.shopmate.domain.model.InviteCode;
import com.shopmate.domain.model.InviteType;
import com.shopmate.domain.model.User;
import com.shopmate.domain.port.out.GroupRepository;
import com.shopmate.domain.port.out.InviteCodeRepository;
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

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Testcontainers
@ActiveProfiles("integration-test")
class GroupInviteRepositoryAdapterIT {

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

    @Autowired GroupRepository groupRepository;
    @Autowired InviteCodeRepository inviteCodeRepository;
    @Autowired UserRepository userRepository;

    @Test
    void saveAndFindGroup() {
        Group group = groupRepository.save(new Group(UUID.randomUUID(), "Test Household", Instant.now()));

        var found = groupRepository.findById(group.id());
        assertThat(found).isPresent();
        assertThat(found.get().name()).isEqualTo("Test Household");
    }

    @Test
    void findInviteByCode() {
        Group group = groupRepository.save(new Group(UUID.randomUUID(), "Invite Group", Instant.now()));
        User creator = userRepository.save(new User(UUID.randomUUID(), "creator@test.com", "Creator", null, group.id()));

        InviteCode invite = inviteCodeRepository.save(new InviteCode(
            UUID.randomUUID(), "ABCD2345", InviteType.JOIN_GROUP, group.id(), creator.id(),
            Instant.now(), Instant.now().plus(7, ChronoUnit.DAYS), null, null));

        var found = inviteCodeRepository.findByCode("ABCD2345");
        assertThat(found).isPresent();
        assertThat(found.get().id()).isEqualTo(invite.id());
        assertThat(found.get().type()).isEqualTo(InviteType.JOIN_GROUP);
        assertThat(found.get().groupId()).isEqualTo(group.id());
        assertThat(found.get().isUsed()).isFalse();
    }

    @Test
    void markUsedSucceedsOnceThenFailsOnConcurrentRedemption() {
        Group group = groupRepository.save(new Group(UUID.randomUUID(), "Race Group", Instant.now()));
        User creator = userRepository.save(new User(UUID.randomUUID(), "race-creator@test.com", "Creator", null, group.id()));
        User redeemer = userRepository.save(new User(UUID.randomUUID(), "race-redeemer@test.com", "Redeemer", null, null));

        InviteCode invite = inviteCodeRepository.save(new InviteCode(
            UUID.randomUUID(), "RACE2345", InviteType.JOIN_GROUP, group.id(), creator.id(),
            Instant.now(), Instant.now().plus(7, ChronoUnit.DAYS), null, null));

        boolean first = inviteCodeRepository.markUsed(invite.id(), redeemer.id(), Instant.now());
        boolean second = inviteCodeRepository.markUsed(invite.id(), redeemer.id(), Instant.now());

        assertThat(first).isTrue();
        assertThat(second).isFalse();

        var found = inviteCodeRepository.findByCode("RACE2345");
        assertThat(found).isPresent();
        assertThat(found.get().isUsed()).isTrue();
        assertThat(found.get().usedBy()).isEqualTo(redeemer.id());
    }

    @Test
    void findAllByGroupIdReturnsGroupMembers() {
        Group group = groupRepository.save(new Group(UUID.randomUUID(), "Members Group", Instant.now()));
        Group otherGroup = groupRepository.save(new Group(UUID.randomUUID(), "Other Group", Instant.now()));

        User memberOne = userRepository.save(new User(UUID.randomUUID(), "m1@test.com", "Member One", null, group.id()));
        User memberTwo = userRepository.save(new User(UUID.randomUUID(), "m2@test.com", "Member Two", null, group.id()));
        userRepository.save(new User(UUID.randomUUID(), "outsider@test.com", "Outsider", null, otherGroup.id()));

        List<User> members = userRepository.findAllByGroupId(group.id());
        assertThat(members).extracting(User::id).containsExactlyInAnyOrder(memberOne.id(), memberTwo.id());
    }

    /**
     * Proves the User.groupId round-trip through the real adapter (not a mock). This is the
     * regression the task called out: GoogleOAuth2SuccessHandler preserves existing.groupId()
     * across re-login, but that preservation was a no-op while toDomain() hardcoded null and
     * save() never persisted groupId — every login would silently eject the user from their
     * group.
     */
    @Test
    void userGroupIdSurvivesSaveAndReloadRoundTrip() {
        Group group = groupRepository.save(new Group(UUID.randomUUID(), "Roundtrip Group", Instant.now()));

        User user = userRepository.save(new User(UUID.randomUUID(), "roundtrip@test.com", "Roundtrip User", null, group.id()));
        assertThat(user.groupId()).isEqualTo(group.id());

        var reloaded = userRepository.findById(user.id());
        assertThat(reloaded).isPresent();
        assertThat(reloaded.get().groupId()).isEqualTo(group.id());

        // Simulate the GoogleOAuth2SuccessHandler re-login path: re-saving the user (as it
        // rebuilds the User record on every login) must not drop the preserved groupId.
        User resaved = userRepository.save(reloaded.get());
        assertThat(resaved.groupId()).isEqualTo(group.id());
        assertThat(userRepository.findById(user.id()).get().groupId()).isEqualTo(group.id());
    }
}
