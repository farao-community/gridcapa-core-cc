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
    private final String raoRequestInstant;

    private String networkWithPraUrl;
    private String raoResultFileUrl;
    private HourlyRaoResultStatus status = HourlyRaoResultStatus.PENDING;
    private HourlyRaoResultErrorCode errorCode = HourlyRaoResultErrorCode.UNKNOWN_FAILURE;
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

    public String getRaoResultFileUrl() {
        return raoResultFileUrl;
    }

    public HourlyRaoResultErrorCode getErrorCode() {
        return errorCode;
    }

    public String getErrorCodeString() {
        return errorCode.getCode();
    }

    public void setErrorCode(HourlyRaoResultErrorCode errorCode) {
        this.errorCode = errorCode;
    }

    public HourlyRaoResultStatus getStatus() {
        return status;
    }

    public void setStatus(HourlyRaoResultStatus status) {
        this.status = status;
    }

    public boolean isFailed() {
        return status == HourlyRaoResultStatus.FAILURE;
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

    @Override
    public boolean equals(final Object o) {
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
}
