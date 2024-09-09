/*
 * Copyright (c) 2023, RTE (http://www.rte-france.com)
 *  This Source Code Form is subject to the terms of the Mozilla Public
 *  License, v. 2.0. If a copy of the MPL was not distributed with this
 *  file, You can obtain one at http://mozilla.org/MPL/2.0/.
 *
 */

package com.farao_community.farao.gridcapa_core_cc.api.resource;

import com.farao_community.farao.gridcapa.task_manager.api.TaskParameterDto;
import org.apache.commons.lang3.builder.ToStringBuilder;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;

/**
 * @author Godelaine de Montmorillon {@literal <godelaine.demontmorillon at rte-france.com>}
 * @author Philippe Edwards {@literal <philippe.edwards at rte-france.com>}
 */
public class InternalCoreCCRequest {
    private CoreCCRequest coreCCRequest;
    private HourlyRaoRequest hourlyRaoRequest;
    private HourlyRaoResult hourlyRaoResult;
    private int version = 1;
    private Instant requestReceivedInstant;
    private String timeInterval;
    private String correlationId;

    public InternalCoreCCRequest(CoreCCRequest coreCCRequest) {
        this.coreCCRequest = coreCCRequest;
    }

    public String getId() {
        return coreCCRequest.getId();
    }

    public String getRunId() {
        return coreCCRequest.getCurrentRunId();
    }

    public OffsetDateTime getTimestamp() {
        return coreCCRequest.getTimestamp();
    }

    public CoreCCFileResource getCgm() {
        return coreCCRequest.getCgm();
    }

    public CoreCCFileResource getDcCgm() {
        return coreCCRequest.getDcCgm();
    }

    public CoreCCFileResource getCbcora() {
        return coreCCRequest.getCbcora();
    }

    public CoreCCFileResource getGlsk() {
        return coreCCRequest.getGlsk();
    }

    public CoreCCFileResource getRefProg() {
        return coreCCRequest.getRefProg();
    }

    public CoreCCFileResource getRaoRequest() {
        return coreCCRequest.getRaoRequest();
    }

    public CoreCCFileResource getVirtualHub() {
        return coreCCRequest.getVirtualHub();
    }

    public List<TaskParameterDto> getParameters() {
        return coreCCRequest.getTaskParameterList();
    }

    public HourlyRaoRequest getHourlyRaoRequest() {
        return hourlyRaoRequest;
    }

    public void setHourlyRaoRequest(HourlyRaoRequest hourlyInput) {
        this.hourlyRaoRequest = hourlyInput;
    }

    public HourlyRaoResult getHourlyRaoResult() {
        return hourlyRaoResult;
    }

    public void setHourlyRaoResult(HourlyRaoResult hourlyArtifact) {
        this.hourlyRaoResult = hourlyArtifact;
    }

    public int getVersion() {
        return version;
    }

    public void setVersion(int version) {
        this.version = version;
    }

    public Instant getRequestReceivedInstant() {
        return requestReceivedInstant;
    }

    public void setRequestReceivedInstant(Instant inputsReceivedInstant) {
        this.requestReceivedInstant = inputsReceivedInstant;
    }

    public String getTimeInterval() {
        return timeInterval;
    }

    public void setTimeInterval(String timeInterval) {
        this.timeInterval = timeInterval;
    }

    public String getCorrelationId() {
        return correlationId;
    }

    public void setCorrelationId(String correlationId) {
        this.correlationId = correlationId;
    }

    @Override
    public String toString() {
        return ToStringBuilder.reflectionToString(this);
    }

}
