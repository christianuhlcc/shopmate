package com.shopmate.domain.model;

public class ListCapacityExceededException extends RuntimeException {
    public ListCapacityExceededException() {
        super("Shopping list has reached the maximum of 100 items");
    }
}
