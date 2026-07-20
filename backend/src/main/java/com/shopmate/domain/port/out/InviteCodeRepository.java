package com.shopmate.domain.port.out;

import com.shopmate.domain.model.InviteCode;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface InviteCodeRepository {
    Optional<InviteCode> findByCode(String code);
    InviteCode save(InviteCode inviteCode);

    /**
     * Conditionally marks the invite used ({@code UPDATE ... WHERE used_by IS NULL}),
     * so concurrent redemptions of the same single-use code can race safely.
     *
     * @return true if this call was the one that marked it used, false if it was
     *         already used by a concurrent redemption.
     */
    boolean markUsed(UUID inviteId, UUID usedBy, Instant usedAt);
}
