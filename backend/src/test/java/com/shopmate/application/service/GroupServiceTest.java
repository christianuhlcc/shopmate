package com.shopmate.application.service;

import com.shopmate.domain.model.Group;
import com.shopmate.domain.model.NoGroupException;
import com.shopmate.domain.model.User;
import com.shopmate.domain.model.UserNotFoundException;
import com.shopmate.domain.port.out.GroupRepository;
import com.shopmate.domain.port.out.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GroupServiceTest {

    @Mock GroupRepository groupRepository;
    @Mock UserRepository userRepository;

    GroupService service;

    private static final UUID USER_ID = UUID.randomUUID();
    private static final UUID GROUP_ID = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service = new GroupService(groupRepository, userRepository);
    }

    private User groupedUser() {
        return new User(USER_ID, "user@example.com", "User", null, GROUP_ID);
    }

    private User grouplessUser() {
        return new User(USER_ID, "user@example.com", "User", null, null);
    }

    @Test
    void getGroupForUserThrowsNoGroupExceptionForGrouplessUser() {
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(grouplessUser()));
        assertThatThrownBy(() -> service.getGroupForUser(USER_ID))
            .isInstanceOf(NoGroupException.class);
    }

    @Test
    void getGroupForUserThrowsUserNotFoundForUnknownUser() {
        when(userRepository.findById(USER_ID)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.getGroupForUser(USER_ID))
            .isInstanceOf(UserNotFoundException.class);
    }

    @Test
    void getGroupForUserReturnsGroup() {
        Group group = new Group(GROUP_ID, "Household", Instant.now());
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(groupedUser()));
        when(groupRepository.findById(GROUP_ID)).thenReturn(Optional.of(group));

        assertThat(service.getGroupForUser(USER_ID)).isEqualTo(group);
    }

    @Test
    void getGroupForUserThrowsNoGroupExceptionWhenGroupRecordMissing() {
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(groupedUser()));
        when(groupRepository.findById(GROUP_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getGroupForUser(USER_ID))
            .isInstanceOf(NoGroupException.class);
    }

    @Test
    void getGroupMembersThrowsNoGroupExceptionForGrouplessUser() {
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(grouplessUser()));
        assertThatThrownBy(() -> service.getGroupMembers(USER_ID))
            .isInstanceOf(NoGroupException.class);
    }

    @Test
    void getGroupMembersDelegatesToRepository() {
        List<User> members = List.of(groupedUser());
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(groupedUser()));
        when(userRepository.findAllByGroupId(GROUP_ID)).thenReturn(members);

        assertThat(service.getGroupMembers(USER_ID)).isEqualTo(members);
    }
}
