package com.enterprise.knowledge.mcp.tools;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class UserDirectoryTools {

    private final RestClient userServiceClient;

    public UserDirectoryTools(RestClient userServiceClient) {
        this.userServiceClient = userServiceClient;
    }

    @Tool(name = "get_user_profile",
            description = "Get a user's profile (name, email, department, role) from the user directory.")
    public String getUserProfile(@ToolParam(description = "The user's id") String userId) {
        return BackendCall.run("get_user_profile", () -> userServiceClient.get()
                .uri("/api/users/{id}", userId).retrieve().body(String.class));
    }

    @Tool(name = "find_users_by_department",
            description = "List the people in a department, e.g. to find who owns a policy area.")
    public String findUsersByDepartment(@ToolParam(description = "Department name, e.g. HR") String department) {
        return BackendCall.run("find_users_by_department", () -> userServiceClient.get()
                .uri(b -> b.path("/api/users").queryParam("department", department).build())
                .retrieve().body(String.class));
    }
}
