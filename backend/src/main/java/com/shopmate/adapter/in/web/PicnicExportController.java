package com.shopmate.adapter.in.web;

import com.shopmate.domain.model.ArticleSuggestion;
import com.shopmate.domain.model.ExportFailure;
import com.shopmate.domain.model.ExportResult;
import com.shopmate.domain.model.ItemSuggestion;
import com.shopmate.domain.model.PicnicLinkState;
import com.shopmate.domain.model.PicnicLinkStatus;
import com.shopmate.domain.port.in.PicnicExportUseCase;
import com.shopmate.generated.api.PicnicExportApi;
import com.shopmate.generated.model.ExportRequest;
import com.shopmate.generated.model.ItemSuggestions;
import com.shopmate.generated.model.PicnicCredentialsRequest;
import com.shopmate.generated.model.PicnicCredentialsStatus;
import com.shopmate.generated.model.PicnicLinkResult;
import com.shopmate.generated.model.PicnicSecondFactorRequest;
import com.shopmate.infrastructure.security.SecurityContextHelper;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

// The OpenAPI spec declares `servers: /api`, but the generator does not include
// that base path in the interface mappings — it must be added at class level.
@RestController
@RequestMapping("/api")
public class PicnicExportController implements PicnicExportApi {

    private final PicnicExportUseCase picnicExportUseCase;
    private final SecurityContextHelper securityContextHelper;

    public PicnicExportController(PicnicExportUseCase picnicExportUseCase,
                                   SecurityContextHelper securityContextHelper) {
        this.picnicExportUseCase = picnicExportUseCase;
        this.securityContextHelper = securityContextHelper;
    }

    @Override
    public ResponseEntity<PicnicLinkResult> linkPicnicCredentials(
            @Valid @RequestBody PicnicCredentialsRequest picnicCredentialsRequest) {
        UUID currentUserId = securityContextHelper.getCurrentUserId();
        PicnicLinkState state = picnicExportUseCase.linkCredentials(
                currentUserId,
                picnicCredentialsRequest.getEmail(),
                picnicCredentialsRequest.getPassword());
        return ResponseEntity.ok(new PicnicLinkResult(toDto(state)));
    }

    @Override
    public ResponseEntity<Void> sendPicnicSecondFactor() {
        picnicExportUseCase.resendSecondFactor(securityContextHelper.getCurrentUserId());
        return ResponseEntity.noContent().build();
    }

    @Override
    public ResponseEntity<Void> verifyPicnicSecondFactor(
            @Valid @RequestBody PicnicSecondFactorRequest picnicSecondFactorRequest) {
        picnicExportUseCase.verifySecondFactor(
                securityContextHelper.getCurrentUserId(),
                picnicSecondFactorRequest.getCode());
        return ResponseEntity.noContent().build();
    }

    @Override
    public ResponseEntity<Void> unlinkPicnicCredentials() {
        UUID currentUserId = securityContextHelper.getCurrentUserId();
        picnicExportUseCase.unlinkCredentials(currentUserId);
        return ResponseEntity.noContent().build();
    }

    @Override
    public ResponseEntity<PicnicCredentialsStatus> getPicnicCredentialsStatus() {
        UUID currentUserId = securityContextHelper.getCurrentUserId();
        PicnicLinkStatus status = picnicExportUseCase.getCredentialsStatus(currentUserId);
        return ResponseEntity.ok(new PicnicCredentialsStatus(status.linked())
                .email(status.email())
                .status(status.state() == null ? null : toDto(status.state())));
    }

    @Override
    public ResponseEntity<ItemSuggestions> getPicnicItemSuggestions(
            @PathVariable UUID listId, @PathVariable UUID itemId) {
        UUID currentUserId = securityContextHelper.getCurrentUserId();
        return ResponseEntity.ok(toDto(picnicExportUseCase.getItemSuggestions(listId, itemId, currentUserId)));
    }

    @Override
    public ResponseEntity<com.shopmate.generated.model.ExportResult> exportToPicnic(
            @PathVariable UUID listId,
            @Valid @RequestBody ExportRequest exportRequest) {
        UUID currentUserId = securityContextHelper.getCurrentUserId();
        List<com.shopmate.domain.model.ExportSelection> selections = exportRequest.getSelections().stream()
                .map(sel -> new com.shopmate.domain.model.ExportSelection(sel.getItemId(), sel.getArticleId()))
                .toList();
        ExportResult result = picnicExportUseCase.export(listId, currentUserId, selections);
        return ResponseEntity.ok(toDto(result));
    }

    // --- Mapping helpers ---

    private static com.shopmate.generated.model.PicnicLinkState toDto(PicnicLinkState state) {
        return com.shopmate.generated.model.PicnicLinkState.valueOf(state.name());
    }

    private ItemSuggestions toDto(ItemSuggestion suggestion) {
        List<com.shopmate.generated.model.ArticleSuggestion> articleDtos = suggestion.suggestions().stream()
                .map(this::toDto)
                .toList();
        return new ItemSuggestions(suggestion.itemId(), suggestion.itemName(), articleDtos);
    }

    private com.shopmate.generated.model.ArticleSuggestion toDto(ArticleSuggestion suggestion) {
        return new com.shopmate.generated.model.ArticleSuggestion(suggestion.id(), suggestion.name())
                .imageUrl(suggestion.imageUrl())
                .priceCents(suggestion.priceCents())
                .unit(suggestion.unit());
    }

    private com.shopmate.generated.model.ExportResult toDto(ExportResult result) {
        List<com.shopmate.generated.model.ExportFailure> failureDtos = result.failures().stream()
                .map(this::toDto)
                .toList();
        return new com.shopmate.generated.model.ExportResult(result.added(), result.skipped(), failureDtos);
    }

    private com.shopmate.generated.model.ExportFailure toDto(ExportFailure failure) {
        return new com.shopmate.generated.model.ExportFailure(failure.itemId(), failure.reason());
    }
}
