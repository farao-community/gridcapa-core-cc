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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

/**
 * @author Thomas Bouquet {@literal <thomas.bouquet at rte-france.com>}
 */
class HourlyRaoRequestTest {

    private MinioAdapter minioAdapter;
    private HourlyRaoRequest hourlyRaoRequest;

    @BeforeEach
    void setUp() {
        minioAdapter = Mockito.mock(MinioAdapter.class);
        Mockito.when(minioAdapter.generatePreSignedUrl(Mockito.any())).thenReturn("http://url");
        hourlyRaoRequest = new HourlyRaoRequest(minioAdapter, "2023-07-25T14:13:00Z", "file/path/network", "file/path/cb", "file/path/refprog", "file/path/virtualHub", "file/path/glsk", "file/path/raoParameters", "path/to/destination");
    }

    @Test
    void checkHourlyRaoRequest() {
        assertEquals("2023-07-25T14:13:00Z", hourlyRaoRequest.getRaoRequestInstant());
        assertEquals("file/path/network", hourlyRaoRequest.getNetworkFileUrl());
        assertEquals("http://url", hourlyRaoRequest.toRaoRequest("id", "runId").getCracFileUrl());
        assertEquals("http://url", hourlyRaoRequest.toRaoRequest("id", "runId").getNetworkFileUrl());
        assertEquals(-2130351041, hourlyRaoRequest.hashCode());
    }

    @Test
    void testEquals() {
        HourlyRaoRequest sameInstantHourlyRaoRequest = new HourlyRaoRequest(minioAdapter, "2023-07-25T14:13:00Z", "file/path/network", "file/path/cb", "file/path/refprog", "file/path/virtualHub", "file/path/glsk", "file/path/raoParameters", "path/to/destination");
        HourlyRaoRequest differentInstantHourlyRaoRequest = new HourlyRaoRequest(minioAdapter, "2023-07-25T15:13:00Z", "file/path/network", "file/path/cb", "file/path/refprog", "file/path/virtualHub", "file/path/glsk", "file/path/raoParameters", "path/to/destination");
        assertEquals(hourlyRaoRequest, hourlyRaoRequest);
        assertEquals(hourlyRaoRequest, sameInstantHourlyRaoRequest);
        assertNotEquals(differentInstantHourlyRaoRequest, hourlyRaoRequest);
        assertNotEquals(0, hourlyRaoRequest);
    }

}
