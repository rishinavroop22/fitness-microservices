package com.enterprise.knowledge.user.service;

import com.enterprise.knowledge.user.dto.SyncUserRequest;
import com.enterprise.knowledge.user.dto.UserResponse;
import com.enterprise.knowledge.user.model.User;
import com.enterprise.knowledge.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Slf4j
@RequiredArgsConstructor
public class UserService {

    private final UserRepository repository;

    /** Idempotent upsert keyed on the Keycloak subject; called by the gateway on first request. */
    @Transactional
    public UserResponse sync(SyncUserRequest request) {
        User user = repository.findByKeycloakId(request.keycloakId())
                .or(() -> request.email() == null ? java.util.Optional.empty() : repository.findByEmail(request.email()))
                .orElseGet(User::new);
        boolean isNew = user.getId() == null;
        user.setKeycloakId(request.keycloakId());
        user.setEmail(request.email());
        user.setFirstName(request.firstName());
        user.setLastName(request.lastName());
        if (request.department() != null && !request.department().isBlank()) {
            user.setDepartment(request.department().trim().toUpperCase());
        }
        User saved = repository.save(user);
        if (isNew) {
            log.info("Provisioned user {} ({}) in department {}", saved.getEmail(), saved.getKeycloakId(),
                    saved.getDepartment());
        }
        return UserResponse.from(saved);
    }

    @Transactional(readOnly = true)
    public UserResponse getByKeycloakId(String keycloakId) {
        return repository.findByKeycloakId(keycloakId).map(UserResponse::from)
                .orElseThrow(() -> new UserNotFoundException(keycloakId));
    }

    @Transactional(readOnly = true)
    public boolean exists(String keycloakId) {
        return repository.existsByKeycloakId(keycloakId);
    }

    @Transactional(readOnly = true)
    public List<UserResponse> findByDepartment(String department) {
        return repository.findByDepartmentIgnoreCaseOrderByLastNameAsc(department).stream()
                .map(UserResponse::from).toList();
    }
}
