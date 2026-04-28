package com.shopmate.adapter.out.persistence;

import com.shopmate.domain.model.ItemChange;
import com.shopmate.domain.model.ItemField;
import com.shopmate.domain.model.ShoppingList;
import com.shopmate.domain.model.User;
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

    @Test
    void saveAndFindListWithMember() {
        User owner = userRepository.save(new User(UUID.randomUUID(), "owner@test.com", "Owner", null));
        ShoppingList list = listRepository.save(new ShoppingList(
            UUID.randomUUID(), "Groceries", owner.id(),
            java.util.Set.of(owner.id()), java.util.Map.of(), java.time.Instant.now()));

        var found = listRepository.findById(list.id());
        assertThat(found).isPresent();
        assertThat(found.get().name()).isEqualTo("Groceries");
        assertThat(found.get().memberIds()).contains(owner.id());
    }

    @Test
    void findAllByMemberId() {
        User user = userRepository.save(new User(UUID.randomUUID(), "member@test.com", "Member", null));
        ShoppingList list = listRepository.save(new ShoppingList(
            UUID.randomUUID(), "My List", user.id(),
            java.util.Set.of(user.id()), java.util.Map.of(), java.time.Instant.now()));

        List<ShoppingList> lists = listRepository.findAllByMemberId(user.id());
        assertThat(lists).extracting(ShoppingList::id).contains(list.id());
    }

    @Test
    void lwwMergePersistedCorrectly() {
        User owner = userRepository.save(new User(UUID.randomUUID(), "lww@test.com", "LWW User", null));
        UUID listId = UUID.randomUUID();
        UUID itemId = UUID.randomUUID();

        ShoppingList list = listRepository.save(new ShoppingList(
            listId, "LWW List", owner.id(),
            java.util.Set.of(owner.id()), java.util.Map.of(), java.time.Instant.now()));

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
        User owner = userRepository.save(new User(UUID.randomUUID(), "stale@test.com", "Stale User", null));
        UUID listId = UUID.randomUUID();
        UUID itemId = UUID.randomUUID();

        ShoppingList list = listRepository.save(new ShoppingList(
            listId, "Stale Test", owner.id(),
            java.util.Set.of(owner.id()), java.util.Map.of(), java.time.Instant.now()));

        // Apply newer change first
        ItemChange newer = new ItemChange(itemId, listId, ItemField.NAME, "Correct", 200L, owner.id());
        listRepository.save(list.applyChange(newer));

        // Apply older change — must NOT overwrite
        ItemChange older = new ItemChange(itemId, listId, ItemField.NAME, "Stale", 100L, owner.id());
        listRepository.save(listRepository.findById(listId).get().applyChange(older));

        var result = listRepository.findById(listId);
        assertThat(result.get().items().get(itemId).name().value()).isEqualTo("Correct");
    }
}
