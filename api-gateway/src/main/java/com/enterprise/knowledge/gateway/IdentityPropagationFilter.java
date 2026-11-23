package com.enterprise.knowledge.gateway;

import com.enterprise.knowledge.gateway.user.UserSyncService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

/**
 * Runs after Spring Security has validated the Keycloak JWT. It
 * <ol>
 *   <li>strips any identity headers sent by the client (they cannot be trusted),</li>
 *   <li>makes sure the caller exists in user-service (first login auto-provisions them), and</li>
 *   <li>forwards {@code X-User-ID} and {@code X-User-Department} taken from the verified token.</li>
 * </ol>
 */
@Component
@RequiredArgsConstructor
public class IdentityPropagationFilter implements WebFilter {

    public static final String USER_ID = "X-User-ID";
    public static final String USER_DEPARTMENT = "X-User-Department";
    public static final String DEFAULT_DEPARTMENT = "ALL";

    private final UserSyncService userSyncService;

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        ServerHttpRequest stripped = exchange.getRequest().mutate()
                .headers(h -> {
                    h.remove(USER_ID);
                    h.remove(USER_DEPARTMENT);
                })
                .build();
        ServerWebExchange cleanExchange = exchange.mutate().request(stripped).build();

        return ReactiveSecurityContextHolder.getContext()
                .map(SecurityContext::getAuthentication)
                .filter(JwtAuthenticationToken.class::isInstance)
                .map(auth -> ((JwtAuthenticationToken) auth).getToken())
                .flatMap(jwt -> userSyncService.ensureUser(jwt).thenReturn(withIdentity(cleanExchange, jwt)))
                .defaultIfEmpty(cleanExchange)
                .flatMap(chain::filter);
    }

    public static String department(Jwt jwt) {
        String department = jwt.getClaimAsString("department");
        return department == null || department.isBlank() ? DEFAULT_DEPARTMENT : department.trim().toUpperCase();
    }

    private ServerWebExchange withIdentity(ServerWebExchange exchange, Jwt jwt) {
        ServerHttpRequest request = exchange.getRequest().mutate()
                .header(USER_ID, jwt.getSubject())
                .header(USER_DEPARTMENT, department(jwt))
                .build();
        return exchange.mutate().request(request).build();
    }
}
