package com.shopmate.domain.model;

import java.util.UUID;

public class AlreadyInGroupException extends RuntimeException {
    public AlreadyInGroupException(UUID userId) {
        super("User already belongs to a group: " + userId);
    }
}
