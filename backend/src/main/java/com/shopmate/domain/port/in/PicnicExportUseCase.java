package com.shopmate.domain.port.in;

import com.shopmate.domain.model.ExportResult;
import com.shopmate.domain.model.ExportSelection;
import com.shopmate.domain.model.ItemSuggestion;
import com.shopmate.domain.model.PicnicLinkStatus;

import java.util.List;
import java.util.UUID;

public interface PicnicExportUseCase {

    /**
     * Hashes {@code rawPassword} to MD5, verifies it against Picnic, then persists the
     * digest — the raw password itself is never persisted or logged.
     */
    void linkCredentials(UUID userId, String email, String rawPassword);

    void unlinkCredentials(UUID userId);

    PicnicLinkStatus getCredentialsStatus(UUID userId);

    /**
     * Only considers active items ({@code checked=false}, {@code deleted=false}).
     * Throws {@link com.shopmate.domain.model.PicnicCredentialsMissingException} if the
     * requesting user has no Picnic account linked.
     */
    List<ItemSuggestion> getSuggestions(UUID listId, UUID requestingUserId);

    ExportResult export(UUID listId, UUID requestingUserId, List<ExportSelection> selections);
}
