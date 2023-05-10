/*
 * Copyright (c) 2023, RTE (http://www.rte-france.com)
 *  This Source Code Form is subject to the terms of the Mozilla Public
 *  License, v. 2.0. If a copy of the MPL was not distributed with this
 *  file, You can obtain one at http://mozilla.org/MPL/2.0/.
 *
 */

package com.farao_community.farao.gridcapa_core_cc.api.resource;

import com.farao_community.farao.gridcapa_core_cc.api.exception.CoreCCInternalException;
import org.apache.commons.lang3.builder.ToStringBuilder;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.HashSet;
import java.util.Set;

/**
 * @author Godelaine de Montmorillon {@literal <godelaine.demontmorillon at rte-france.com>}
 * @author Philippe Edwards {@literal <philippe.edwards at rte-france.com>}
 */
public class InternalCoreCCRequest {
    private CoreCCRequest coreCCRequest;
    private Set<HourlyRaoRequest> hourlyRaoRequests = new HashSet<>();
    private Set<HourlyRaoResult> hourlyRaoResults = new HashSet<>();
    private DailyOutputs dailyOutputs = new DailyOutputs();
    private int version;
    private Status status;
    private Instant inputsReceivedInstant;
    private Instant computationStartInstant;
    private Instant computationEndInstant;
    private Instant outputsSentInstant;
    private String timeInterval;
    private String correlationId;

    public InternalCoreCCRequest(CoreCCRequest coreCCRequest) {
        this.coreCCRequest = coreCCRequest;
    }

    public enum Status {
        PENDING,
        RUNNING,
        SUCCESS,
        FAILURE
    }

    public String getId() {
        return coreCCRequest.getId();
    }

    public OffsetDateTime getTimestamp() {
        return coreCCRequest.getTimestamp();
    }

    public CoreCCFileResource getCgm() {
        return coreCCRequest.getCgm();
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

    public boolean getLaunchedAutomatically() {
        return coreCCRequest.getLaunchedAutomatically();
    }

    public Set<HourlyRaoRequest> getHourlyRaoRequests() {
        return hourlyRaoRequests;
    }

    public void setHourlyRaoRequests(Set<HourlyRaoRequest> hourlyInputs) {
        this.hourlyRaoRequests = hourlyInputs;
    }

    public HourlyRaoRequest getHourlyRequestFromResponse(HourlyRaoResult hourlyRaoResponse) {
        return this.getHourlyRaoRequests().stream().filter(request -> request.getInstant().equals(hourlyRaoResponse.getInstant()))
            .findFirst().orElseThrow(() -> new CoreCCInternalException(String.format("Rao response not found for timestamp %s .", hourlyRaoResponse.getInstant())));
    }

    public Set<HourlyRaoResult> getHourlyRaoResults() {
        return hourlyRaoResults;
    }

    public void setHourlyRaoResults(Set<HourlyRaoResult> hourlyArtifacts) {
        this.hourlyRaoResults = hourlyArtifacts;
    }

    public DailyOutputs getDailyOutputs() {
        return dailyOutputs;
    }

    public void setDailyOutputs(DailyOutputs dailyOutputs) {
        this.dailyOutputs = dailyOutputs;
    }

    public int getVersion() {
        return version;
    }

    public void setVersion(int version) {
        this.version = version;
    }

    public Status getStatus() {
        return status;
    }

    public void setStatus(Status status) {
        this.status = status;
    }

    public Instant getComputationEndInstant() {
        return computationEndInstant;
    }

    public void setComputationEndInstant(Instant computationEndInstant) {
        this.computationEndInstant = computationEndInstant;
    }

    public Instant getOutputsSentInstant() {
        return outputsSentInstant;
    }

    public void setOutputsSentInstant(Instant outputsSentInstant) {
        this.outputsSentInstant = outputsSentInstant;
    }

    public Instant getInputsReceivedInstant() {
        return inputsReceivedInstant;
    }

    public void setInputsReceivedInstant(Instant inputsReceivedInstant) {
        this.inputsReceivedInstant = inputsReceivedInstant;
    }

    public Instant getComputationStartInstant() {
        return computationStartInstant;
    }

    public void setComputationStartInstant(Instant computationStartInstant) {
        this.computationStartInstant = computationStartInstant;
    }

    public String getDestinationKey() {
        return ("RAO_WORKING_DIR" + "/" + getTimestamp()).replace(":", "");
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
