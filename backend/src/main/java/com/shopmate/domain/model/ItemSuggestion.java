package com.shopmate.domain.model;

import java.util.List;
import java.util.UUID;

/**
 * One shopping list item paired with its Picnic search candidates, and the term they were
 * found with — which is the item's own name unless the caller overrode it.
 */
public record ItemSuggestion(
    UUID itemId, String itemName, String searchTerm, List<ArticleSuggestion> suggestions) {}
