/*
 * Copyright (c) 2020, RTE (http://www.rte-france.com)
 */
package com.farao_community.farao.gridcapa_core_cc.app.messaging;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.github.jasminb.jsonapi.annotations.Id;
import com.github.jasminb.jsonapi.annotations.Type;

import java.time.LocalDate;

@Type("process-start-request")
public class ProcessStartRequest {
    @Id
    private final String id;
    private final LocalDate targetDate;

    @JsonCreator
    public ProcessStartRequest(@JsonProperty("id") String id, @JsonProperty("targetDate") LocalDate targetDate) {
        this.id = id;
        this.targetDate = targetDate;
    }

    public String getId() {
        return id;
    }

    public LocalDate getTargetDate() {
        return targetDate;
    }
}

