/*
 * Copyright (c) 2021, RTE (http://www.rte-france.com)
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
    private String resultsDestinationUrl = "CORE/CC/";
    private MinioAdapter minioAdapter;
    private String raoRequestInstant;
    private String networkFileUrl;
    private String cracFileUrl;
    private String refprogFileUrl;
    private String realGlskFileUrl;
    private String raoParametersFileUrl;
    private String resultsDestination;

    @Value("${core-cc-runner.async-time-out}")
    private long raoTimeOut;

    public HourlyRaoRequest(MinioAdapter minioAdapter,
                            String raoRequestInstant,
                            String networkFileUrl,
                            String cbFileUrl,
                            String refprogFileUrl,
                            String realGlskFileUrl,
                            String raoParametersFileUrl,
                            String resultsDestination) {
        this.minioAdapter = minioAdapter;
        this.raoRequestInstant = raoRequestInstant;
        this.networkFileUrl = networkFileUrl;
        this.cracFileUrl = cbFileUrl;
        this.refprogFileUrl = refprogFileUrl;
        this.realGlskFileUrl = realGlskFileUrl;
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

    public RaoRequest toRaoRequest(String id) {
        return new RaoRequest(id, this.raoRequestInstant, minioAdapter.generatePreSignedUrl(this.networkFileUrl),  minioAdapter.generatePreSignedUrl(this.cracFileUrl), this.refprogFileUrl, this.realGlskFileUrl,  minioAdapter.generatePreSignedUrl(this.raoParametersFileUrl), resultsDestinationUrl + this.resultsDestination, Instant.now().plusMillis(raoTimeOut));
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
