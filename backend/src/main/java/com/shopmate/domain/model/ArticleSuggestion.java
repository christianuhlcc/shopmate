package com.shopmate.domain.model;

/**
 * One Picnic catalog candidate for a shopping list item. {@code id} is Picnic's own
 * product id (opaque string). {@code imageUrl}, {@code priceCents}, and {@code unit}
 * may be null when Picnic's search response omits them.
 */
public record ArticleSuggestion(String id, String name, String imageUrl, Integer priceCents, String unit) {}
