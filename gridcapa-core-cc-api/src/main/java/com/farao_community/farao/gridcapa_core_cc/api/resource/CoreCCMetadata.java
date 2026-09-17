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
    private String raoRequestFileName;
    private String requestReceivedInstant;
    private String raoRequestInstant;
    private String timeInterval;
    private String correlationId;
    private int version;

    private String continentalComputationStart;
    private String continentalComputationEnd;
    private String continentalComputationStatus;
    private String continentalComputationErrorCode;
    private String continentalComputationErrorMessage;

    private String semComputationStart;
    private String semComputationEnd;
    private String semComputationStatus;
    private String semComputationErrorCode;
    private String semComputationErrorMessage;

    @JsonCreator
    public CoreCCMetadata(@JsonProperty("raoRequestFileName") String raoRequestFileName,
                          @JsonProperty("requestReceivedInstant") String requestReceivedInstant,
                          @JsonProperty("raoRequestInstant") String raoRequestInstant,
                          @JsonProperty("timeInterval") String timeInterval,
                          @JsonProperty("correlationId") String correlationId,
                          @JsonProperty("version") int version,
                          @JsonProperty("continentalComputationStart") String continentalComputationStart,
                          @JsonProperty("continentalComputationEnd") String continentalComputationEnd,
                          @JsonProperty("continentalComputationStatus") String continentalComputationStatus,
                          @JsonProperty("continentalComputationErrorCode") String continentalComputationErrorCode,
                          @JsonProperty("continentalComputationErrorMessage") String continentalComputationErrorMessage,
                          @JsonProperty("semComputationStart") String semComputationStart,
                          @JsonProperty("semComputationEnd") String semComputationEnd,
                          @JsonProperty("semComputationStatus") String semComputationStatus,
                          @JsonProperty("semComputationErrorCode") String semComputationErrorCode,
                          @JsonProperty("semComputationErrorMessage") String semComputationErrorMessage) {
        this.raoRequestFileName = raoRequestFileName;
        this.requestReceivedInstant = requestReceivedInstant;
        this.raoRequestInstant = raoRequestInstant;
        this.timeInterval = timeInterval;
        this.correlationId = correlationId;
        this.version = version;

        this.continentalComputationStart = continentalComputationStart;
        this.continentalComputationEnd = continentalComputationEnd;
        this.continentalComputationStatus = continentalComputationStatus;
        this.continentalComputationErrorCode = continentalComputationErrorCode;
        this.continentalComputationErrorMessage = continentalComputationErrorMessage;

        this.semComputationStart = semComputationStart;
        this.semComputationEnd = semComputationEnd;
        this.semComputationStatus = semComputationStatus;
        this.semComputationErrorCode = semComputationErrorCode;
        this.semComputationErrorMessage = semComputationErrorMessage;
    }

    public String getRaoRequestFileName() {
        return raoRequestFileName;
    }

    public String getRequestReceivedInstant() {
        return requestReceivedInstant;
    }

    public String getRaoRequestInstant() {
        return raoRequestInstant;
    }

    public String getTimeInterval() {
        return timeInterval;
    }

    public String getCorrelationId() {
        return correlationId;
    }

    public int getVersion() {
        return version;
    }

    public String getContinentalComputationStart() {
        return continentalComputationStart;
    }

    public String getContinentalComputationEnd() {
        return continentalComputationEnd;
    }

    public String getContinentalComputationStatus() {
        return continentalComputationStatus;
    }

    public String getContinentalComputationErrorCode() {
        return continentalComputationErrorCode;
    }

    public String getContinentalComputationErrorMessage() {
        return continentalComputationErrorMessage;
    }

    public String getSemComputationStart() {
        return semComputationStart;
    }

    public String getSemComputationEnd() {
        return semComputationEnd;
    }

    public String getSemComputationStatus() {
        return semComputationStatus;
    }

    public String getSemComputationErrorCode() {
        return semComputationErrorCode;
    }

    public String getSemComputationErrorMessage() {
        return semComputationErrorMessage;
    }

    @Override
    public String toString() {
        return ToStringBuilder.reflectionToString(this);
    }

    public static class Builder {
        private String raoRequestFileName;
        private String requestReceivedInstant;
        private String raoRequestInstant;
        private String timeInterval;
        private String correlationId;
        private int version;

        private String continentalComputationStart;
        private String continentalComputationEnd;
        private String continentalComputationStatus;
        private String continentalComputationErrorCode;
        private String continentalComputationErrorMessage;

        private String semComputationStart;
        private String semComputationEnd;
        private String semComputationStatus;
        private String semComputationErrorCode;
        private String semComputationErrorMessage;

        public Builder() {
        }

        public Builder withRaoRequestFileName(final String raoRequestFileName) {
            this.raoRequestFileName = raoRequestFileName;
            return this;
        }

        public Builder withRequestReceivedInstant(final String requestReceivedInstant) {
            this.requestReceivedInstant = requestReceivedInstant;
            return this;
        }

        public Builder withRaoRequestInstant(final String raoRequestInstant) {
            this.raoRequestInstant = raoRequestInstant;
            return this;
        }

        public Builder withTimeInterval(final String timeInterval) {
            this.timeInterval = timeInterval;
            return this;
        }

        public Builder withCorrelationId(final String correlationId) {
            this.correlationId = correlationId;
            return this;
        }

        public Builder withVersion(final int version) {
            this.version = version;
            return this;
        }

        public Builder withContinentalComputationStartInstant(final String computationStart) {
            this.continentalComputationStart = computationStart;
            return this;
        }

        public Builder withContinentalComputationEndInstant(final String computationEnd) {
            this.continentalComputationEnd = computationEnd;
            return this;
        }

        public Builder withContinentalComputationStatus(final String status) {
            this.continentalComputationStatus = status;
            return this;
        }

        public Builder withContinentalComputationErrorCode(final String errorCode) {
            this.continentalComputationErrorCode = errorCode;
            return this;
        }

        public Builder withContinentalComputationErrorMessage(final String errorMessage) {
            this.continentalComputationErrorMessage = errorMessage;
            return this;
        }

        public Builder withSemComputationStartInstant(final String computationStart) {
            this.semComputationStart = computationStart;
            return this;
        }

        public Builder withSemComputationEndInstant(final String computationEnd) {
            this.semComputationEnd = computationEnd;
            return this;
        }

        public Builder withSemComputationStatus(final String status) {
            this.semComputationStatus = status;
            return this;
        }

        public Builder withSemComputationErrorCode(final String errorCode) {
            this.semComputationErrorCode = errorCode;
            return this;
        }

        public Builder withSemComputationErrorMessage(final String errorMessage) {
            this.semComputationErrorMessage = errorMessage;
            return this;
        }

        public CoreCCMetadata build() {
            return new CoreCCMetadata(
                raoRequestFileName,
                requestReceivedInstant,
                raoRequestInstant,
                timeInterval,
                correlationId,
                version,
                continentalComputationStart,
                continentalComputationEnd,
                continentalComputationStatus,
                continentalComputationErrorCode,
                continentalComputationErrorMessage,
                semComputationStart,
                semComputationEnd,
                semComputationStatus,
                semComputationErrorCode,
                semComputationErrorMessage
            );
        }
    }
}
