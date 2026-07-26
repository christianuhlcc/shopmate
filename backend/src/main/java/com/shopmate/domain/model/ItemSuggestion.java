package com.shopmate.domain.model;

import java.util.List;
import java.util.UUID;

/**
 * One shopping list item paired with its (up to 5) Picnic search candidates.
 */
public record ItemSuggestion(UUID itemId, String itemName, List<ArticleSuggestion> suggestions) {}
