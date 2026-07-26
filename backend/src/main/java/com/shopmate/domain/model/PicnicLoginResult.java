package com.shopmate.domain.model;

/**
 * Outcome of a Picnic login. Picnic issues an auth key even when it still wants a second
 * factor, and that provisional key is refused by every real endpoint — so a session alone
 * says nothing about whether the account is usable; {@code secondFactorRequired} does.
 */
public record PicnicLoginResult(PicnicSession session, boolean secondFactorRequired) {}
