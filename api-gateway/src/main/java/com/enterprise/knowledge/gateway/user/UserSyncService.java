package com.enterprise.knowledge.gateway.user;

import com.enterprise.knowledge.gateway.IdentityPropagationFilter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/** Provisions Keycloak users into user-service on first sight; later requests skip the call. */
@Service
@Slf4j
@RequiredArgsConstructor
public class UserSyncService {

    private final WebClient userServiceWebClient;
    private final Set<String> syncedUsers = ConcurrentHashMap.newKeySet();

    public Mono<Void> ensureUser(Jwt jwt) {
        String subject = jwt.getSubject();
        if (subject == null || syncedUsers.contains(subject)) {
            return Mono.empty();
        }
        SyncUserRequest request = new SyncUserRequest(
                subject,
                jwt.getClaimAsString("email"),
                jwt.getClaimAsString("given_name"),
                jwt.getClaimAsString("family_name"),
                IdentityPropagationFilter.department(jwt));
        return userServiceWebClient.post()
                .uri("/api/users/sync")
                .bodyValue(request)
                .retrieve()
                .toBodilessEntity()
                .doOnSuccess(r -> syncedUsers.add(subject))
                .doOnError(e -> log.warn("User sync failed for {}: {}", subject, e.getMessage()))
                // A user-service outage must not block reads that do not need a profile.
                .onErrorResume(e -> Mono.empty())
                .then();
    }
}
