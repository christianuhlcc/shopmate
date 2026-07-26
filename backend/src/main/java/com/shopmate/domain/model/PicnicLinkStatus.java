package com.shopmate.domain.model;

/**
 * Whether the requesting user has a Picnic account linked. {@code email} is null
 * when {@code linked} is false.
 */
public record PicnicLinkStatus(boolean linked, String email) {}
