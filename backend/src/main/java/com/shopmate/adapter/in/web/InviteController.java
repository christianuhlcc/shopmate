package com.shopmate.adapter.in.web;

import com.shopmate.domain.model.Group;
import com.shopmate.domain.model.InviteCode;
import com.shopmate.domain.model.User;
import com.shopmate.domain.port.in.InviteUseCase;
import com.shopmate.domain.port.out.GroupRepository;
import com.shopmate.generated.api.InvitesApi;
import com.shopmate.generated.model.CreateInviteRequest;
import com.shopmate.generated.model.GroupSummary;
import com.shopmate.generated.model.InviteCodeResponse;
import com.shopmate.generated.model.RedeemInviteRequest;
import com.shopmate.generated.model.UserProfile;
import com.shopmate.infrastructure.security.SecurityContextHelper;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

// The OpenAPI spec declares `servers: /api`, but the generator does not include
// that base path in the interface mappings — it must be added at class level.
@RestController
@RequestMapping("/api")
public class InviteController implements InvitesApi {

    private final InviteUseCase inviteUseCase;
    private final GroupRepository groupRepository;
    private final SecurityContextHelper securityContextHelper;

    public InviteController(InviteUseCase inviteUseCase,
                             GroupRepository groupRepository,
                             SecurityContextHelper securityContextHelper) {
        this.inviteUseCase = inviteUseCase;
        this.groupRepository = groupRepository;
        this.securityContextHelper = securityContextHelper;
    }

    @Override
    public ResponseEntity<InviteCodeResponse> createInvite(@Valid @RequestBody CreateInviteRequest createInviteRequest) {
        UUID currentUserId = securityContextHelper.getCurrentUserId();
        InviteCode invite = inviteUseCase.createInvite(currentUserId, mapType(createInviteRequest.getType()));
        InviteCodeResponse response = new InviteCodeResponse(
                invite.code(),
                mapType(invite.type()),
                OffsetDateTime.ofInstant(invite.expiresAt(), ZoneOffset.UTC));
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @Override
    public ResponseEntity<UserProfile> redeemInvite(@Valid @RequestBody RedeemInviteRequest redeemInviteRequest) {
        UUID currentUserId = securityContextHelper.getCurrentUserId();
        User updated = inviteUseCase.redeemInvite(currentUserId, redeemInviteRequest.getCode(), redeemInviteRequest.getGroupName());
        UserProfile profile = new UserProfile(updated.id(), updated.email(), updated.displayName())
                .avatarUrl(updated.avatarUrl());
        if (updated.groupId() != null) {
            groupRepository.findById(updated.groupId())
                    .ifPresent(group -> profile.group(toGroupSummary(group)));
        }
        return ResponseEntity.ok(profile);
    }

    private GroupSummary toGroupSummary(Group group) {
        return new GroupSummary(group.id(), group.name());
    }

    private com.shopmate.domain.model.InviteType mapType(com.shopmate.generated.model.InviteType generated) {
        return switch (generated) {
            case JOIN_GROUP -> com.shopmate.domain.model.InviteType.JOIN_GROUP;
            case NEW_GROUP -> com.shopmate.domain.model.InviteType.NEW_GROUP;
        };
    }

    private com.shopmate.generated.model.InviteType mapType(com.shopmate.domain.model.InviteType domain) {
        return switch (domain) {
            case JOIN_GROUP -> com.shopmate.generated.model.InviteType.JOIN_GROUP;
            case NEW_GROUP -> com.shopmate.generated.model.InviteType.NEW_GROUP;
        };
    }
}
