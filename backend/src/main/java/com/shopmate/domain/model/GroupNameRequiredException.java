package com.shopmate.domain.model;

public class GroupNameRequiredException extends RuntimeException {
    public GroupNameRequiredException() {
        super("Group name is required to redeem a NEW_GROUP invite");
    }
}
