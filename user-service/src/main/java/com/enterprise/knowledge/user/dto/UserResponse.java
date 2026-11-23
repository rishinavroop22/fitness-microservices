package com.enterprise.knowledge.user.dto;

import com.enterprise.knowledge.user.model.User;

import java.time.LocalDateTime;

public record UserResponse(
        String id,
        String keycloakId,
        String email,
        String firstName,
        String lastName,
        String department,
        String role,
        LocalDateTime createdAt) {

    public static UserResponse from(User user) {
        return new UserResponse(String.valueOf(user.getId()), user.getKeycloakId(), user.getEmail(),
                user.getFirstName(), user.getLastName(), user.getDepartment(), user.getRole().name(),
                user.getCreatedAt());
    }
}
