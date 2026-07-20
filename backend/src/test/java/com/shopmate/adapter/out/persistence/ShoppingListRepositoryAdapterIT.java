package com.shopmate.adapter.out.persistence;

import com.shopmate.domain.model.ItemChange;
import com.shopmate.domain.model.ItemField;
import com.shopmate.domain.model.Group;
import com.shopmate.domain.model.ShoppingList;
import com.shopmate.domain.model.User;
import com.shopmate.domain.port.out.GroupRepository;
import com.shopmate.domain.port.out.ShoppingListRepository;
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

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Testcontainers
@ActiveProfiles("integration-test")
class ShoppingListRepositoryAdapterIT {

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

    @Autowired ShoppingListRepository listRepository;
    @Autowired UserRepository userRepository;
    @Autowired GroupRepository groupRepository;

    /** shopping_lists.group_id is NOT NULL and FK-constrained after V5, so every fixture needs a real group row. */
    private UUID newGroup() {
        return groupRepository.save(new Group(UUID.randomUUID(), "Test Group", java.time.Instant.now())).id();
    }

    @Test
    void saveAndFindList() {
        UUID groupId = newGroup();
        User owner = userRepository.save(new User(UUID.randomUUID(), "owner@test.com", "Owner", null, groupId));
        ShoppingList list = listRepository.save(new ShoppingList(
            UUID.randomUUID(), "Groceries", owner.id(),
            groupId, java.util.Map.of(), java.time.Instant.now()));

        var found = listRepository.findById(list.id());
        assertThat(found).isPresent();
        assertThat(found.get().name()).isEqualTo("Groceries");
        assertThat(found.get().groupId()).isEqualTo(groupId);
    }

    @Test
    void findAllByGroupIdIncludesListsOwnedByOtherMembers() {
        // The core group-tenancy guarantee: a list owned by one member is visible to
        // every other member of the same group, with no per-list sharing step.
        UUID groupId = newGroup();
        UUID otherGroupId = newGroup();
        User alice = userRepository.save(new User(UUID.randomUUID(), "alice@test.com", "Alice", null, groupId));
        userRepository.save(new User(UUID.randomUUID(), "bob@test.com", "Bob", null, groupId));

        ShoppingList aliceList = listRepository.save(new ShoppingList(
            UUID.randomUUID(), "Alice's List", alice.id(),
            groupId, java.util.Map.of(), java.time.Instant.now()));
        User outsider = userRepository.save(
            new User(UUID.randomUUID(), "outsider@test.com", "Outsider", null, otherGroupId));
        ShoppingList outsiderList = listRepository.save(new ShoppingList(
            UUID.randomUUID(), "Outsider List", outsider.id(),
            otherGroupId, java.util.Map.of(), java.time.Instant.now()));

        // Bob queries by the shared group and sees Alice's list, but never the other group's.
        List<ShoppingList> lists = listRepository.findAllByGroupId(groupId);
        assertThat(lists).extracting(ShoppingList::id)
            .contains(aliceList.id())
            .doesNotContain(outsiderList.id());
    }

    @Test
    void lwwMergePersistedCorrectly() {
        UUID groupId = newGroup();
        User owner = userRepository.save(new User(UUID.randomUUID(), "lww@test.com", "LWW User", null, groupId));
        UUID listId = UUID.randomUUID();
        UUID itemId = UUID.randomUUID();

        ShoppingList list = listRepository.save(new ShoppingList(
            listId, "LWW List", owner.id(),
            groupId, java.util.Map.of(), java.time.Instant.now()));

        // Apply name change
        ItemChange change = new ItemChange(itemId, listId, ItemField.NAME, "Milk", 100L, owner.id());
        ShoppingList updated = list.applyChange(change);
        listRepository.save(updated);

        // Apply a later name change
        ItemChange change2 = new ItemChange(itemId, listId, ItemField.NAME, "Oat Milk", 200L, owner.id());
        ShoppingList updated2 = listRepository.findById(listId).get().applyChange(change2);
        listRepository.save(updated2);

        var result = listRepository.findById(listId);
        assertThat(result).isPresent();
        assertThat(result.get().items().get(itemId).name().value()).isEqualTo("Oat Milk");
    }

    @Test
    void olderChangeDoesNotOverwriteNewer() {
        UUID groupId = newGroup();
        User owner = userRepository.save(new User(UUID.randomUUID(), "stale@test.com", "Stale User", null, groupId));
        UUID listId = UUID.randomUUID();
        UUID itemId = UUID.randomUUID();

        ShoppingList list = listRepository.save(new ShoppingList(
            listId, "Stale Test", owner.id(),
            groupId, java.util.Map.of(), java.time.Instant.now()));

        // Apply newer change first
        ItemChange newer = new ItemChange(itemId, listId, ItemField.NAME, "Correct", 200L, owner.id());
        listRepository.save(list.applyChange(newer));

        // Apply older change — must NOT overwrite
        ItemChange older = new ItemChange(itemId, listId, ItemField.NAME, "Stale", 100L, owner.id());
        listRepository.save(listRepository.findById(listId).get().applyChange(older));

        var result = listRepository.findById(listId);
        assertThat(result.get().items().get(itemId).name().value()).isEqualTo("Correct");
    }

    @Test
    void updatesToEveryLwwFieldArePersisted() {
        UUID groupId = newGroup();
        User owner = userRepository.save(new User(UUID.randomUUID(), "fields@test.com", "Fields User", null, groupId));
        UUID listId = UUID.randomUUID();
        UUID itemId = UUID.randomUUID();

        ShoppingList list = listRepository.save(new ShoppingList(
            listId, "Fields Test", owner.id(),
            groupId, java.util.Map.of(), java.time.Instant.now()));

        listRepository.save(list.applyChange(
            new ItemChange(itemId, listId, ItemField.NAME, "Milk", 100L, owner.id())));

        long ts = 200L;
        for (var change : List.of(
                new ItemChange(itemId, listId, ItemField.QUANTITY, "3", ts, owner.id()),
                new ItemChange(itemId, listId, ItemField.CHECKED, "true", ts + 1, owner.id()),
                new ItemChange(itemId, listId, ItemField.DELETED, "true", ts + 2, owner.id()),
                new ItemChange(itemId, listId, ItemField.SORT_KEY, "m5", ts + 3, owner.id()),
                new ItemChange(itemId, listId, ItemField.SECTION, "GETRAENKE", ts + 4, owner.id()))) {
            listRepository.save(listRepository.findById(listId).get().applyChange(change));
        }

        var item = listRepository.findById(listId).get().items().get(itemId);
        assertThat(item.quantity().value()).isEqualTo("3");
        assertThat(item.checked().value()).isTrue();
        assertThat(item.deleted().value()).isTrue();
        assertThat(item.sortKey().value()).isEqualTo("m5");
        assertThat(item.section().value()).isEqualTo("GETRAENKE");
    }

    @Test
    void sectionDefaultsToSonstigesAndRoundTrips() {
        UUID groupId = newGroup();
        User owner = userRepository.save(new User(UUID.randomUUID(), "section@test.com", "Section User", null, groupId));
        UUID listId = UUID.randomUUID();
        UUID itemId = UUID.randomUUID();

        ShoppingList list = listRepository.save(new ShoppingList(
            listId, "Section Test", owner.id(),
            groupId, java.util.Map.of(), java.time.Instant.now()));

        listRepository.save(list.applyChange(
            new ItemChange(itemId, listId, ItemField.NAME, "Milch", 100L, owner.id())));

        var seeded = listRepository.findById(listId).get().items().get(itemId);
        assertThat(seeded.section().value()).isEqualTo("SONSTIGES");

        // A newer SECTION change wins the LWW merge-at-save
        listRepository.save(listRepository.findById(listId).get().applyChange(
            new ItemChange(itemId, listId, ItemField.SECTION, "MOLKEREI_EIER", 200L, owner.id())));

        var updated = listRepository.findById(listId).get().items().get(itemId);
        assertThat(updated.section().value()).isEqualTo("MOLKEREI_EIER");

        // A stale SECTION change must not overwrite the newer one (merge-at-save timestamp semantics)
        listRepository.save(listRepository.findById(listId).get().applyChange(
            new ItemChange(itemId, listId, ItemField.SECTION, "SONSTIGES", 150L, owner.id())));

        var afterStale = listRepository.findById(listId).get().items().get(itemId);
        assertThat(afterStale.section().value()).isEqualTo("MOLKEREI_EIER");
    }
}
