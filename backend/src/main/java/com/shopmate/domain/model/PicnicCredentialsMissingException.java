package com.shopmate.domain.model;

import java.util.UUID;

public class PicnicCredentialsMissingException extends RuntimeException {
    public PicnicCredentialsMissingException(UUID userId) {
        super("No Picnic credentials linked for user: " + userId);
    }
}
