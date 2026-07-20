package com.shopmate.domain.model;

import java.time.Instant;
import java.util.UUID;

public record Group(
    UUID id,
    String name,
    Instant createdAt
) {}
