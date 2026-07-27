package com.shopmate.domain.model;

/**
 * A usable Picnic session: the {@code x-picnic-auth} key plus the device id it was issued
 * against. Both travel together on every authenticated call — Picnic ties the key to the
 * device that obtained it, so a key without its device id is not usable (ADR-0014 amendment).
 */
public record PicnicSession(String authKey, String deviceId) {}
