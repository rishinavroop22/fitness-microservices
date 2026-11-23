package com.enterprise.knowledge.user.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record SyncUserRequest(
        @NotBlank String keycloakId,
        @Email String email,
        String firstName,
        String lastName,
        String department) {
}
