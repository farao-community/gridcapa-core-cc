/*
 * Copyright (c) 2023, RTE (http://www.rte-france.com)
 *  This Source Code Form is subject to the terms of the Mozilla Public
 *  License, v. 2.0. If a copy of the MPL was not distributed with this
 *  file, You can obtain one at http://mozilla.org/MPL/2.0/.
 *
 */

package com.farao_community.farao.gridcapa_core_cc.api.resource;

import com.farao_community.farao.gridcapa_core_cc.api.OffsetDateTimeDeserializer;
import com.farao_community.farao.gridcapa_core_cc.api.OffsetDateTimeSerializer;
import com.farao_community.farao.gridcapa_core_cc.api.exception.CoreCCInternalException;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.github.jasminb.jsonapi.annotations.Id;
import com.github.jasminb.jsonapi.annotations.Type;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.HashSet;
import java.util.Set;

/**
 * @author Godelaine de Montmorillon {@literal <godelaine.demontmorillon at rte-france.com>}
 * @author Philippe Edwards {@literal <philippe.edwards at rte-france.com>}
 */
@Type("core-cc-request")
public class CoreCCRequest {
    @Id
    private final String id;

    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
    @JsonSerialize(using = OffsetDateTimeSerializer.class)
    @JsonDeserialize(using = OffsetDateTimeDeserializer.class)
    private final OffsetDateTime timestamp;
    private final CoreCCFileResource cgm;
    private final CoreCCFileResource cbcora;
    private final CoreCCFileResource glsk;
    private final CoreCCFileResource refProg;
    private final CoreCCFileResource raorequest;
    private final CoreCCFileResource virtualhub;
    private final boolean launchedAutomatically;

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

    public enum Status {
        PENDING,
        RUNNING,
        SUCCESS,
        FAILURE
    }

    @JsonCreator
    public CoreCCRequest(@JsonProperty("id") String id,
                         @JsonProperty("timestamp") OffsetDateTime timestamp,
                         @JsonProperty("cgm") CoreCCFileResource cgm,
                         @JsonProperty("cbcora") CoreCCFileResource cbcora,
                         @JsonProperty("glsk") CoreCCFileResource glsk,
                         @JsonProperty("refProg") CoreCCFileResource refProg,
                         @JsonProperty("raorequest") CoreCCFileResource raorequest,
                         @JsonProperty("virtualhub") CoreCCFileResource virtualhub,
                         @JsonProperty("launchedAutomatically") boolean launchedAutomatically) {
        this.id = id;
        this.timestamp = timestamp;
        this.cgm = cgm;
        this.cbcora = cbcora;
        this.glsk = glsk;
        this.refProg = refProg;
        this.raorequest = raorequest;
        this.virtualhub = virtualhub;
        this.launchedAutomatically = launchedAutomatically;
    }

    public CoreCCRequest(String id,
                         OffsetDateTime timestamp,
                         CoreCCFileResource cgm,
                         CoreCCFileResource cbcora,
                         CoreCCFileResource glsk,
                         CoreCCFileResource refProg,
                         CoreCCFileResource raorequest,
                         CoreCCFileResource virtualhub) {
        this(id, timestamp, cgm, cbcora, glsk, refProg, raorequest, virtualhub, false);
    }

    public String getId() {
        return id;
    }

    public OffsetDateTime getTimestamp() {
        return timestamp;
    }

    public CoreCCFileResource getCgm() {
        return cgm;
    }

    public CoreCCFileResource getCbcora() {
        return cbcora;
    }

    public CoreCCFileResource getGlsk() {
        return glsk;
    }

    public CoreCCFileResource getRefProg() {
        return refProg;
    }

    public CoreCCFileResource getRaoRequest() {
        return raorequest;
    }

    public CoreCCFileResource getVirtualHub() {
        return virtualhub;
    }

    public boolean getLaunchedAutomatically() {
        return launchedAutomatically;
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
        return "RAO_WORKING_DIR" + "/" + getTimestamp();
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
