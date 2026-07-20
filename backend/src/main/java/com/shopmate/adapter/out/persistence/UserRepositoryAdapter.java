package com.shopmate.adapter.out.persistence;

import com.shopmate.adapter.out.persistence.entity.UserEntity;
import com.shopmate.adapter.out.persistence.repository.SpringDataUserRepository;
import com.shopmate.domain.model.User;
import com.shopmate.domain.port.out.UserRepository;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;
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

    @Override
    public List<User> findAllByGroupId(UUID groupId) {
        // Stub: UserEntity has no group_id column yet (added in task A3, which also
        // provides the real derived-query implementation).
        throw new UnsupportedOperationException("findAllByGroupId not yet implemented — see task A3");
    }

    User toDomain(UserEntity e) {
        // TODO(A3): UserEntity has no group_id column yet; wire it through once it does.
        return new User(e.getId(), e.getEmail(), e.getDisplayName(), e.getAvatarUrl(), null);
    }
}
