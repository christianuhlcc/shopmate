package com.shopmate.adapter.out.persistence;

import com.shopmate.adapter.out.persistence.entity.UserEntity;
import com.shopmate.adapter.out.persistence.repository.SpringDataUserRepository;
import com.shopmate.domain.model.User;
import com.shopmate.domain.port.out.UserRepository;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Component
public class UserRepositoryAdapter implements UserRepository {

    private final SpringDataUserRepository jpaRepository;

    public UserRepositoryAdapter(SpringDataUserRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    public Optional<User> findByEmail(String email) {
        return jpaRepository.findByEmail(email).map(this::toDomain);
    }

    @Override
    public Optional<User> findById(UUID id) {
        return jpaRepository.findById(id).map(this::toDomain);
    }

    @Override
    public User save(User user) {
        UserEntity entity = jpaRepository.findById(user.id())
            .orElseGet(() -> new UserEntity(user.id(), user.email(), user.displayName(), user.avatarUrl(), Instant.now()));
        entity.setDisplayName(user.displayName());
        entity.setAvatarUrl(user.avatarUrl());
        return toDomain(jpaRepository.save(entity));
    }

    User toDomain(UserEntity e) {
        return new User(e.getId(), e.getEmail(), e.getDisplayName(), e.getAvatarUrl());
    }
}
