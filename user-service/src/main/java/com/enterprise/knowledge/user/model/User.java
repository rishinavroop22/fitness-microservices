package com.enterprise.knowledge.user.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;
import java.util.UUID;

/** A Keycloak user mirrored locally so other services and AI tools can look up profile and department. */
@Entity
@Table(name = "users", indexes = @Index(name = "idx_users_department", columnList = "department"))
@Getter
@Setter
@NoArgsConstructor
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, unique = true)
    private String keycloakId;

    @Column(unique = true)
    private String email;

    private String firstName;
    private String lastName;

    @Column(nullable = false)
    private String department = "ALL";

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private UserRole role = UserRole.EMPLOYEE;

    @CreationTimestamp
    private LocalDateTime createdAt;

    @UpdateTimestamp
    private LocalDateTime updatedAt;
}
