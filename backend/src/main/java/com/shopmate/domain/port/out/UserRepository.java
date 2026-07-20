package com.shopmate.domain.port.out;

import com.shopmate.domain.model.User;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface UserRepository {
    Optional<User> findByEmail(String email);
    Optional<User> findById(UUID id);
    User save(User user);
    List<User> findAllByGroupId(UUID groupId);
}
