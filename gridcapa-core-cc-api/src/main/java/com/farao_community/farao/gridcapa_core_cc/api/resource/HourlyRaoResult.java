/*
 * Copyright (c) 2023, RTE (http://www.rte-france.com)
 *  This Source Code Form is subject to the terms of the Mozilla Public
 *  License, v. 2.0. If a copy of the MPL was not distributed with this
 *  file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.farao_community.farao.gridcapa_core_cc.api.resource;

import com.farao_community.farao.rao_runner.api.resource.RaoSuccessResponse;

import java.time.Instant;
import java.util.Objects;

/**
 * @author Mohamed BenRejeb {@literal <mohamed.ben-rejeb at rte-france.com>}
 */
public class HourlyRaoResult {
    private String raoRequestInstant;

    private String networkWithPraUrl;
    private String raoResultFileUrl;
    private Status status = Status.PENDING;
    private ErrorCode errorCode = ErrorCode.UNKNOWN_FAILURE;
    private String errorMessage;
    private Instant computationStartInstant =  Instant.ofEpochSecond(0);
    private Instant computationEndInstant =  Instant.ofEpochSecond(0);

    public HourlyRaoResult(String raoRequestInstant) {
        this.raoRequestInstant = raoRequestInstant;
    }

    public String getRaoRequestInstant() {
        return raoRequestInstant;
    }

    public String getNetworkWithPraUrl() {
        return networkWithPraUrl;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }

    public enum ErrorCode {
        RUNNING("0"),
        BD_PREPROCESSING_FAILURE("1"),
        TS_PREPROCESSING_FAILURE("2"),
        RAO_FAILURE("3"),
        UNKNOWN_FAILURE("99");

        private String code;

        private ErrorCode(String code) {
            this.code = code;
        }

        public String getCode() {
            return code;
        }
    }

    public enum Status {
        PENDING,
        RUNNING,
        SUCCESS,
        FAILURE
    }

    @Override
    public boolean equals(Object o) {

        if (o == this) {
            return true;
        }
        if (!(o instanceof HourlyRaoResult)) {
            return false;
        }
        HourlyRaoResult hourlyRaoResult = (HourlyRaoResult) o;
        if (hourlyRaoResult.raoRequestInstant != null) {
            return hourlyRaoResult.raoRequestInstant.equals(raoRequestInstant);
        } else {
            return false;
        }
    }

    @Override
    public int hashCode() {
        return Objects.hash(raoRequestInstant);
    }

    public String getRaoResultFileUrl() {
        return raoResultFileUrl;
    }

    public String getErrorCodeString() {
        return errorCode.getCode();
    }

    public void setErrorCode(ErrorCode errorCode) {
        this.errorCode = errorCode;
    }

    public Status getStatus() {
        return status;
    }

    public void setStatus(Status status) {
        this.status = status;
    }

    public Instant getComputationStartInstant() {
        return computationStartInstant;
    }

    public Instant getComputationEndInstant() {
        return computationEndInstant;
    }

    public void setRaoResponseData(RaoSuccessResponse raoResponse) {
        this.networkWithPraUrl = raoResponse.getNetworkWithPraFileUrl();
        this.raoResultFileUrl = raoResponse.getRaoResultFileUrl();
        this.computationStartInstant = raoResponse.getComputationStartInstant();
        this.computationEndInstant = raoResponse.getComputationEndInstant();
    }

}
