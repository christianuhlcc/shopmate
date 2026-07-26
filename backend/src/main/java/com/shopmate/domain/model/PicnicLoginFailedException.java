package com.shopmate.domain.model;

public class PicnicLoginFailedException extends RuntimeException {
    public PicnicLoginFailedException(String message) {
        super(message);
    }
}
