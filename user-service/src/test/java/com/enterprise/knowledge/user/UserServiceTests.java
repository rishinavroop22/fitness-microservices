package com.enterprise.knowledge.user;

import com.enterprise.knowledge.user.dto.SyncUserRequest;
import com.enterprise.knowledge.user.dto.UserResponse;
import com.enterprise.knowledge.user.service.UserNotFoundException;
import com.enterprise.knowledge.user.service.UserService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
class UserServiceTests {

    @Autowired
    private UserService userService;

    @Test
    void syncIsIdempotentAndNormalisesDepartment() {
        UserResponse first = userService.sync(new SyncUserRequest("kc-1", "asha@corp.com", "Asha", "Rao", "hr"));
        UserResponse second = userService.sync(new SyncUserRequest("kc-1", "asha@corp.com", "Asha", "Rao", "HR"));

        assertThat(second.id()).isEqualTo(first.id());
        assertThat(second.department()).isEqualTo("HR");
        assertThat(userService.exists("kc-1")).isTrue();
        assertThat(userService.findByDepartment("hr")).hasSize(1);
    }

    @Test
    void unknownUserRaisesNotFound() {
        assertThatThrownBy(() -> userService.getByKeycloakId("missing")).isInstanceOf(UserNotFoundException.class);
    }
}
