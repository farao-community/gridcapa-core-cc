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
import com.github.jasminb.jsonapi.annotations.Id;
import com.github.jasminb.jsonapi.annotations.Type;
import org.apache.commons.lang3.builder.ToStringBuilder;

import java.time.Instant;

/**
 * @author Ameni Walha {@literal <ameni.walha at rte-france.com>}
 */
@Type("core-cc-response")
public class CoreCCResponse {

    @Id
    private final String id;
    private final String mainResultFileUrl;
    private final Instant computationStartInstant;
    private final Instant computationEndInstant;

    @JsonCreator
    public CoreCCResponse(@JsonProperty("id") String id, @JsonProperty("mainResultFileUrl") String mainResultFileUrl, @JsonProperty("computationStartInstant") Instant computationStartInstant, @JsonProperty("computationEndInstant") Instant computationEndInstant) {
        this.id = id;
        this.mainResultFileUrl = mainResultFileUrl;
        this.computationStartInstant = computationStartInstant;
        this.computationEndInstant = computationEndInstant;
    }

    public String getId() {
        return id;
    }

    public String getMainResultFileUrl() {
        return mainResultFileUrl;
    }

    public Instant getComputationStartInstant() {
        return computationStartInstant;
    }

    public Instant getComputationEndInstant() {
        return computationEndInstant;
    }

    @Override
    public String toString() {
        return ToStringBuilder.reflectionToString(this);
    }
}
