package com.shopmate.adapter.in.web;

import com.shopmate.domain.model.Group;
import com.shopmate.domain.model.User;
import com.shopmate.domain.port.in.GroupUseCase;
import com.shopmate.generated.api.GroupsApi;
import com.shopmate.generated.model.GroupResponse;
import com.shopmate.generated.model.UserProfile;
import com.shopmate.infrastructure.security.SecurityContextHelper;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

// The OpenAPI spec declares `servers: /api`, but the generator does not include
// that base path in the interface mappings — it must be added at class level.
@RestController
@RequestMapping("/api")
public class GroupController implements GroupsApi {

    private final GroupUseCase groupUseCase;
    private final SecurityContextHelper securityContextHelper;

    public GroupController(GroupUseCase groupUseCase, SecurityContextHelper securityContextHelper) {
        this.groupUseCase = groupUseCase;
        this.securityContextHelper = securityContextHelper;
    }

    @Override
    public ResponseEntity<GroupResponse> getMyGroup() {
        UUID currentUserId = securityContextHelper.getCurrentUserId();
        Group group = groupUseCase.getGroupForUser(currentUserId);
        List<User> members = groupUseCase.getGroupMembers(currentUserId);
        List<UserProfile> memberProfiles = members.stream()
                .map(this::toUserProfile)
                .toList();
        GroupResponse response = new GroupResponse(
                group.id(),
                group.name(),
                OffsetDateTime.ofInstant(group.createdAt(), ZoneOffset.UTC),
                memberProfiles);
        return ResponseEntity.ok(response);
    }

    private UserProfile toUserProfile(User user) {
        return new UserProfile(user.id(), user.email(), user.displayName())
                .avatarUrl(user.avatarUrl());
    }
}
