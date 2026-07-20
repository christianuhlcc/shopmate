package com.shopmate.application.service;

import com.shopmate.domain.model.Group;
import com.shopmate.domain.model.NoGroupException;
import com.shopmate.domain.model.User;
import com.shopmate.domain.model.UserNotFoundException;
import com.shopmate.domain.port.in.GroupUseCase;
import com.shopmate.domain.port.out.GroupRepository;
import com.shopmate.domain.port.out.UserRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
public class GroupService implements GroupUseCase {

    private final GroupRepository groupRepository;
    private final UserRepository userRepository;

    public GroupService(GroupRepository groupRepository, UserRepository userRepository) {
        this.groupRepository = groupRepository;
        this.userRepository = userRepository;
    }

    @Override
    public Group getGroupForUser(UUID userId) {
        UUID groupId = requireGroupId(userId);
        return groupRepository.findById(groupId)
            .orElseThrow(() -> new NoGroupException(userId));
    }

    @Override
    public List<User> getGroupMembers(UUID userId) {
        UUID groupId = requireGroupId(userId);
        return userRepository.findAllByGroupId(groupId);
    }

    private UUID requireGroupId(UUID userId) {
        User user = userRepository.findById(userId)
            .orElseThrow(() -> new UserNotFoundException("No user with id: " + userId));
        if (user.groupId() == null) {
            throw new NoGroupException(userId);
        }
        return user.groupId();
    }
}
