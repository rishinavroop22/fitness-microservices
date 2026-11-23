package com.enterprise.knowledge.gateway.user;

public record SyncUserRequest(String keycloakId, String email, String firstName, String lastName, String department) {
}
