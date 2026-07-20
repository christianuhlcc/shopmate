package com.shopmate.adapter.out.persistence;

import com.shopmate.adapter.out.persistence.entity.GroupEntity;
import com.shopmate.adapter.out.persistence.repository.SpringDataGroupRepository;
import com.shopmate.domain.model.Group;
import com.shopmate.domain.port.out.GroupRepository;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.UUID;

@Component
public class GroupRepositoryAdapter implements GroupRepository {

    private final SpringDataGroupRepository jpaRepository;

    public GroupRepositoryAdapter(SpringDataGroupRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    public Optional<Group> findById(UUID id) {
        return jpaRepository.findById(id).map(this::toDomain);
    }

    @Override
    public Group save(Group group) {
        GroupEntity entity = jpaRepository.findById(group.id())
            .orElseGet(() -> new GroupEntity(group.id(), group.name(), group.createdAt()));
        entity.setName(group.name());
        return toDomain(jpaRepository.save(entity));
    }

    private Group toDomain(GroupEntity e) {
        return new Group(e.getId(), e.getName(), e.getCreatedAt());
    }
}
