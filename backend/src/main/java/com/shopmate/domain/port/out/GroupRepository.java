package com.shopmate.domain.port.out;

import com.shopmate.domain.model.Group;

import java.util.Optional;
import java.util.UUID;

public interface GroupRepository {
    Optional<Group> findById(UUID id);
    Group save(Group group);
}
