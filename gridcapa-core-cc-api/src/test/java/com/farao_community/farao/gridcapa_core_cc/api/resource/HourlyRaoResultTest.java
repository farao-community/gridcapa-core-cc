/*
 * Copyright (c) 2023, RTE (http://www.rte-france.com)
 *  This Source Code Form is subject to the terms of the Mozilla Public
 *  License, v. 2.0. If a copy of the MPL was not distributed with this
 *  file, You can obtain one at http://mozilla.org/MPL/2.0/.
 *
 */

package com.farao_community.farao.gridcapa_core_cc.api.resource;

import com.farao_community.farao.rao_runner.api.resource.RaoSuccessResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * @author Thomas Bouquet {@literal <thomas.bouquet at rte-france.com>}
 */
class HourlyRaoResultTest {

    private HourlyRaoResult hourlyRaoResult;
    private Instant initialInstant;
    private Instant oneSecondInstant;
    private RaoSuccessResponse raoResponse;

    @BeforeEach
    void setUp() {
        hourlyRaoResult = new HourlyRaoResult("2023-07-25T14:09:00Z");
        initialInstant = Instant.ofEpochSecond(0);
        oneSecondInstant = Instant.ofEpochSecond(1);
        raoResponse = Mockito.mock(RaoSuccessResponse.class);
        Mockito.when(raoResponse.getNetworkWithPraFileUrl()).thenReturn("network");
        Mockito.when(raoResponse.getRaoResultFileUrl()).thenReturn("raoResult");
        Mockito.when(raoResponse.getComputationStartInstant()).thenReturn(initialInstant);
        Mockito.when(raoResponse.getComputationEndInstant()).thenReturn(oneSecondInstant);
    }

    @Test
    void checkHourlyRaoResult() {
        assertEquals("2023-07-25T14:09:00Z", hourlyRaoResult.getRaoRequestInstant());
        assertNull(hourlyRaoResult.getNetworkWithPraUrl());
        assertNull(hourlyRaoResult.getErrorMessage());
        assertNull(hourlyRaoResult.getRaoResultFileUrl());
        assertEquals("99", hourlyRaoResult.getErrorCodeString());
        assertEquals(HourlyRaoResult.Status.PENDING, hourlyRaoResult.getStatus());
        assertEquals(initialInstant, hourlyRaoResult.getComputationStartInstant());
        assertEquals(initialInstant, hourlyRaoResult.getComputationEndInstant());
        assertEquals(2141528230, hourlyRaoResult.hashCode());
    }

    @Test
    void changeErrorCode() {
        hourlyRaoResult.setErrorCode(HourlyRaoResult.ErrorCode.RUNNING);
        assertEquals("0", hourlyRaoResult.getErrorCodeString());
        hourlyRaoResult.setErrorCode(HourlyRaoResult.ErrorCode.BD_PREPROCESSING_FAILURE);
        assertEquals("1", hourlyRaoResult.getErrorCodeString());
        hourlyRaoResult.setErrorCode(HourlyRaoResult.ErrorCode.TS_PREPROCESSING_FAILURE);
        assertEquals("2", hourlyRaoResult.getErrorCodeString());
        hourlyRaoResult.setErrorCode(HourlyRaoResult.ErrorCode.RAO_FAILURE);
        assertEquals("3", hourlyRaoResult.getErrorCodeString());
    }

    @Test
    void changeStatus() {
        assertEquals(HourlyRaoResult.Status.PENDING, hourlyRaoResult.getStatus());
        hourlyRaoResult.setStatus(HourlyRaoResult.Status.SUCCESS);
        assertEquals(HourlyRaoResult.Status.SUCCESS, hourlyRaoResult.getStatus());
        hourlyRaoResult.setStatus(HourlyRaoResult.Status.RUNNING);
        assertEquals(HourlyRaoResult.Status.RUNNING, hourlyRaoResult.getStatus());
        hourlyRaoResult.setStatus(HourlyRaoResult.Status.FAILURE);
        assertEquals(HourlyRaoResult.Status.FAILURE, hourlyRaoResult.getStatus());
    }

    @Test
    void changeErrorMessage() {
        hourlyRaoResult.setErrorMessage("This is an error message!");
        assertEquals("This is an error message!", hourlyRaoResult.getErrorMessage());
    }

    @Test
    void addRaoResponseData() {
        hourlyRaoResult.setRaoResponseData(raoResponse);
        assertEquals("network", hourlyRaoResult.getNetworkWithPraUrl());
        assertEquals("raoResult", hourlyRaoResult.getRaoResultFileUrl());
        assertEquals(initialInstant, hourlyRaoResult.getComputationStartInstant());
        assertEquals(oneSecondInstant, hourlyRaoResult.getComputationEndInstant());
    }

    @Test
    void testEquals() {
        HourlyRaoResult sameInstantHourlyRaoResult = new HourlyRaoResult("2023-07-25T14:09:00Z");
        HourlyRaoResult differentInstantHourlyRaoResult = new HourlyRaoResult("2023-07-25T15:09:00Z");
        assertEquals(hourlyRaoResult, hourlyRaoResult);
        assertEquals(hourlyRaoResult, sameInstantHourlyRaoResult);
        assertNotEquals(123, hourlyRaoResult);
        assertNotEquals(hourlyRaoResult, new HourlyRaoResult(null));
        assertNotEquals(hourlyRaoResult, differentInstantHourlyRaoResult);
    }
}
