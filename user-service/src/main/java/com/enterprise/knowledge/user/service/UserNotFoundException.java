package com.enterprise.knowledge.user.service;

public class UserNotFoundException extends RuntimeException {
    public UserNotFoundException(String keycloakId) {
        super("User not found: " + keycloakId);
    }
}
