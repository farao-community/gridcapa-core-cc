/*
 * Copyright (c) 2023, RTE (http://www.rte-france.com)
 *  This Source Code Form is subject to the terms of the Mozilla Public
 *  License, v. 2.0. If a copy of the MPL was not distributed with this
 *  file, You can obtain one at http://mozilla.org/MPL/2.0/.
 *
 */

package com.farao_community.farao.gridcapa_core_cc.api.resource;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.apache.commons.lang3.builder.ToStringBuilder;

/**
 * @author Godelaine de Montmorillon {@literal <godelaine.demontmorillon at rte-france.com>}
 */
public class CoreCCMetadata {
    private final String raoRequestFileName;
    private final String requestReceivedInstant;
    private final String raoRequestInstant;

    private final String computationStart;

    private final String computationEnd;
    private final String timeInterval;
    private final String correlationId;

    private final String status;
    private final String errorCode;
    private final String errorMessage;

    private final int version;

    @JsonCreator
    public CoreCCMetadata(@JsonProperty("raoRequestFileName") String raoRequestFileName,
                          @JsonProperty("requestReceivedInstant") String requestReceivedInstant,
                          @JsonProperty("raoRequestInstant") String raoRequestInstant,
                          @JsonProperty("computationStart") String computationStart,
                          @JsonProperty("computationEnd") String computationEnd,
                          @JsonProperty("timeInterval") String timeInterval,
                          @JsonProperty("correlationId") String correlationId,
                          @JsonProperty("status") String status,
                          @JsonProperty("errorCode") String errorCode,
                          @JsonProperty("errorMessage") String errorMessage,
                          @JsonProperty("version") int version) {
        this.raoRequestFileName = raoRequestFileName;
        this.requestReceivedInstant = requestReceivedInstant;
        this.raoRequestInstant = raoRequestInstant;
        this.computationStart = computationStart;
        this.computationEnd = computationEnd;
        this.timeInterval = timeInterval;
        this.correlationId = correlationId;
        this.status = status;
        this.errorCode = errorCode;
        this.errorMessage = errorMessage;
        this.version = version;
    }

    public String getRaoRequestFileName() {
        return raoRequestFileName;
    }

    public String getRequestReceivedInstant() {
        return requestReceivedInstant;
    }

    public String getComputationStart() {
        return computationStart;
    }

    public String getRaoRequestInstant() {
        return raoRequestInstant;
    }

    public String getComputationEnd() {
        return computationEnd;
    }

    public String getTimeInterval() {
        return timeInterval;
    }

    public String getCorrelationId() {
        return correlationId;
    }

    public String getStatus() {
        return status;
    }

    public String getErrorCode() {
        return errorCode;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public int getVersion() {
        return version;
    }

    @Override
    public String toString() {
        return ToStringBuilder.reflectionToString(this);
    }

}
