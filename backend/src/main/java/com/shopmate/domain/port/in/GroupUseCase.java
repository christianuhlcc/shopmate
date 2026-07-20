package com.shopmate.domain.port.in;

import com.shopmate.domain.model.Group;
import com.shopmate.domain.model.User;

import java.util.List;
import java.util.UUID;

public interface GroupUseCase {

    Group getGroupForUser(UUID userId);

    List<User> getGroupMembers(UUID userId);
}
