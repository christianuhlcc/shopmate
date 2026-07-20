package com.shopmate.application.service;

import com.shopmate.domain.model.AlreadyInGroupException;
import com.shopmate.domain.model.Group;
import com.shopmate.domain.model.GroupNameRequiredException;
import com.shopmate.domain.model.InviteCode;
import com.shopmate.domain.model.InviteExpiredException;
import com.shopmate.domain.model.InviteInvalidException;
import com.shopmate.domain.model.InviteType;
import com.shopmate.domain.model.NoGroupException;
import com.shopmate.domain.model.User;
import com.shopmate.domain.model.UserNotFoundException;
import com.shopmate.domain.port.in.InviteUseCase;
import com.shopmate.domain.port.out.GroupRepository;
import com.shopmate.domain.port.out.InviteCodeRepository;
import com.shopmate.domain.port.out.UserRepository;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

/**
 * Not a Spring bean itself (needs a plain String constructor parameter for the
 * bootstrap code, which can't be resolved by type-based autowiring); wired
 * explicitly as a {@code @Bean} in {@code infrastructure/config}.
 */
public class InviteService implements InviteUseCase {

    static final String CODE_ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    static final int CODE_LENGTH = 8;
    static final Duration INVITE_TTL = Duration.ofDays(7);

    private final InviteCodeRepository inviteCodeRepository;
    private final UserRepository userRepository;
    private final GroupRepository groupRepository;
    private final String bootstrapCode;
    private final SecureRandom random = new SecureRandom();

    public InviteService(InviteCodeRepository inviteCodeRepository,
                          UserRepository userRepository,
                          GroupRepository groupRepository,
                          String bootstrapCode) {
        this.inviteCodeRepository = inviteCodeRepository;
        this.userRepository = userRepository;
        this.groupRepository = groupRepository;
        this.bootstrapCode = bootstrapCode;
    }

    @Override
    public InviteCode createInvite(UUID userId, InviteType type) {
        User user = requireUser(userId);
        if (user.groupId() == null) {
            throw new NoGroupException(userId);
        }

        UUID groupId = type == InviteType.JOIN_GROUP ? user.groupId() : null;
        Instant now = Instant.now();
        InviteCode invite = new InviteCode(UUID.randomUUID(), generateUniqueCode(), type, groupId,
            userId, now, now.plus(INVITE_TTL), null, null);
        return inviteCodeRepository.save(invite);
    }

    @Override
    public User redeemInvite(UUID userId, String code, String groupName) {
        User user = requireUser(userId);
        if (user.groupId() != null) {
            throw new AlreadyInGroupException(userId);
        }

        if (bootstrapCode != null && !bootstrapCode.isBlank() && bootstrapCode.equals(code)) {
            return redeemBootstrapCode(user, groupName);
        }

        InviteCode invite = inviteCodeRepository.findByCode(code)
            .orElseThrow(() -> new InviteInvalidException("Unknown or already-used invite code"));
        if (invite.isUsed()) {
            throw new InviteInvalidException("Unknown or already-used invite code");
        }

        Instant now = Instant.now();
        if (invite.isExpired(now)) {
            throw new InviteExpiredException("Invite code has expired");
        }

        if (invite.type() == InviteType.NEW_GROUP && (groupName == null || groupName.isBlank())) {
            throw new GroupNameRequiredException();
        }

        boolean marked = inviteCodeRepository.markUsed(invite.id(), userId, now);
        if (!marked) {
            throw new InviteInvalidException("Invite code was already redeemed");
        }

        UUID targetGroupId = invite.type() == InviteType.NEW_GROUP
            ? groupRepository.save(new Group(UUID.randomUUID(), groupName, now)).id()
            : invite.groupId();

        return userRepository.save(withGroup(user, targetGroupId));
    }

    private User redeemBootstrapCode(User user, String groupName) {
        if (groupName == null || groupName.isBlank()) {
            throw new GroupNameRequiredException();
        }
        Group group = groupRepository.save(new Group(UUID.randomUUID(), groupName, Instant.now()));
        return userRepository.save(withGroup(user, group.id()));
    }

    private User requireUser(UUID userId) {
        return userRepository.findById(userId)
            .orElseThrow(() -> new UserNotFoundException("No user with id: " + userId));
    }

    private User withGroup(User user, UUID groupId) {
        return new User(user.id(), user.email(), user.displayName(), user.avatarUrl(), groupId);
    }

    private String generateUniqueCode() {
        String code;
        do {
            code = generateCode();
        } while (inviteCodeRepository.findByCode(code).isPresent());
        return code;
    }

    private String generateCode() {
        StringBuilder sb = new StringBuilder(CODE_LENGTH);
        for (int i = 0; i < CODE_LENGTH; i++) {
            sb.append(CODE_ALPHABET.charAt(random.nextInt(CODE_ALPHABET.length())));
        }
        return sb.toString();
    }
}
