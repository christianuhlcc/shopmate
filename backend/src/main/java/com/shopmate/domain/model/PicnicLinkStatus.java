package com.shopmate.domain.model;

/**
 * Whether the requesting user has a usable Picnic account linked. {@code email} is null when
 * nothing is stored at all; {@code state} is null in that same case and otherwise reports how
 * far linking got.
 */
public record PicnicLinkStatus(boolean linked, String email, PicnicLinkState state) {

    public static PicnicLinkStatus notLinked() {
        return new PicnicLinkStatus(false, null, null);
    }
}
