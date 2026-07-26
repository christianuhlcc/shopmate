package com.shopmate.application.service;

import com.shopmate.domain.model.ArticleSuggestion;
import com.shopmate.domain.model.ExportFailure;
import com.shopmate.domain.model.ExportResult;
import com.shopmate.domain.model.ExportSelection;
import com.shopmate.domain.model.ItemSuggestion;
import com.shopmate.domain.model.PicnicCredentials;
import com.shopmate.domain.model.PicnicCredentialsMissingException;
import com.shopmate.domain.model.PicnicLinkStatus;
import com.shopmate.domain.model.PicnicUnavailableException;
import com.shopmate.domain.model.ShoppingItem;
import com.shopmate.domain.model.ShoppingList;
import com.shopmate.domain.port.in.PicnicExportUseCase;
import com.shopmate.domain.port.in.ShoppingListUseCase;
import com.shopmate.domain.port.out.PicnicClientPort;
import com.shopmate.domain.port.out.PicnicCredentialsRepository;
import org.springframework.stereotype.Service;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class PicnicExportService implements PicnicExportUseCase {

    private static final int MAX_SUGGESTIONS_PER_ITEM = 5;
    private static final Pattern LEADING_DIGITS = Pattern.compile("^(\\d+)");

    private final ShoppingListUseCase shoppingListUseCase;
    private final PicnicClientPort picnicClientPort;
    private final PicnicCredentialsRepository picnicCredentialsRepository;

    public PicnicExportService(ShoppingListUseCase shoppingListUseCase,
                                PicnicClientPort picnicClientPort,
                                PicnicCredentialsRepository picnicCredentialsRepository) {
        this.shoppingListUseCase = shoppingListUseCase;
        this.picnicClientPort = picnicClientPort;
        this.picnicCredentialsRepository = picnicCredentialsRepository;
    }

    @Override
    public void linkCredentials(UUID userId, String email, String rawPassword) {
        PicnicCredentials credentials = new PicnicCredentials(email, md5Hex(rawPassword));
        // verifyLogin throws (PicnicLoginFailedException/PicnicUnavailableException) before
        // anything is persisted — the raw password never survives past this point either way.
        picnicClientPort.verifyLogin(credentials);
        picnicCredentialsRepository.save(userId, credentials);
    }

    @Override
    public void unlinkCredentials(UUID userId) {
        picnicCredentialsRepository.delete(userId);
    }

    @Override
    public PicnicLinkStatus getCredentialsStatus(UUID userId) {
        return picnicCredentialsRepository.findByUserId(userId)
            .map(c -> new PicnicLinkStatus(true, c.email()))
            .orElse(new PicnicLinkStatus(false, null));
    }

    @Override
    public List<ItemSuggestion> getSuggestions(UUID listId, UUID requestingUserId) {
        PicnicCredentials credentials = requireCredentials(requestingUserId);
        ShoppingList list = shoppingListUseCase.getList(listId, requestingUserId);

        List<ItemSuggestion> result = new ArrayList<>();
        for (ShoppingItem item : list.activeItems()) {
            if (item.checked().value()) {
                continue;
            }
            // Let PicnicUnavailableException propagate uncaught: a search failure for any
            // item fails the whole call (all-or-nothing), unlike export's per-item handling.
            List<ArticleSuggestion> suggestions = picnicClientPort.searchArticles(credentials, item.name().value())
                .stream()
                .limit(MAX_SUGGESTIONS_PER_ITEM)
                .toList();
            result.add(new ItemSuggestion(item.id(), item.name().value(), suggestions));
        }
        return result;
    }

    @Override
    public ExportResult export(UUID listId, UUID requestingUserId, List<ExportSelection> selections) {
        PicnicCredentials credentials = requireCredentials(requestingUserId);
        ShoppingList list = shoppingListUseCase.getList(listId, requestingUserId);

        int added = 0;
        int skipped = 0;
        List<ExportFailure> failures = new ArrayList<>();

        for (ExportSelection selection : selections) {
            if (selection.articleId() == null) {
                skipped++;
                continue;
            }

            ShoppingItem item = list.items().get(selection.itemId());
            if (item == null) {
                failures.add(new ExportFailure(selection.itemId(), "Item not found on list"));
                continue;
            }

            int count = parseCartCount(item.quantity().value());
            try {
                picnicClientPort.addToCart(credentials, selection.articleId(), count);
                added++;
            } catch (PicnicUnavailableException e) {
                // One bad SKU must not abort the batch (ADR-0014) — record and keep going.
                failures.add(new ExportFailure(selection.itemId(), e.getMessage()));
            }
        }

        return new ExportResult(added, skipped, List.copyOf(failures));
    }

    private PicnicCredentials requireCredentials(UUID userId) {
        return picnicCredentialsRepository.findByUserId(userId)
            .orElseThrow(() -> new PicnicCredentialsMissingException(userId));
    }

    /**
     * Extracts a leading integer from a freitext quantity value as the Picnic cart count.
     * No unit conversion is attempted (ADR-0014): "500g" parses to 500, "2x" to 2, and
     * anything without a leading digit (null, blank, "Butter") defaults to 1.
     */
    static int parseCartCount(String quantity) {
        if (quantity == null || quantity.isBlank()) {
            return 1;
        }
        Matcher matcher = LEADING_DIGITS.matcher(quantity);
        if (!matcher.find()) {
            return 1;
        }
        return Integer.parseInt(matcher.group(1));
    }

    private static String md5Hex(String rawPassword) {
        try {
            MessageDigest digest = MessageDigest.getInstance("MD5");
            byte[] hash = digest.digest(rawPassword.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            // MD5 is guaranteed available on every standard JDK provider.
            throw new IllegalStateException("MD5 algorithm not available", e);
        }
    }
}
