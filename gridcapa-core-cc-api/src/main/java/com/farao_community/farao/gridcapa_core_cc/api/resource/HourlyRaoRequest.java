/*
 * Copyright (c) 2023, RTE (http://www.rte-france.com)
 *  This Source Code Form is subject to the terms of the Mozilla Public
 *  License, v. 2.0. If a copy of the MPL was not distributed with this
 *  file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.farao_community.farao.gridcapa_core_cc.api.resource;

import com.farao_community.farao.minio_adapter.starter.MinioAdapter;
import com.farao_community.farao.rao_runner.api.resource.RaoRequest;
import org.springframework.beans.factory.annotation.Value;

import java.time.Instant;
import java.util.Objects;

/**
 * @author Mohamed BenRejeb {@literal <mohamed.ben-rejeb at rte-france.com>}
 */
public class HourlyRaoRequest {
    private final MinioAdapter minioAdapter;
    private final String raoRequestInstant;
    private final String networkFileUrl;
    private final String cracFileUrl;
    private final String refprogFileUrl;
    private final String virtualHubsConfigurationFileUrl;
    private final String realGlskFileUrl;
    private final String raoParametersFileUrl;
    private final String resultsDestination;

    @Value("${core-cc-runner.async-time-out}")
    private long raoTimeOut;

    public HourlyRaoRequest(MinioAdapter minioAdapter,
                            String raoRequestInstant,
                            String networkFileUrl,
                            String cbFileUrl,
                            String refprogFileUrl,
                            String virtualHubsConfigurationFileUrl,
                            String realGlskFileUrl,
                            String raoParametersFileUrl,
                            String resultsDestination) {
        this.minioAdapter = minioAdapter;
        this.raoRequestInstant = raoRequestInstant;
        this.networkFileUrl = networkFileUrl;
        this.cracFileUrl = cbFileUrl;
        this.refprogFileUrl = refprogFileUrl;
        this.realGlskFileUrl = realGlskFileUrl;
        this.virtualHubsConfigurationFileUrl = virtualHubsConfigurationFileUrl;
        this.raoParametersFileUrl = raoParametersFileUrl;
        this.resultsDestination = resultsDestination;
    }

    public String getRaoRequestInstant() {
        return raoRequestInstant;
    }

    public String getNetworkFileUrl() {
        return networkFileUrl;
    }

    public String getCracFileUrl() {
        return cracFileUrl;
    }

    public String getRaoParametersFileUrl() {
        return raoParametersFileUrl;
    }

    public String getResultsDestination() {
        return resultsDestination;
    }

    public RaoRequest toRaoRequest(String id, String runId) {
        String resultsDestinationUrl = "CORE/CC/";
        return new RaoRequest.RaoRequestBuilder()
                .withId(id)
                .withRunId(runId)
                .withInstant(this.raoRequestInstant)
                .withNetworkFileUrl(minioAdapter.generatePreSignedUrl(this.networkFileUrl))
                .withCracFileUrl(minioAdapter.generatePreSignedUrl(this.cracFileUrl))
                .withRefprogFileUrl(this.refprogFileUrl)
                .withRealGlskFileUrl(this.realGlskFileUrl)
                .withRaoParametersFileUrl(minioAdapter.generatePreSignedUrl(this.raoParametersFileUrl))
                .withVirtualhubsFileUrl(this.virtualHubsConfigurationFileUrl)
                .withResultsDestination(resultsDestinationUrl + this.resultsDestination)
                .withTargetEndInstant(Instant.now().plusMillis(raoTimeOut))
                .build();
    }

    @Override
    public boolean equals(Object o) {

        if (o == this) {
            return true;
        }
        if (!(o instanceof HourlyRaoRequest)) {
            return false;
        }
        HourlyRaoRequest hourlyRaoRequest = (HourlyRaoRequest) o;
        if (hourlyRaoRequest.raoRequestInstant != null) {
            return hourlyRaoRequest.raoRequestInstant.equals(raoRequestInstant);
        } else {
            return false;
        }
    }

    @Override
    public int hashCode() {
        return Objects.hash(raoRequestInstant);
    }

}
