package com.shopmate.adapter.out.persistence;

import com.shopmate.adapter.out.persistence.repository.SpringDataShoppingListRepository;
import com.shopmate.domain.model.Group;
import com.shopmate.domain.model.ShoppingList;
import com.shopmate.domain.model.User;
import com.shopmate.domain.port.out.SectionCorrectionRepository;
import com.shopmate.domain.port.out.GroupRepository;
import com.shopmate.domain.port.out.ShoppingListRepository;
import com.shopmate.domain.port.out.UserRepository;
import com.shopmate.domain.section.Section;
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
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Testcontainers
@ActiveProfiles("integration-test")
class SectionCorrectionRepositoryAdapterIT {

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

    @Autowired SectionCorrectionRepository sectionCorrectionRepository;
    @Autowired ShoppingListRepository listRepository;
    @Autowired UserRepository userRepository;
    @Autowired SpringDataShoppingListRepository listJpa;
    @Autowired GroupRepository groupRepository;

    private static int ownerCounter = 0;

    private UUID createListAndOwner() {
        // shopping_lists.group_id is NOT NULL and FK-constrained after V5.
        UUID groupId = groupRepository.save(new Group(UUID.randomUUID(), "Test Group", Instant.now())).id();
        User owner = userRepository.save(new User(UUID.randomUUID(), nextOwnerEmail(), "Owner", null, groupId));
        UUID listId = UUID.randomUUID();
        ShoppingList list = new ShoppingList(listId, "Section Correction Test", owner.id(),
            groupId, Map.of(), Instant.now());
        listRepository.save(list);
        return listId;
    }

    private static synchronized String nextOwnerEmail() {
        return "sc-owner-" + (ownerCounter++) + "@test.com";
    }

    @Test
    void roundTripsAStoredCorrection() {
        UUID listId = createListAndOwner();
        User modifier = userRepository.save(new User(UUID.randomUUID(), "sc-mod1@test.com", "Modifier", null, null));

        assertThat(sectionCorrectionRepository.find(listId, "hefe")).isEmpty();

        sectionCorrectionRepository.upsert(listId, "hefe", Section.BROT_BACKWAREN, 100L, modifier.id());

        assertThat(sectionCorrectionRepository.find(listId, "hefe")).contains(Section.BROT_BACKWAREN);
    }

    @Test
    void newerTimestampOverwritesOlderCorrection() {
        UUID listId = createListAndOwner();
        User modifier = userRepository.save(new User(UUID.randomUUID(), "sc-mod2@test.com", "Modifier", null, null));

        sectionCorrectionRepository.upsert(listId, "hefe", Section.BROT_BACKWAREN, 100L, modifier.id());
        sectionCorrectionRepository.upsert(listId, "hefe", Section.GETRAENKE, 200L, modifier.id());

        assertThat(sectionCorrectionRepository.find(listId, "hefe")).contains(Section.GETRAENKE);
    }

    @Test
    void olderTimestampDoesNotOverwriteNewerCorrection() {
        UUID listId = createListAndOwner();
        User modifier = userRepository.save(new User(UUID.randomUUID(), "sc-mod3@test.com", "Modifier", null, null));

        sectionCorrectionRepository.upsert(listId, "hefe", Section.GETRAENKE, 200L, modifier.id());
        sectionCorrectionRepository.upsert(listId, "hefe", Section.BROT_BACKWAREN, 100L, modifier.id());

        assertThat(sectionCorrectionRepository.find(listId, "hefe")).contains(Section.GETRAENKE);
    }

    @Test
    void correctionsAreScopedPerList() {
        UUID listIdA = createListAndOwner();
        UUID listIdB = createListAndOwner();
        User modifier = userRepository.save(new User(UUID.randomUUID(), "sc-mod4@test.com", "Modifier", null, null));

        sectionCorrectionRepository.upsert(listIdA, "hefe", Section.BROT_BACKWAREN, 100L, modifier.id());

        assertThat(sectionCorrectionRepository.find(listIdA, "hefe")).contains(Section.BROT_BACKWAREN);
        assertThat(sectionCorrectionRepository.find(listIdB, "hefe")).isEmpty();
    }

    @Test
    void correctionIsDeletedWhenListIsDeleted() {
        UUID listId = createListAndOwner();
        User modifier = userRepository.save(new User(UUID.randomUUID(), "sc-mod5@test.com", "Modifier", null, null));

        sectionCorrectionRepository.upsert(listId, "hefe", Section.BROT_BACKWAREN, 100L, modifier.id());
        assertThat(sectionCorrectionRepository.find(listId, "hefe")).isPresent();

        listJpa.deleteById(listId);

        assertThat(sectionCorrectionRepository.find(listId, "hefe")).isEmpty();
    }
}
