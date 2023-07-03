/*
 * Copyright (c) 2021, RTE (http://www.rte-france.com)
 */
package com.farao_community.farao.gridcapa_core_cc.api.resource;

import com.farao_community.farao.rao_runner.api.resource.RaoResponse;

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

    public HourlyRaoResult() {
    }

    public String getRaoRequestInstant() {
        return raoRequestInstant;
    }

    public void setRaoRequestInstant(String raoRequestInstant) {
        this.raoRequestInstant = raoRequestInstant;
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

    public void setRaoResponseData(RaoResponse raoResponse) {
        this.networkWithPraUrl = raoResponse.getNetworkWithPraFileUrl();
        this.raoResultFileUrl = raoResponse.getRaoResultFileUrl();
        this.computationStartInstant = raoResponse.getComputationStartInstant();
        this.computationEndInstant = raoResponse.getComputationEndInstant();
    }

}
