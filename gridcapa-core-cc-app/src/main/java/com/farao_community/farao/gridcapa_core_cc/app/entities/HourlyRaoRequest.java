/*
 * Copyright (c) 2021, RTE (http://www.rte-france.com)
 */
package com.farao_community.farao.gridcapa_core_cc.app.entities;

import com.rte_france.farao.rao_runner.api.resource.RaoRequest;

import javax.persistence.Column;
import javax.persistence.Embeddable;
import java.time.Instant;
import java.util.Objects;

/**
 * @author Mohamed BenRejeb {@literal <mohamed.ben-rejeb at rte-france.com>}
 */
@Embeddable
public class HourlyRaoRequest {

    private String instant;

    @Column(columnDefinition = "TEXT")
    private String networkFileUrl;

    @Column(columnDefinition = "TEXT")
    private String cracFileUrl;

    @Column(columnDefinition = "TEXT")
    private String refprogFileUrl;

    @Column(columnDefinition = "TEXT")
    private String realGlskFileUrl;

    @Column(columnDefinition = "TEXT")
    private String raoParametersFileUrl;

    private String resultsDestination;

    private Instant targetEndInstant;

    public HourlyRaoRequest() {
    }

    public HourlyRaoRequest(String instant,
                            String networkFileUrl,
                            String cbFileUrl,
                            String refprogFileUrl,
                            String realGlskFileUrl,
                            String raoParametersFileUrl,
                            String resultsDestination) {
        this(instant, networkFileUrl, cbFileUrl, refprogFileUrl, realGlskFileUrl, raoParametersFileUrl, resultsDestination, null);
    }

    public HourlyRaoRequest(String instant,
                            String networkFileUrl,
                            String cbFileUrl,
                            String refprogFileUrl,
                            String realGlskFileUrl,
                            String raoParametersFileUrl,
                            String resultsDestination,
                            Instant targetEndInstant) {
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
        return new RaoRequest(id + "_" + this.instant, this.instant, this.networkFileUrl, this.cracFileUrl, this.refprogFileUrl, this.realGlskFileUrl, this.raoParametersFileUrl, this.resultsDestination, this.targetEndInstant);
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
