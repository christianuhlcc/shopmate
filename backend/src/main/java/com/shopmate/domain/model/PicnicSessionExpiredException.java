package com.shopmate.domain.model;

/**
 * The stored session key was refused by Picnic. There is no silent recovery — re-obtaining a
 * key needs the user to complete 2FA again — so this is deliberately distinct from
 * PicnicUnavailableException, which is a transient upstream problem.
 */
public class PicnicSessionExpiredException extends RuntimeException {
    public PicnicSessionExpiredException(String message) {
        super(message);
    }
}
