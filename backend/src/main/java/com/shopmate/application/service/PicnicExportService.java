package com.shopmate.application.service;

import com.shopmate.domain.model.ArticleSuggestion;
import com.shopmate.domain.model.ExportFailure;
import com.shopmate.domain.model.ExportResult;
import com.shopmate.domain.model.ExportSelection;
import com.shopmate.domain.model.ItemNotFoundException;
import com.shopmate.domain.model.ItemSuggestion;
import com.shopmate.domain.model.PicnicAccountLink;
import com.shopmate.domain.model.PicnicCredentials;
import com.shopmate.domain.model.PicnicCredentialsMissingException;
import com.shopmate.domain.model.PicnicLinkState;
import com.shopmate.domain.model.PicnicLinkStatus;
import com.shopmate.domain.model.PicnicLoginResult;
import com.shopmate.domain.model.PicnicSecondFactorRequiredException;
import com.shopmate.domain.model.PicnicSession;
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
import java.security.SecureRandom;
import java.util.List;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class PicnicExportService implements PicnicExportUseCase {

    // Picnic returns ~120 products per search and we parse the whole response anyway, so a
    // deeper list is free; five routinely failed to contain the right product.
    private static final int MAX_SUGGESTIONS_PER_ITEM = 20;
    private static final Pattern LEADING_DIGITS = Pattern.compile("^(\\d+)");
    private static final SecureRandom DEVICE_ID_RANDOM = new SecureRandom();

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
    public PicnicLinkState linkCredentials(UUID userId, String email, String rawPassword) {
        // A fresh device id per link: Picnic ties the session to it, and reusing one identifier
        // across all our users would make the whole install correlatable to Picnic and let one
        // bad actor get it blocked for everybody (ADR-0014 amendment).
        String deviceId = newDeviceId();
        PicnicCredentials credentials = new PicnicCredentials(email, md5Hex(rawPassword));

        // Throws before anything is persisted if Picnic rejects the password; the raw password
        // never survives past this method either way.
        PicnicLoginResult result = picnicClientPort.login(credentials, deviceId);

        if (!result.secondFactorRequired()) {
            picnicCredentialsRepository.save(userId,
                new PicnicAccountLink(email, result.session(), PicnicLinkState.LINKED));
            return PicnicLinkState.LINKED;
        }

        // Persist the provisional session *before* asking for the code: /user/2fa/verify needs
        // exactly this key, so losing it to a restart would strand the user mid-link with no
        // way forward but retyping their password.
        picnicCredentialsRepository.save(userId,
            new PicnicAccountLink(email, result.session(), PicnicLinkState.PENDING_SECOND_FACTOR));
        picnicClientPort.sendSecondFactor(result.session());
        return PicnicLinkState.PENDING_SECOND_FACTOR;
    }

    @Override
    public void resendSecondFactor(UUID userId) {
        picnicClientPort.sendSecondFactor(requirePendingLink(userId).session());
    }

    @Override
    public void verifySecondFactor(UUID userId, String code) {
        PicnicAccountLink pending = requirePendingLink(userId);
        // Throws PicnicLoginFailedException on a wrong code, leaving the pending link intact so
        // the user can just try again.
        PicnicSession upgraded = picnicClientPort.verifySecondFactor(pending.session(), code);
        picnicCredentialsRepository.save(userId,
            new PicnicAccountLink(pending.email(), upgraded, PicnicLinkState.LINKED));
    }

    @Override
    public void unlinkCredentials(UUID userId) {
        picnicCredentialsRepository.delete(userId);
    }

    @Override
    public PicnicLinkStatus getCredentialsStatus(UUID userId) {
        return picnicCredentialsRepository.findByUserId(userId)
            .map(link -> new PicnicLinkStatus(link.isLinked(), link.email(), link.state()))
            .orElseGet(PicnicLinkStatus::notLinked);
    }

    @Override
    public ItemSuggestion getItemSuggestions(UUID listId, UUID itemId, UUID requestingUserId) {
        PicnicSession session = requireLinkedSession(requestingUserId);
        ShoppingList list = shoppingListUseCase.getList(listId, requestingUserId);

        ShoppingItem item = list.items().get(itemId);
        if (item == null || item.deleted().value() || item.checked().value()) {
            throw new ItemNotFoundException(itemId);
        }

        List<ArticleSuggestion> suggestions = picnicClientPort.searchArticles(session, item.name().value())
            .stream()
            .limit(MAX_SUGGESTIONS_PER_ITEM)
            .toList();
        return new ItemSuggestion(item.id(), item.name().value(), suggestions);
    }

    @Override
    public ExportResult export(UUID listId, UUID requestingUserId, List<ExportSelection> selections) {
        PicnicSession session = requireLinkedSession(requestingUserId);
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
                picnicClientPort.addToCart(session, selection.articleId(), count);
                added++;
            } catch (PicnicUnavailableException e) {
                // One bad SKU must not abort the batch (ADR-0014) — record and keep going.
                failures.add(new ExportFailure(selection.itemId(), e.getMessage()));
            }
        }

        return new ExportResult(added, skipped, List.copyOf(failures));
    }

    /**
     * A half-finished link is deliberately not treated as "missing": the frontend resumes at
     * the code-entry step instead of making the user retype their password.
     */
    private PicnicSession requireLinkedSession(UUID userId) {
        PicnicAccountLink link = picnicCredentialsRepository.findByUserId(userId)
            .orElseThrow(() -> new PicnicCredentialsMissingException(userId));
        if (!link.isLinked()) {
            throw new PicnicSecondFactorRequiredException(userId);
        }
        return link.session();
    }

    private PicnicAccountLink requirePendingLink(UUID userId) {
        PicnicAccountLink link = picnicCredentialsRepository.findByUserId(userId)
            .orElseThrow(() -> new PicnicCredentialsMissingException(userId));
        if (link.state() != PicnicLinkState.PENDING_SECOND_FACTOR) {
            throw new PicnicSecondFactorRequiredException(userId);
        }
        return link;
    }

    /** 16 hex chars, matching the shape of the device ids Picnic's own clients send. */
    private static String newDeviceId() {
        byte[] raw = new byte[8];
        DEVICE_ID_RANDOM.nextBytes(raw);
        return HexFormat.of().withUpperCase().formatHex(raw);
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
