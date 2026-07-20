package com.shopmate.domain.model;

import java.util.UUID;

public class NoGroupException extends RuntimeException {
    public NoGroupException(UUID userId) {
        super("User has no group: " + userId);
    }
}
