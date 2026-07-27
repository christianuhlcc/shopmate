package com.shopmate.domain.model;

/**
 * Lifecycle of a user's Picnic link. {@code PENDING_SECOND_FACTOR} holds a provisional
 * session that can do nothing except complete 2FA.
 */
public enum PicnicLinkState {
    PENDING_SECOND_FACTOR,
    LINKED
}
