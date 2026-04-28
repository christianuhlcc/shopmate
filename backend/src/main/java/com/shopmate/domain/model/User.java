package com.shopmate.domain.model;

import java.util.UUID;

public record User(
    UUID id,
    String email,
    String displayName,
    String avatarUrl
) {}
