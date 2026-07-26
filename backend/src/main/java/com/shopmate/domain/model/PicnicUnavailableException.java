package com.shopmate.domain.model;

public class PicnicUnavailableException extends RuntimeException {
    public PicnicUnavailableException(String message) {
        super(message);
    }

    public PicnicUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
