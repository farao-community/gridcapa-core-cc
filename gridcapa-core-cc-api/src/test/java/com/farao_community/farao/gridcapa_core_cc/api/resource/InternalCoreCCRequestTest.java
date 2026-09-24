/*
 * Copyright (c) 2023, RTE (http://www.rte-france.com)
 *  This Source Code Form is subject to the terms of the Mozilla Public
 *  License, v. 2.0. If a copy of the MPL was not distributed with this
 *  file, You can obtain one at http://mozilla.org/MPL/2.0/.
 *
 */

package com.farao_community.farao.gridcapa_core_cc.api.resource;

import com.farao_community.farao.gridcapa.task_manager.api.TaskParameterDto;
import com.farao_community.farao.minio_adapter.starter.MinioAdapter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * @author Thomas Bouquet {@literal <thomas.bouquet at rte-france.com>}
 */
class InternalCoreCCRequestTest {
    private InternalCoreCCRequest internalCoreCCRequest;
    private HourlyRaoRequest continentalHourlyRaoRequest;
    private HourlyRaoRequest semHourlyRaoRequest;
    private HourlyRaoResult continentalHourlyRaoResult;
    private HourlyRaoResult semHourlyRaoResult;

    private final OffsetDateTime timestamp = OffsetDateTime.of(2023, 7, 18, 13, 49, 50, 0, ZoneId.of("Europe/Brussels").getRules().getOffset(LocalDateTime.now()));
    private final TaskParameterDto taskParameterDto = new TaskParameterDto("paramId", "paramType", "paramValue", "paramDefaultValue");

    @BeforeEach
    void setUp() {
        final CoreCCRequest coreCCRequest = Mockito.mock(CoreCCRequest.class);
        Mockito.when(coreCCRequest.getId()).thenReturn("id");
        Mockito.when(coreCCRequest.getCurrentRunId()).thenReturn("runId");
        Mockito.when(coreCCRequest.getTimestamp()).thenReturn(timestamp);
        Mockito.when(coreCCRequest.getCgm()).thenReturn(new CoreCCFileResource("cgm", "file/cgm"));
        Mockito.when(coreCCRequest.getDcCgm()).thenReturn(new CoreCCFileResource("dcCgm", "file/dcCgm"));
        Mockito.when(coreCCRequest.getCbcora()).thenReturn(new CoreCCFileResource("cbcora", "file/cbcora"));
        Mockito.when(coreCCRequest.getGlsk()).thenReturn(new CoreCCFileResource("glsk", "file/glsk"));
        Mockito.when(coreCCRequest.getRefProg()).thenReturn(new CoreCCFileResource("refProg", "file/refProg"));
        Mockito.when(coreCCRequest.getRaoRequest()).thenReturn(new CoreCCFileResource("raoRequest", "file/raoRequest"));
        Mockito.when(coreCCRequest.getVirtualHub()).thenReturn(new CoreCCFileResource("virtualHub", "file/virtualHub"));
        Mockito.when(coreCCRequest.getTaskParameterList()).thenReturn(List.of(taskParameterDto));
        internalCoreCCRequest = new InternalCoreCCRequest(coreCCRequest);
        internalCoreCCRequest.setRaoParameters(new CoreCCFileResource("raoParameters", "file/raoParameters"));

        final MinioAdapter minioAdapter = Mockito.mock(MinioAdapter.class);
        Mockito.when(minioAdapter.generatePreSignedUrl(Mockito.any())).thenReturn("http://url");
        continentalHourlyRaoRequest = new HourlyRaoRequest(minioAdapter, "continentalInstant", "file/path/network", "file/path/cb", "file/path/refprog", "file/path/virtualHub", "file/path/glsk", "file/path/raoParameters", "path/to/continental/destination");
        semHourlyRaoRequest = new HourlyRaoRequest(minioAdapter, "semInstant", "file/path/network", "file/path/cb", "file/path/refprog", "file/path/virtualHub", "file/path/glsk", "file/path/raoParameters", "path/to/sem/destination");
        continentalHourlyRaoResult = new HourlyRaoResult("continentalInstant");
        semHourlyRaoResult = new HourlyRaoResult("semInstant");
    }

    private boolean equalsCoreCCFileResource(String filename, CoreCCFileResource actual) {
        return actual.getFilename().equals(filename) && actual.getUrl().equals("file/" + filename);
    }

    @Test
    void checkInternalCoreCCRequest() {
        assertEquals("id", internalCoreCCRequest.getId());
        assertEquals("runId", internalCoreCCRequest.getRunId());
        assertEquals(timestamp, internalCoreCCRequest.getTimestamp());
        assertTrue(equalsCoreCCFileResource("cgm", internalCoreCCRequest.getCgm()));
        assertTrue(equalsCoreCCFileResource("dcCgm", internalCoreCCRequest.getDcCgm()));
        assertTrue(equalsCoreCCFileResource("cbcora", internalCoreCCRequest.getCbcora()));
        assertTrue(equalsCoreCCFileResource("glsk", internalCoreCCRequest.getGlsk()));
        assertTrue(equalsCoreCCFileResource("refProg", internalCoreCCRequest.getRefProg()));
        assertTrue(equalsCoreCCFileResource("raoRequest", internalCoreCCRequest.getRaoRequest()));
        assertTrue(equalsCoreCCFileResource("virtualHub", internalCoreCCRequest.getVirtualHub()));
        assertTrue(internalCoreCCRequest.getParameters().contains(taskParameterDto));
        assertTrue(equalsCoreCCFileResource("raoParameters", internalCoreCCRequest.getRaoParameters()));
        assertNull(internalCoreCCRequest.getContinentalHourlyRaoRequest());
        assertNull(internalCoreCCRequest.getSemHourlyRaoRequest());
        assertNull(internalCoreCCRequest.getContinentalHourlyRaoResult());
        assertNull(internalCoreCCRequest.getSemHourlyRaoResult());
        assertEquals(1, internalCoreCCRequest.getVersion());
        assertNull(internalCoreCCRequest.getRequestReceivedInstant());
        assertNull(internalCoreCCRequest.getTimeInterval());
        assertNull(internalCoreCCRequest.getCorrelationId());
        assertNull(internalCoreCCRequest.getDestinationPath());
        assertFalse(internalCoreCCRequest.isSemEnabled());
    }

    @Test
    void changeContinentalHourlyRaoRequest() {
        internalCoreCCRequest.setContinentalHourlyRaoRequest(continentalHourlyRaoRequest);
        assertEquals(continentalHourlyRaoRequest, internalCoreCCRequest.getContinentalHourlyRaoRequest());
    }

    @Test
    void changeSemHourlyRaoRequest() {
        internalCoreCCRequest.setSemHourlyRaoRequest(semHourlyRaoRequest);
        assertEquals(semHourlyRaoRequest, internalCoreCCRequest.getSemHourlyRaoRequest());
    }

    @Test
    void changeContinentalHourlyRaoResult() {
        internalCoreCCRequest.setContinentalHourlyRaoResult(continentalHourlyRaoResult);
        assertEquals(continentalHourlyRaoResult, internalCoreCCRequest.getContinentalHourlyRaoResult());
    }

    @Test
    void changeSemHourlyRaoResult() {
        internalCoreCCRequest.setSemHourlyRaoResult(semHourlyRaoResult);
        assertEquals(semHourlyRaoResult, internalCoreCCRequest.getSemHourlyRaoResult());
    }

    @Test
    void changeVersion() {
        internalCoreCCRequest.setVersion(2);
        assertEquals(2, internalCoreCCRequest.getVersion());
    }

    @Test
    void changeRequestReceivedInstant() {
        final Instant instant = Instant.ofEpochSecond(0);
        assertNotEquals(instant, internalCoreCCRequest.getRequestReceivedInstant());
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

    @Test
    void changeDestinationPath() {
        internalCoreCCRequest.setDestinationPath("path/to/destination");
        assertEquals("path/to/destination", internalCoreCCRequest.getDestinationPath());
    }

    @Test
    void changeSemEnabled() {
        internalCoreCCRequest.setSemEnabled(true);
        assertTrue(internalCoreCCRequest.isSemEnabled());
    }
}
