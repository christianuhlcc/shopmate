package com.shopmate.domain.model;

public class InviteExpiredException extends RuntimeException {
    public InviteExpiredException(String message) {
        super(message);
    }
}
