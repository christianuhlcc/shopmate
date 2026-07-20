package com.shopmate.domain.port.in;

import com.shopmate.domain.model.InviteCode;
import com.shopmate.domain.model.InviteType;
import com.shopmate.domain.model.User;

import java.util.UUID;

public interface InviteUseCase {

    InviteCode createInvite(UUID userId, InviteType type);

    User redeemInvite(UUID userId, String code, String groupName);
}
