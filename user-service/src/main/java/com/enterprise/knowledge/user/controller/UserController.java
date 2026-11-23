package com.enterprise.knowledge.user.controller;

import com.enterprise.knowledge.user.dto.SyncUserRequest;
import com.enterprise.knowledge.user.dto.UserResponse;
import com.enterprise.knowledge.user.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    @PostMapping("/sync")
    public ResponseEntity<UserResponse> sync(@Valid @RequestBody SyncUserRequest request) {
        return ResponseEntity.ok(userService.sync(request));
    }

    @GetMapping("/me")
    public ResponseEntity<UserResponse> me(@RequestHeader("X-User-ID") String userId) {
        return ResponseEntity.ok(userService.getByKeycloakId(userId));
    }

    @GetMapping("/{keycloakId}")
    public ResponseEntity<UserResponse> getUser(@PathVariable String keycloakId) {
        return ResponseEntity.ok(userService.getByKeycloakId(keycloakId));
    }

    @GetMapping("/{keycloakId}/validate")
    public ResponseEntity<Boolean> validate(@PathVariable String keycloakId) {
        return ResponseEntity.ok(userService.exists(keycloakId));
    }

    @GetMapping
    public ResponseEntity<List<UserResponse>> byDepartment(@RequestParam String department) {
        return ResponseEntity.ok(userService.findByDepartment(department));
    }
}
