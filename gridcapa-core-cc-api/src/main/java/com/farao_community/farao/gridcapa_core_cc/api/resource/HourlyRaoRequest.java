/*
 * Copyright (c) 2021, RTE (http://www.rte-france.com)
 */
package com.farao_community.farao.gridcapa_core_cc.api.resource;

import com.farao_community.farao.minio_adapter.starter.MinioAdapter;
import com.farao_community.farao.rao_runner.api.resource.RaoRequest;

import java.time.Instant;
import java.util.Objects;

/**
 * @author Mohamed BenRejeb {@literal <mohamed.ben-rejeb at rte-france.com>}
 */
public class HourlyRaoRequest {
    private MinioAdapter minioAdapter;
    private String instant;
    private String networkFileUrl;
    private String cracFileUrl;
    private String refprogFileUrl;
    private String realGlskFileUrl;
    private String raoParametersFileUrl;
    private String resultsDestination;
    private Instant targetEndInstant;

    public HourlyRaoRequest() {
    }

    public HourlyRaoRequest(MinioAdapter minioAdapter,
                            String instant,
                            String networkFileUrl,
                            String cbFileUrl,
                            String refprogFileUrl,
                            String realGlskFileUrl,
                            String raoParametersFileUrl,
                            String resultsDestination) {
        this(minioAdapter, instant, networkFileUrl, cbFileUrl, refprogFileUrl, realGlskFileUrl, raoParametersFileUrl, resultsDestination, null);
    }

    public HourlyRaoRequest(MinioAdapter minioAdapter,
                            String instant,
                            String networkFileUrl,
                            String cbFileUrl,
                            String refprogFileUrl,
                            String realGlskFileUrl,
                            String raoParametersFileUrl,
                            String resultsDestination,
                            Instant targetEndInstant) {
        this.minioAdapter = minioAdapter;
        this.instant = instant;
        this.networkFileUrl = networkFileUrl;
        this.cracFileUrl = cbFileUrl;
        this.refprogFileUrl = refprogFileUrl;
        this.realGlskFileUrl = realGlskFileUrl;
        this.raoParametersFileUrl = raoParametersFileUrl;
        this.resultsDestination = resultsDestination;
        this.targetEndInstant = targetEndInstant;
    }

    public String getInstant() {
        return instant;
    }

    public void setInstant(String instant) {
        this.instant = instant;
    }

    public String getNetworkFileUrl() {
        return networkFileUrl;
    }

    public void setNetworkFileUrl(String cgmFileUrl) {
        this.networkFileUrl = cgmFileUrl;
    }

    public String getRefprogFileUrl() {
        return refprogFileUrl;
    }

    public void setRefprogFileUrl(String refprogFileUrl) {
        this.refprogFileUrl = refprogFileUrl;
    }

    public String getRealGlskFileUrl() {
        return realGlskFileUrl;
    }

    public void setRealGlskFileUrl(String realGlskFileUrl) {
        this.realGlskFileUrl = realGlskFileUrl;
    }

    public String getCracFileUrl() {
        return cracFileUrl;
    }

    public void setCracFileUrl(String cbFileUrl) {
        this.cracFileUrl = cbFileUrl;
    }

    public String getRaoParametersFileUrl() {
        return raoParametersFileUrl;
    }

    public void setRaoParametersFileUrl(String raoParametersFileUrl) {
        this.raoParametersFileUrl = raoParametersFileUrl;
    }

    public String getResultsDestination() {
        return resultsDestination;
    }

    public void setResultsDestination(String resultsDestination) {
        this.resultsDestination = resultsDestination;
    }

    public RaoRequest toRaoRequest(String id) {
        return new RaoRequest(id, this.instant, minioAdapter.generatePreSignedUrl(this.networkFileUrl),  minioAdapter.generatePreSignedUrl(this.cracFileUrl), this.refprogFileUrl, this.realGlskFileUrl,  minioAdapter.generatePreSignedUrl(this.raoParametersFileUrl), "CORE/CC/" + this.resultsDestination, this.targetEndInstant);
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
        if (hourlyRaoRequest.instant != null) {
            return hourlyRaoRequest.instant.equals(instant);
        } else {
            return false;
        }
    }

    @Override
    public int hashCode() {
        return Objects.hash(instant);
    }

}
