/*
 * Copyright (c) 2023, RTE (http://www.rte-france.com)
 *  This Source Code Form is subject to the terms of the Mozilla Public
 *  License, v. 2.0. If a copy of the MPL was not distributed with this
 *  file, You can obtain one at http://mozilla.org/MPL/2.0/.
 *
 */

package com.farao_community.farao.gridcapa_core_cc.api.resource;

import com.farao_community.farao.gridcapa.task_manager.api.TaskParameterDto;
import com.farao_community.farao.gridcapa_core_cc.api.OffsetDateTimeDeserializer;
import com.farao_community.farao.gridcapa_core_cc.api.OffsetDateTimeSerializer;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.github.jasminb.jsonapi.annotations.Id;
import com.github.jasminb.jsonapi.annotations.Type;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * @author Godelaine de Montmorillon {@literal <godelaine.demontmorillon at rte-france.com>}
 * @author Philippe Edwards {@literal <philippe.edwards at rte-france.com>}
 */
@Type("core-cc-request")
public class CoreCCRequest {
    @Id
    private final String id;

    private final String currentRunId;
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
    @JsonSerialize(using = OffsetDateTimeSerializer.class)
    @JsonDeserialize(using = OffsetDateTimeDeserializer.class)
    private final OffsetDateTime timestamp;
    private final CoreCCFileResource cgm;
    private final CoreCCFileResource dcCgm;
    private final CoreCCFileResource cbcora;
    private final CoreCCFileResource glsk;
    private final CoreCCFileResource refProg;
    private final CoreCCFileResource raoRequest;
    private final CoreCCFileResource virtualHub;
    private final boolean launchedAutomatically;
    private final List<TaskParameterDto> taskParameterList;

    @JsonCreator
    public CoreCCRequest(@JsonProperty("id") final String id,
                         @JsonProperty("currentRunId") final String currentRunId,
                         @JsonProperty("timestamp") final OffsetDateTime timestamp,
                         @JsonProperty("cgm") final CoreCCFileResource cgm,
                         @JsonProperty("dcgm") final CoreCCFileResource dcCgm,
                         @JsonProperty("cbcora") final CoreCCFileResource cbcora,
                         @JsonProperty("glsk") final CoreCCFileResource glsk,
                         @JsonProperty("refProg") final CoreCCFileResource refProg,
                         @JsonProperty("raoRequest") final CoreCCFileResource raoRequest,
                         @JsonProperty("virtualHub") final CoreCCFileResource virtualHub,
                         @JsonProperty("launchedAutomatically") final boolean launchedAutomatically,
                         @JsonProperty("taskParameterList") final List<TaskParameterDto> taskParameterList) {
        this.id = id;
        this.currentRunId = currentRunId;
        this.timestamp = timestamp;
        this.cgm = cgm;
        this.dcCgm = dcCgm;
        this.cbcora = cbcora;
        this.glsk = glsk;
        this.refProg = refProg;
        this.raoRequest = raoRequest;
        this.virtualHub = virtualHub;
        this.launchedAutomatically = launchedAutomatically;
        this.taskParameterList = taskParameterList;
    }

    public CoreCCRequest(final String id,
                         final String currentRunId,
                         final OffsetDateTime timestamp,
                         final CoreCCFileResource cgm,
                         final CoreCCFileResource dcCgm,
                         final CoreCCFileResource cbcora,
                         final CoreCCFileResource glsk,
                         final CoreCCFileResource refProg,
                         final CoreCCFileResource raoRequest,
                         final CoreCCFileResource virtualHub,
                         final List<TaskParameterDto> taskParameterList) {
        this(id, currentRunId, timestamp, cgm, dcCgm, cbcora, glsk, refProg, raoRequest, virtualHub, false, taskParameterList);
    }

    public String getId() {
        return id;
    }

    public String getCurrentRunId() {
        return currentRunId;
    }

    public OffsetDateTime getTimestamp() {
        return timestamp;
    }

    public CoreCCFileResource getCgm() {
        return cgm;
    }

    public CoreCCFileResource getDcCgm() {
        return dcCgm;
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
        return raoRequest;
    }

    public CoreCCFileResource getVirtualHub() {
        return virtualHub;
    }

    public boolean getLaunchedAutomatically() {
        return launchedAutomatically;
    }

    public List<TaskParameterDto> getTaskParameterList() {
        return taskParameterList;
    }

    @Override
    public String toString() {
        return ToStringBuilder.reflectionToString(this);
    }
}
