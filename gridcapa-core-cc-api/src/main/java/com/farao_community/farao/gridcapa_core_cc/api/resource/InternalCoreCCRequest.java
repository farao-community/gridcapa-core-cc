/*
 * Copyright (c) 2023, RTE (http://www.rte-france.com)
 *  This Source Code Form is subject to the terms of the Mozilla Public
 *  License, v. 2.0. If a copy of the MPL was not distributed with this
 *  file, You can obtain one at http://mozilla.org/MPL/2.0/.
 *
 */

package com.farao_community.farao.gridcapa_core_cc.api.resource;

import org.apache.commons.lang3.builder.ToStringBuilder;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;

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

    public String getDestinationKey() {
        String hourlyFolderName = OffsetDateTime.parse(getTimestamp().toString()).format(DateTimeFormatter.ofPattern("yyyyMMdd'_'HHmm").withZone(ZoneId.of("Europe/Brussels")));
        return "RAO_WORKING_DIR" + "/" + handle25TimestampCase(hourlyFolderName, getTimestamp().toInstant().toString());
    }

    private static String handle25TimestampCase(String filename, String instant) {
        ZoneOffset previousOffset = OffsetDateTime.from(Instant.parse(instant).minus(1, ChronoUnit.HOURS).atZone(ZoneId.of("Europe/Brussels"))).getOffset();
        ZoneOffset currentOffset = OffsetDateTime.from(Instant.parse(instant).atZone(ZoneId.of("Europe/Brussels"))).getOffset();
        if (previousOffset == ZoneOffset.ofHours(2) && currentOffset == ZoneOffset.ofHours(1)) {
            return filename.replace("_0", "_B");
        } else {
            return filename;
        }
    }

    public String getAckDestinationKey() {
        String hourlyFolderName = OffsetDateTime.parse(getTimestamp().toString()).format(DateTimeFormatter.ofPattern("yyyy-MM-dd").withZone(ZoneId.of("Europe/Brussels")));
        return "RAO_OUTPUTS_DIR" + "/" + handle25TimestampCase(hourlyFolderName, getTimestamp().toInstant().toString());
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
