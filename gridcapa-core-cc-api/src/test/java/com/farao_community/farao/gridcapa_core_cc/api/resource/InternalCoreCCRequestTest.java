/*
 * Copyright (c) 2023, RTE (http://www.rte-france.com)
 *  This Source Code Form is subject to the terms of the Mozilla Public
 *  License, v. 2.0. If a copy of the MPL was not distributed with this
 *  file, You can obtain one at http://mozilla.org/MPL/2.0/.
 *
 */

package com.farao_community.farao.gridcapa_core_cc.api.resource;

import com.farao_community.farao.minio_adapter.starter.MinioAdapter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;

import static org.junit.jupiter.api.Assertions.*;

/**
 * @author Thomas Bouquet {@literal <thomas.bouquet at rte-france.com>}
 */
class InternalCoreCCRequestTest {
    private CoreCCRequest coreCCRequest;
    private InternalCoreCCRequest internalCoreCCRequest;
    private HourlyRaoRequest hourlyRaoRequest;
    private HourlyRaoResult hourlyRaoResult;
    private MinioAdapter minioAdapter;
    private Instant instant = Instant.ofEpochSecond(0);

    @BeforeEach
    void setUp() {
        coreCCRequest = Mockito.mock(CoreCCRequest.class);
        Mockito.when(coreCCRequest.getId()).thenReturn("id");
        Mockito.when(coreCCRequest.getTimestamp()).thenReturn(OffsetDateTime.of(2023, 7, 18, 13, 49, 50, 0, ZoneId.of("Europe/Brussels").getRules().getOffset(LocalDateTime.now())));
        Mockito.when(coreCCRequest.getCgm()).thenReturn(new CoreCCFileResource("cgm", "file/cgm"));
        Mockito.when(coreCCRequest.getCbcora()).thenReturn(new CoreCCFileResource("cbcora", "file/cbcora"));
        Mockito.when(coreCCRequest.getGlsk()).thenReturn(new CoreCCFileResource("glsk", "file/glsk"));
        Mockito.when(coreCCRequest.getRefProg()).thenReturn(new CoreCCFileResource("refProg", "file/refProg"));
        Mockito.when(coreCCRequest.getRaoRequest()).thenReturn(new CoreCCFileResource("raoRequest", "file/raoRequest"));
        Mockito.when(coreCCRequest.getVirtualHub()).thenReturn(new CoreCCFileResource("virtualHub", "file/virtualHub"));
        internalCoreCCRequest = new InternalCoreCCRequest(coreCCRequest);
        minioAdapter = Mockito.mock(MinioAdapter.class);
        Mockito.when(minioAdapter.generatePreSignedUrl(Mockito.any())).thenReturn("http://url");
        hourlyRaoRequest = new HourlyRaoRequest(minioAdapter, "instant", "file/path/network", "file/path/cb", "file/path/refprog", "file/path/virtualHub", "file/path/glsk", "file/path/raoParameters", "path/to/destination");
        hourlyRaoResult = new HourlyRaoResult("instant");
    }

    private boolean equalsCoreCCFileResource(CoreCCFileResource expected, CoreCCFileResource actual) {
        return (expected.getFilename().equals(actual.getFilename())) && (expected.getUrl().equals(actual.getUrl()));
    }

    private CoreCCFileResource createFileResource(String filename) {
        return new CoreCCFileResource(filename, "file/" + filename);
    }

    @Test
    void checkInternalCoreCCRequest() {
        assertTrue(equalsCoreCCFileResource(createFileResource("cgm"), internalCoreCCRequest.getCgm()));
        assertTrue(equalsCoreCCFileResource(createFileResource("cbcora"), internalCoreCCRequest.getCbcora()));
        assertTrue(equalsCoreCCFileResource(createFileResource("glsk"), internalCoreCCRequest.getGlsk()));
        assertTrue(equalsCoreCCFileResource(createFileResource("refProg"), internalCoreCCRequest.getRefProg()));
        assertTrue(equalsCoreCCFileResource(createFileResource("raoRequest"), internalCoreCCRequest.getRaoRequest()));
        assertTrue(equalsCoreCCFileResource(createFileResource("virtualHub"), internalCoreCCRequest.getVirtualHub()));
        assertNull(internalCoreCCRequest.getHourlyRaoRequest());
        assertNull(internalCoreCCRequest.getHourlyRaoResult());
        assertEquals(1, internalCoreCCRequest.getVersion());
        assertNull(internalCoreCCRequest.getRequestReceivedInstant());
        assertNull(internalCoreCCRequest.getTimeInterval());
        assertNull(internalCoreCCRequest.getCorrelationId());
    }

    @Test
    void changeHourlyRaoRequest() {
        internalCoreCCRequest.setHourlyRaoRequest(hourlyRaoRequest);
        assertEquals(hourlyRaoRequest, internalCoreCCRequest.getHourlyRaoRequest());
    }

    @Test
    void changeHourlyRaoResult() {
        internalCoreCCRequest.setHourlyRaoResult(hourlyRaoResult);
        assertEquals(hourlyRaoResult, internalCoreCCRequest.getHourlyRaoResult());
    }

    @Test
    void changeVersion() {
        internalCoreCCRequest.setVersion(2);
        assertEquals(2, internalCoreCCRequest.getVersion());
    }

    @Test
    void changeRequestReceivedInstant() {
        internalCoreCCRequest.setRequestReceivedInstant(instant);
        assertEquals(instant, internalCoreCCRequest.getRequestReceivedInstant());
    }

    @Test
    void changeTimeInterval() {
        internalCoreCCRequest.setTimeInterval("timeInterval");
        assertEquals("timeInterval", internalCoreCCRequest.getTimeInterval());
    }

    @Test
    void changeCorrelationId() {
        internalCoreCCRequest.setCorrelationId("correlationId");
        assertEquals("correlationId", internalCoreCCRequest.getCorrelationId());
    }
}
