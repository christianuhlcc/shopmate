package com.shopmate.domain.port.in;

import com.shopmate.domain.model.ExportResult;
import com.shopmate.domain.model.ExportSelection;
import com.shopmate.domain.model.ItemSuggestion;
import com.shopmate.domain.model.PicnicLinkState;
import com.shopmate.domain.model.PicnicLinkStatus;

import java.util.List;
import java.util.UUID;

public interface PicnicExportUseCase {

    /**
     * Exchanges the password for a Picnic session and stores that session — the password is
     * MD5-hashed for the login wire format and then discarded, never persisted (ADR-0014
     * amendment).
     *
     * <p>Returns {@link PicnicLinkState#PENDING_SECOND_FACTOR} when Picnic wants an SMS code,
     * in which case one has already been sent and
     * {@link #verifySecondFactor(UUID, String)} must follow.
     */
    PicnicLinkState linkCredentials(UUID userId, String email, String rawPassword);

    /**
     * Re-sends the SMS code for a link still awaiting its second factor.
     */
    void resendSecondFactor(UUID userId);

    /**
     * Completes linking. Throws PicnicLoginFailedException if Picnic rejects the code.
     */
    void verifySecondFactor(UUID userId, String code);

    void unlinkCredentials(UUID userId);

    PicnicLinkStatus getCredentialsStatus(UUID userId);

    /**
     * Suggestions for a single item — one Picnic search per call. Searching a whole list up
     * front made the wait scale with list size and spent searches on items the user then
     * skipped; the client walks items instead.
     *
     * <p>The item must be active ({@code checked=false}, {@code deleted=false}) and on the
     * list. Throws {@link com.shopmate.domain.model.PicnicCredentialsMissingException} if
     * nothing is linked, {@link com.shopmate.domain.model.PicnicSecondFactorRequiredException}
     * if linking was never completed, and
     * {@link com.shopmate.domain.model.PicnicSessionExpiredException} if the stored session is
     * no longer accepted.
     *
     * @param searchTermOverride what to search for instead of the item's name; null or blank
     *                           falls back to the name. Item names are freitext and often make
     *                           poor queries, so the user can replace one without renaming the
     *                           item on a list other people share.
     */
    ItemSuggestion getItemSuggestions(
        UUID listId, UUID itemId, UUID requestingUserId, String searchTermOverride);

    /**
     * Autocompletes a partial search term against Picnic. Needs a linked session and throws the
     * same credential exceptions as {@link #getItemSuggestions}, but touches no list — it is
     * about the query, not about anything the caller owns.
     */
    List<String> suggestSearchTerms(UUID requestingUserId, String partialTerm);

    ExportResult export(UUID listId, UUID requestingUserId, List<ExportSelection> selections);
}
