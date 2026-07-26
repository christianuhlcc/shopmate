package com.shopmate.application.service;

import com.shopmate.domain.model.ArticleSuggestion;
import com.shopmate.domain.model.ExportResult;
import com.shopmate.domain.model.ExportSelection;
import com.shopmate.domain.model.ItemSuggestion;
import com.shopmate.domain.model.LwwField;
import com.shopmate.domain.model.PicnicCredentials;
import com.shopmate.domain.model.PicnicCredentialsMissingException;
import com.shopmate.domain.model.PicnicLinkStatus;
import com.shopmate.domain.model.PicnicLoginFailedException;
import com.shopmate.domain.model.PicnicUnavailableException;
import com.shopmate.domain.model.ShoppingItem;
import com.shopmate.domain.model.ShoppingList;
import com.shopmate.domain.port.in.ShoppingListUseCase;
import com.shopmate.domain.port.out.PicnicClientPort;
import com.shopmate.domain.port.out.PicnicCredentialsRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PicnicExportServiceTest {

    @Mock ShoppingListUseCase shoppingListUseCase;
    @Mock PicnicClientPort picnicClientPort;
    @Mock PicnicCredentialsRepository picnicCredentialsRepository;

    PicnicExportService service;

    private static final UUID USER_ID = UUID.randomUUID();
    private static final UUID LIST_ID = UUID.randomUUID();
    private static final String KNOWN_PASSWORD_MD5 = "5f4dcc3b5aa765d61d8327deb882cf99"; // md5("password")

    @org.junit.jupiter.api.BeforeEach
    void setUp() {
        service = new PicnicExportService(shoppingListUseCase, picnicClientPort, picnicCredentialsRepository);
    }

    private ShoppingItem item(UUID id, String name, String quantity, boolean checked, boolean deleted, String sortKey) {
        return new ShoppingItem(id, LIST_ID,
            new LwwField<>(name, 100L, USER_ID),
            new LwwField<>(quantity, 100L, USER_ID),
            new LwwField<>(checked, 100L, USER_ID),
            new LwwField<>(deleted, 100L, USER_ID),
            new LwwField<>(sortKey, 100L, USER_ID),
            new LwwField<>("SONSTIGES", 100L, USER_ID),
            Map.of());
    }

    private ShoppingList listOf(ShoppingItem... items) {
        Map<UUID, ShoppingItem> map = new HashMap<>();
        for (ShoppingItem i : items) {
            map.put(i.id(), i);
        }
        return new ShoppingList(LIST_ID, "Test List", USER_ID, UUID.randomUUID(), Map.copyOf(map), Instant.now());
    }

    private ArticleSuggestion article(String id) {
        return new ArticleSuggestion(id, "Article " + id, null, 199, "500g");
    }

    // --- linkCredentials -----------------------------------------------------------

    @Test
    void linkCredentialsHashesPasswordAndVerifiesBeforeSaving() {
        service.linkCredentials(USER_ID, "user@example.com", "password");

        ArgumentCaptor<PicnicCredentials> captor = ArgumentCaptor.forClass(PicnicCredentials.class);
        verify(picnicClientPort).verifyLogin(captor.capture());
        assertThat(captor.getValue().email()).isEqualTo("user@example.com");
        assertThat(captor.getValue().passwordMd5Hex()).isEqualTo(KNOWN_PASSWORD_MD5);

        verify(picnicCredentialsRepository).save(eq(USER_ID), eq(captor.getValue()));
    }

    @Test
    void linkCredentialsPropagatesLoginFailureAndNeverSaves() {
        org.mockito.Mockito.doThrow(new PicnicLoginFailedException("bad password"))
            .when(picnicClientPort).verifyLogin(any());

        assertThatThrownBy(() -> service.linkCredentials(USER_ID, "user@example.com", "wrong"))
            .isInstanceOf(PicnicLoginFailedException.class);

        verify(picnicCredentialsRepository, never()).save(any(), any());
    }

    @Test
    void linkCredentialsPropagatesUnavailableAndNeverSaves() {
        org.mockito.Mockito.doThrow(new PicnicUnavailableException("picnic down"))
            .when(picnicClientPort).verifyLogin(any());

        assertThatThrownBy(() -> service.linkCredentials(USER_ID, "user@example.com", "password"))
            .isInstanceOf(PicnicUnavailableException.class);

        verify(picnicCredentialsRepository, never()).save(any(), any());
    }

    // --- unlinkCredentials -----------------------------------------------------------

    @Test
    void unlinkCredentialsDelegatesToRepository() {
        service.unlinkCredentials(USER_ID);
        verify(picnicCredentialsRepository).delete(USER_ID);
    }

    // --- getCredentialsStatus -----------------------------------------------------------

    @Test
    void getCredentialsStatusReturnsLinkedWhenPresent() {
        when(picnicCredentialsRepository.findByUserId(USER_ID))
            .thenReturn(Optional.of(new PicnicCredentials("user@example.com", KNOWN_PASSWORD_MD5)));

        PicnicLinkStatus status = service.getCredentialsStatus(USER_ID);

        assertThat(status.linked()).isTrue();
        assertThat(status.email()).isEqualTo("user@example.com");
    }

    @Test
    void getCredentialsStatusReturnsNotLinkedWhenAbsent() {
        when(picnicCredentialsRepository.findByUserId(USER_ID)).thenReturn(Optional.empty());

        PicnicLinkStatus status = service.getCredentialsStatus(USER_ID);

        assertThat(status.linked()).isFalse();
        assertThat(status.email()).isNull();
    }

    // --- getSuggestions -----------------------------------------------------------

    @Test
    void getSuggestionsThrowsWhenCredentialsMissing() {
        when(picnicCredentialsRepository.findByUserId(USER_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getSuggestions(LIST_ID, USER_ID))
            .isInstanceOf(PicnicCredentialsMissingException.class);

        verify(shoppingListUseCase, never()).getList(any(), any());
    }

    @Test
    void getSuggestionsExcludesCheckedItemsAndCapsAtFive() {
        PicnicCredentials credentials = new PicnicCredentials("user@example.com", KNOWN_PASSWORD_MD5);
        when(picnicCredentialsRepository.findByUserId(USER_ID)).thenReturn(Optional.of(credentials));

        UUID activeItemId = UUID.randomUUID();
        UUID checkedItemId = UUID.randomUUID();
        ShoppingItem active = item(activeItemId, "Milch", "1", false, false, "a0");
        ShoppingItem checked = item(checkedItemId, "Butter", "1", true, false, "b0");
        ShoppingList list = listOf(active, checked);
        when(shoppingListUseCase.getList(LIST_ID, USER_ID)).thenReturn(list);

        List<ArticleSuggestion> sixResults = List.of(
            article("1"), article("2"), article("3"), article("4"), article("5"), article("6"));
        when(picnicClientPort.searchArticles(credentials, "Milch")).thenReturn(sixResults);

        List<ItemSuggestion> suggestions = service.getSuggestions(LIST_ID, USER_ID);

        assertThat(suggestions).hasSize(1);
        ItemSuggestion only = suggestions.get(0);
        assertThat(only.itemId()).isEqualTo(activeItemId);
        assertThat(only.itemName()).isEqualTo("Milch");
        assertThat(only.suggestions()).hasSize(5);
        assertThat(only.suggestions()).containsExactly(
            article("1"), article("2"), article("3"), article("4"), article("5"));

        verify(picnicClientPort, never()).searchArticles(eq(credentials), eq("Butter"));
        verify(shoppingListUseCase).getList(LIST_ID, USER_ID);
    }

    @Test
    void getSuggestionsPropagatesUnavailableFromSearch() {
        PicnicCredentials credentials = new PicnicCredentials("user@example.com", KNOWN_PASSWORD_MD5);
        when(picnicCredentialsRepository.findByUserId(USER_ID)).thenReturn(Optional.of(credentials));

        ShoppingItem active = item(UUID.randomUUID(), "Milch", "1", false, false, "a0");
        ShoppingList list = listOf(active);
        when(shoppingListUseCase.getList(LIST_ID, USER_ID)).thenReturn(list);
        when(picnicClientPort.searchArticles(eq(credentials), any()))
            .thenThrow(new PicnicUnavailableException("picnic down"));

        assertThatThrownBy(() -> service.getSuggestions(LIST_ID, USER_ID))
            .isInstanceOf(PicnicUnavailableException.class);
    }

    // --- export -----------------------------------------------------------

    @Test
    void exportThrowsWhenCredentialsMissing() {
        when(picnicCredentialsRepository.findByUserId(USER_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.export(LIST_ID, USER_ID, List.of()))
            .isInstanceOf(PicnicCredentialsMissingException.class);

        verify(shoppingListUseCase, never()).getList(any(), any());
    }

    @Test
    void exportSkipsSelectionsWithNullArticleId() {
        PicnicCredentials credentials = new PicnicCredentials("user@example.com", KNOWN_PASSWORD_MD5);
        when(picnicCredentialsRepository.findByUserId(USER_ID)).thenReturn(Optional.of(credentials));
        UUID itemId = UUID.randomUUID();
        ShoppingItem it = item(itemId, "Milch", "1", false, false, "a0");
        when(shoppingListUseCase.getList(LIST_ID, USER_ID)).thenReturn(listOf(it));

        ExportResult result = service.export(LIST_ID, USER_ID, List.of(new ExportSelection(itemId, null)));

        assertThat(result.added()).isEqualTo(0);
        assertThat(result.skipped()).isEqualTo(1);
        assertThat(result.failures()).isEmpty();
        verify(picnicClientPort, never()).addToCart(any(), any(), anyInt());
    }

    @Test
    void exportRecordsFailureWhenItemNotOnList() {
        PicnicCredentials credentials = new PicnicCredentials("user@example.com", KNOWN_PASSWORD_MD5);
        when(picnicCredentialsRepository.findByUserId(USER_ID)).thenReturn(Optional.of(credentials));
        when(shoppingListUseCase.getList(LIST_ID, USER_ID)).thenReturn(listOf());

        UUID staleItemId = UUID.randomUUID();
        ExportResult result = service.export(LIST_ID, USER_ID,
            List.of(new ExportSelection(staleItemId, "article-1")));

        assertThat(result.added()).isEqualTo(0);
        assertThat(result.skipped()).isEqualTo(0);
        assertThat(result.failures()).hasSize(1);
        assertThat(result.failures().get(0).itemId()).isEqualTo(staleItemId);
        assertThat(result.failures().get(0).reason()).isEqualTo("Item not found on list");
        verify(picnicClientPort, never()).addToCart(any(), any(), anyInt());
    }

    @Test
    void exportIncrementsAddedOnSuccessfulAddToCart() {
        PicnicCredentials credentials = new PicnicCredentials("user@example.com", KNOWN_PASSWORD_MD5);
        when(picnicCredentialsRepository.findByUserId(USER_ID)).thenReturn(Optional.of(credentials));
        UUID itemId = UUID.randomUUID();
        ShoppingItem it = item(itemId, "Milch", "2", false, false, "a0");
        when(shoppingListUseCase.getList(LIST_ID, USER_ID)).thenReturn(listOf(it));

        ExportResult result = service.export(LIST_ID, USER_ID,
            List.of(new ExportSelection(itemId, "article-1")));

        assertThat(result.added()).isEqualTo(1);
        assertThat(result.skipped()).isEqualTo(0);
        assertThat(result.failures()).isEmpty();
        verify(picnicClientPort).addToCart(credentials, "article-1", 2);
    }

    @Test
    void exportCatchesUnavailableAndContinuesToNextSelection() {
        PicnicCredentials credentials = new PicnicCredentials("user@example.com", KNOWN_PASSWORD_MD5);
        when(picnicCredentialsRepository.findByUserId(USER_ID)).thenReturn(Optional.of(credentials));
        UUID failingItemId = UUID.randomUUID();
        UUID succeedingItemId = UUID.randomUUID();
        ShoppingItem failing = item(failingItemId, "Milch", "1", false, false, "a0");
        ShoppingItem succeeding = item(succeedingItemId, "Butter", "1", false, false, "b0");
        when(shoppingListUseCase.getList(LIST_ID, USER_ID)).thenReturn(listOf(failing, succeeding));

        org.mockito.Mockito.doThrow(new PicnicUnavailableException("bad sku"))
            .when(picnicClientPort).addToCart(credentials, "bad-article", 1);

        ExportResult result = service.export(LIST_ID, USER_ID, List.of(
            new ExportSelection(failingItemId, "bad-article"),
            new ExportSelection(succeedingItemId, "good-article")));

        assertThat(result.added()).isEqualTo(1);
        assertThat(result.skipped()).isEqualTo(0);
        assertThat(result.failures()).hasSize(1);
        assertThat(result.failures().get(0).itemId()).isEqualTo(failingItemId);
        assertThat(result.failures().get(0).reason()).isEqualTo("bad sku");
        verify(picnicClientPort).addToCart(credentials, "good-article", 1);
    }

    // --- parseCartCount -----------------------------------------------------------

    @Test
    void parseCartCountExtractsLeadingInteger() {
        assertThat(PicnicExportService.parseCartCount("2")).isEqualTo(2);
        assertThat(PicnicExportService.parseCartCount("2x")).isEqualTo(2);
        assertThat(PicnicExportService.parseCartCount("500g")).isEqualTo(500);
    }

    @Test
    void parseCartCountDefaultsToOneWhenNoLeadingDigit() {
        assertThat(PicnicExportService.parseCartCount("Butter")).isEqualTo(1);
        assertThat(PicnicExportService.parseCartCount(null)).isEqualTo(1);
        assertThat(PicnicExportService.parseCartCount("")).isEqualTo(1);
    }
}
