package com.shopmate.domain.model;

import java.util.UUID;

/**
 * The user started linking but never completed the SMS step, so only a provisional session
 * exists. Distinct from PicnicCredentialsMissingException: the frontend resumes at the code
 * entry step rather than asking for the password again.
 */
public class PicnicSecondFactorRequiredException extends RuntimeException {
    public PicnicSecondFactorRequiredException(UUID userId) {
        super("Picnic link is awaiting second-factor verification for user: " + userId);
    }
}
