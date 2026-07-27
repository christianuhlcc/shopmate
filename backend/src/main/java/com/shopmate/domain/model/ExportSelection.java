package com.shopmate.domain.model;

import java.util.UUID;

/**
 * The user's per-item choice for a Picnic export. {@code articleId} is nullable —
 * null means the user skipped this item, so no cart line is added for it.
 */
public record ExportSelection(UUID itemId, String articleId) {}
