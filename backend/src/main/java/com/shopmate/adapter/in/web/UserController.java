package com.shopmate.adapter.in.web;

import com.shopmate.domain.model.Group;
import com.shopmate.domain.model.User;
import com.shopmate.domain.port.out.GroupRepository;
import com.shopmate.domain.port.out.UserRepository;
import com.shopmate.generated.api.UsersApi;
import com.shopmate.generated.model.GroupSummary;
import com.shopmate.generated.model.UserProfile;
import com.shopmate.infrastructure.security.SecurityContextHelper;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

// The OpenAPI spec declares `servers: /api`, but the generator does not include
// that base path in the interface mappings — it must be added at class level.
@RestController
@RequestMapping("/api")
public class UserController implements UsersApi {

    private final UserRepository userRepository;
    private final GroupRepository groupRepository;
    private final SecurityContextHelper securityContextHelper;

    public UserController(UserRepository userRepository, GroupRepository groupRepository,
                           SecurityContextHelper securityContextHelper) {
        this.userRepository = userRepository;
        this.groupRepository = groupRepository;
        this.securityContextHelper = securityContextHelper;
    }

    @Override
    public ResponseEntity<UserProfile> getCurrentUser() {
        UUID currentUserId = securityContextHelper.getCurrentUserId();
        User user = userRepository.findById(currentUserId)
                .orElseThrow(() -> new com.shopmate.domain.model.UserNotFoundException("User not found: " + currentUserId));
        UserProfile profile = new UserProfile(user.id(), user.email(), user.displayName())
                .avatarUrl(user.avatarUrl());
        if (user.groupId() != null) {
            groupRepository.findById(user.groupId())
                    .ifPresent(group -> profile.group(toGroupSummary(group)));
        }
        return ResponseEntity.ok(profile);
    }

    private GroupSummary toGroupSummary(Group group) {
        return new GroupSummary(group.id(), group.name());
    }
}
