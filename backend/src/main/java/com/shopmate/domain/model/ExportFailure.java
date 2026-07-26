package com.shopmate.domain.model;

import java.util.UUID;

public record ExportFailure(UUID itemId, String reason) {}
