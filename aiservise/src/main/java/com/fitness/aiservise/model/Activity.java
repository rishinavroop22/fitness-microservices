package com.fitness.aiservise.model;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.Map;


@Data

public class Activity {

    private String id;
    private String userID;
    private String type;
    private Integer duration;
    private Integer caloriesBurned;
    private LocalDateTime startTime;
    public Map<String, Object> additionalMetrics;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

}
