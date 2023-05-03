/*
 * Copyright (c) 2022, RTE (http://www.rte-france.com)
 */
package com.farao_community.farao.gridcapa_core_cc.app.messaging;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Objects;

/**
 * @author Mohamed Benrejeb {@literal <mohamed.ben-rejeb at rte-france.com>}
 */
public class RaoLogEvent implements Comparable<RaoLogEvent> {
    private String id;
    private String level;
    private String timestamp;
    private String message;

    @JsonCreator
    public RaoLogEvent(@JsonProperty("taskId") String id, @JsonProperty("timestamp") String timestamp, @JsonProperty("level") String level, @JsonProperty("message") String message) {
        this.id = id;
        this.timestamp = timestamp;
        this.level = level;
        this.message = message;
    }

    public String getId() {
        return id;
    }

    public String getTimestamp() {
        return timestamp;
    }

    public String getLevel() {
        return level;
    }

    public String getMessage() {
        return message;
    }

    @Override
    public String toString() {
        String ts = DateTimeFormatter.ofPattern("yyyy'-'MM'-'dd'T'HH':'mm':'ss'.'SSSS'Z'")
            .withZone(ZoneId.of("UTC"))
            .format(OffsetDateTime.parse(this.getTimestamp()));
        return ts + " " + this.getLevel() + " - " + this.getMessage();
    }

    @Override
    public int compareTo(RaoLogEvent o) {
        OffsetDateTime offsetDateTime = OffsetDateTime.parse(timestamp);
        OffsetDateTime offsetDateTimeOther = OffsetDateTime.parse(o.getTimestamp());
        if (offsetDateTime.compareTo(offsetDateTimeOther) == 0) {
            return message.compareTo(o.getMessage());
        }
        return offsetDateTime.compareTo(offsetDateTimeOther);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        RaoLogEvent that = (RaoLogEvent) o;
        return id.equals(that.id) && level.equals(that.level) && timestamp.equals(that.timestamp) && message.equals(that.message);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, level, timestamp, message);
    }
}
