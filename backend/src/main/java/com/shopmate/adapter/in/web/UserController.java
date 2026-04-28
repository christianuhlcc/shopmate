package com.shopmate.adapter.in.web;

import com.shopmate.domain.model.User;
import com.shopmate.domain.port.out.UserRepository;
import com.shopmate.generated.api.UsersApi;
import com.shopmate.generated.model.UserProfile;
import com.shopmate.infrastructure.security.SecurityContextHelper;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
public class UserController implements UsersApi {

    private final UserRepository userRepository;
    private final SecurityContextHelper securityContextHelper;

    public UserController(UserRepository userRepository, SecurityContextHelper securityContextHelper) {
        this.userRepository = userRepository;
        this.securityContextHelper = securityContextHelper;
    }

    @Override
    public ResponseEntity<UserProfile> getCurrentUser() {
        UUID currentUserId = securityContextHelper.getCurrentUserId();
        User user = userRepository.findById(currentUserId)
                .orElseThrow(() -> new com.shopmate.domain.model.UserNotFoundException("User not found: " + currentUserId));
        UserProfile profile = new UserProfile(user.id(), user.email(), user.displayName())
                .avatarUrl(user.avatarUrl());
        return ResponseEntity.ok(profile);
    }
}
