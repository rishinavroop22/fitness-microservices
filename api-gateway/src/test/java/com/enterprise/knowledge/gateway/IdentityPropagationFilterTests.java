package com.enterprise.knowledge.gateway;

import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class IdentityPropagationFilterTests {

    private Jwt jwt(String department) {
        Jwt.Builder builder = Jwt.withTokenValue("token")
                .header("alg", "none")
                .subject("user-1")
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(60));
        if (department != null) {
            builder.claim("department", department);
        }
        return builder.build();
    }

    @Test
    void departmentClaimIsNormalised() {
        assertThat(IdentityPropagationFilter.department(jwt(" engineering "))).isEqualTo("ENGINEERING");
    }

    @Test
    void missingDepartmentFallsBackToPublicScope() {
        assertThat(IdentityPropagationFilter.department(jwt(null))).isEqualTo("ALL");
    }
}
