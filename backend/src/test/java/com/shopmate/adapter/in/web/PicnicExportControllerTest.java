package com.shopmate.adapter.in.web;

import com.shopmate.domain.model.ArticleSuggestion;
import com.shopmate.domain.model.ExportFailure;
import com.shopmate.domain.model.ExportResult;
import com.shopmate.domain.model.ExportSelection;
import com.shopmate.domain.model.ItemSuggestion;
import com.shopmate.domain.model.PicnicLinkStatus;
import com.shopmate.domain.port.in.PicnicExportUseCase;
import com.shopmate.generated.model.PicnicCredentialsRequest;
import com.shopmate.infrastructure.security.SecurityContextHelper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PicnicExportControllerTest {

    @Mock PicnicExportUseCase picnicExportUseCase;
    @Mock SecurityContextHelper securityContextHelper;

    PicnicExportController controller;

    private static final UUID USER_ID = UUID.randomUUID();
    private static final UUID LIST_ID = UUID.randomUUID();
    private static final UUID ITEM_ID = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        controller = new PicnicExportController(picnicExportUseCase, securityContextHelper);
        when(securityContextHelper.getCurrentUserId()).thenReturn(USER_ID);
    }

    @Test
    void linkPicnicCredentialsReturnsTheResultingLinkState() {
        when(picnicExportUseCase.linkCredentials(USER_ID, "user@example.com", "s3cret"))
            .thenReturn(com.shopmate.domain.model.PicnicLinkState.LINKED);

        var response = controller.linkPicnicCredentials(
            new PicnicCredentialsRequest("user@example.com", "s3cret"));

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(response.getBody().getStatus())
            .isEqualTo(com.shopmate.generated.model.PicnicLinkState.LINKED);
        verify(picnicExportUseCase).linkCredentials(USER_ID, "user@example.com", "s3cret");
    }

    @Test
    void linkPicnicCredentialsSurfacesAPendingSecondFactor() {
        // The frontend switches to the code-entry step off this value alone, so collapsing it
        // to "linked" would strand the user on a form that cannot succeed.
        when(picnicExportUseCase.linkCredentials(USER_ID, "user@example.com", "s3cret"))
            .thenReturn(com.shopmate.domain.model.PicnicLinkState.PENDING_SECOND_FACTOR);

        var response = controller.linkPicnicCredentials(
            new PicnicCredentialsRequest("user@example.com", "s3cret"));

        assertThat(response.getBody().getStatus())
            .isEqualTo(com.shopmate.generated.model.PicnicLinkState.PENDING_SECOND_FACTOR);
    }

    @Test
    void secondFactorEndpointsDelegateAndReturn204() {
        var sent = controller.sendPicnicSecondFactor();
        var verified = controller.verifyPicnicSecondFactor(
            new com.shopmate.generated.model.PicnicSecondFactorRequest("252000"));

        assertThat(sent.getStatusCode().value()).isEqualTo(204);
        assertThat(verified.getStatusCode().value()).isEqualTo(204);
        verify(picnicExportUseCase).resendSecondFactor(USER_ID);
        verify(picnicExportUseCase).verifySecondFactor(USER_ID, "252000");
    }

    @Test
    void unlinkPicnicCredentialsDelegatesAndReturns204() {
        var response = controller.unlinkPicnicCredentials();

        assertThat(response.getStatusCode().value()).isEqualTo(204);
        verify(picnicExportUseCase).unlinkCredentials(USER_ID);
    }

    @Test
    void getPicnicCredentialsStatusMapsLinkedStatusWithEmail() {
        when(picnicExportUseCase.getCredentialsStatus(USER_ID))
            .thenReturn(new PicnicLinkStatus(true, "user@example.com", com.shopmate.domain.model.PicnicLinkState.LINKED));

        var response = controller.getPicnicCredentialsStatus();

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(response.getBody().getLinked()).isTrue();
        assertThat(response.getBody().getEmail()).isEqualTo("user@example.com");
    }

    @Test
    void getPicnicCredentialsStatusMapsUnlinkedStatusWithNullEmail() {
        when(picnicExportUseCase.getCredentialsStatus(USER_ID))
            .thenReturn(PicnicLinkStatus.notLinked());

        var response = controller.getPicnicCredentialsStatus();

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(response.getBody().getLinked()).isFalse();
        assertThat(response.getBody().getEmail()).isNull();
    }

    @Test
    void getPicnicItemSuggestionsDelegatesAndMapsFieldForField() {
        ArticleSuggestion withImage = new ArticleSuggestion("art-1", "Vollmilch 1L", "https://img/1.png", 129, "1L");
        ArticleSuggestion withoutImage = new ArticleSuggestion("art-2", "Vollmilch 1.5L", null, null, null);
        ItemSuggestion suggestion = new ItemSuggestion(ITEM_ID, "Milch", List.of(withImage, withoutImage));
        when(picnicExportUseCase.getItemSuggestions(LIST_ID, ITEM_ID, USER_ID)).thenReturn(suggestion);

        var response = controller.getPicnicItemSuggestions(LIST_ID, ITEM_ID);

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        verify(picnicExportUseCase).getItemSuggestions(LIST_ID, ITEM_ID, USER_ID);
        var itemDto = response.getBody();
        assertThat(itemDto.getItemId()).isEqualTo(ITEM_ID);
        assertThat(itemDto.getItemName()).isEqualTo("Milch");
        assertThat(itemDto.getSuggestions()).hasSize(2);

        var first = itemDto.getSuggestions().get(0);
        assertThat(first.getId()).isEqualTo("art-1");
        assertThat(first.getName()).isEqualTo("Vollmilch 1L");
        assertThat(first.getImageUrl()).isEqualTo("https://img/1.png");
        assertThat(first.getPriceCents()).isEqualTo(129);
        assertThat(first.getUnit()).isEqualTo("1L");

        var second = itemDto.getSuggestions().get(1);
        assertThat(second.getId()).isEqualTo("art-2");
        assertThat(second.getName()).isEqualTo("Vollmilch 1.5L");
        assertThat(second.getImageUrl()).isNull();
        assertThat(second.getPriceCents()).isNull();
        assertThat(second.getUnit()).isNull();
    }

    @Test
    void exportToPicnicMapsSelectionsToDomainAndDelegatesToUseCase() {
        UUID skippedItemId = UUID.randomUUID();
        var request = new com.shopmate.generated.model.ExportRequest(List.of(
            new com.shopmate.generated.model.ExportSelection(ITEM_ID).articleId("art-1"),
            new com.shopmate.generated.model.ExportSelection(skippedItemId).articleId(null)));
        when(picnicExportUseCase.export(eq(LIST_ID), eq(USER_ID), org.mockito.ArgumentMatchers.anyList()))
            .thenReturn(new ExportResult(1, 1, List.of()));

        var response = controller.exportToPicnic(LIST_ID, request);

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<ExportSelection>> captor = ArgumentCaptor.forClass(List.class);
        verify(picnicExportUseCase).export(eq(LIST_ID), eq(USER_ID), captor.capture());
        List<ExportSelection> domainSelections = captor.getValue();
        assertThat(domainSelections).hasSize(2);
        assertThat(domainSelections.get(0)).isEqualTo(new ExportSelection(ITEM_ID, "art-1"));
        assertThat(domainSelections.get(1)).isEqualTo(new ExportSelection(skippedItemId, null));
    }

    @Test
    void exportToPicnicMapsResultAndFailuresFieldForField() {
        var request = new com.shopmate.generated.model.ExportRequest(List.of(
            new com.shopmate.generated.model.ExportSelection(ITEM_ID).articleId("art-1")));
        UUID failedItemId = UUID.randomUUID();
        ExportResult domainResult = new ExportResult(2, 1, List.of(
            new ExportFailure(failedItemId, "Picnic rejected the SKU")));
        when(picnicExportUseCase.export(eq(LIST_ID), eq(USER_ID), org.mockito.ArgumentMatchers.anyList()))
            .thenReturn(domainResult);

        var response = controller.exportToPicnic(LIST_ID, request);

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        var body = response.getBody();
        assertThat(body.getAdded()).isEqualTo(2);
        assertThat(body.getSkipped()).isEqualTo(1);
        assertThat(body.getFailures()).hasSize(1);
        assertThat(body.getFailures().get(0).getItemId()).isEqualTo(failedItemId);
        assertThat(body.getFailures().get(0).getReason()).isEqualTo("Picnic rejected the SKU");
    }
}
