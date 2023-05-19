/*
 * Copyright (c) 2022, RTE (http://www.rte-france.com)
 */
package com.farao_community.farao.gridcapa_core_cc.app.messaging;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

/**
 * @author Mohamed Benrejeb {@literal <mohamed.ben-rejeb at rte-france.com>}
 */
public class RaoLogEvent {
    private final String gridcapaTaskId;
    private final String id;

    private final String level;
    private final String timestamp;
    private final String message;
    private final String serviceName;
    private final String eventPrefix;

    @JsonCreator
    public RaoLogEvent(@JsonProperty("gridcapa-task-id") String gridcapaTaskId, @JsonProperty("id") String id, @JsonProperty("level") String level, @JsonProperty("timestamp") String timestamp, @JsonProperty("message") String message, @JsonProperty("serviceName") String serviceName, @JsonProperty("eventPrefix") String eventPrefix) {
        this.gridcapaTaskId = gridcapaTaskId;
        this.id = id;
        this.level = level;
        this.timestamp = timestamp;
        this.message = message;
        this.serviceName = serviceName;
        this.eventPrefix = eventPrefix;
    }

    public RaoLogEvent(@JsonProperty("gridcapa-task-id") String gridcapaTaskId, @JsonProperty("id") String id, @JsonProperty("level") String level, @JsonProperty("timestamp") String timestamp, @JsonProperty("message") String message, @JsonProperty("serviceName") String serviceName) {
        this(gridcapaTaskId, id, level, timestamp, message, serviceName, null);
    }

    public String getGridcapaTaskId() {
        return gridcapaTaskId;
    }

    public String getId() {
        return id;
    }

    public String getLevel() {
        return level;
    }

    public String getTimestamp() {
        return timestamp;
    }

    public String getMessage() {
        return message;
    }

    public String getServiceName() {
        return serviceName;
    }

    public String getEventPrefix() {
        return eventPrefix;
    }

    public String toString() {
        String ts = DateTimeFormatter.ofPattern("yyyy'-'MM'-'dd'T'HH':'mm':'ss'.'SSSS'Z'")
            .withZone(ZoneId.of("UTC"))
            .format(OffsetDateTime.parse(this.getTimestamp()));
        return ts + " " + this.getLevel() + " - " + this.getMessage();
    }
}
