/*
 * Copyright (c) 2023, RTE (http://www.rte-france.com)
 *  This Source Code Form is subject to the terms of the Mozilla Public
 *  License, v. 2.0. If a copy of the MPL was not distributed with this
 *  file, You can obtain one at http://mozilla.org/MPL/2.0/.
 *
 */

package com.farao_community.farao.gridcapa_core_cc.api.resource;

import com.farao_community.farao.minio_adapter.starter.MinioAdapter;
import com.farao_community.farao.rao_runner.api.resource.RaoRequest;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

/**
 * @author Thomas Bouquet {@literal <thomas.bouquet at rte-france.com>}
 */
class HourlyRaoRequestTest {

    private MinioAdapter minioAdapter;
    private HourlyRaoRequest hourlyRaoRequest;

    @BeforeEach
    void setUp() {
        minioAdapter = Mockito.mock(MinioAdapter.class);
        Mockito.when(minioAdapter.generatePreSignedUrl("file/path/network")).thenReturn("http://network/");
        Mockito.when(minioAdapter.generatePreSignedUrl("file/path/cb")).thenReturn("http://crac/");
        Mockito.when(minioAdapter.generatePreSignedUrl("file/path/raoParameters")).thenReturn("http://raoParameters/");
        hourlyRaoRequest = new HourlyRaoRequest(
            minioAdapter,
            "2023-07-25T14:13:00Z",
            "file/path/network",
            "file/path/cb",
            "file/path/refprog",
            "file/path/virtualHub",
            "file/path/glsk",
            "file/path/raoParameters",
            "path/to/destination"
        );
    }

    @Test
    void checkHourlyRaoRequestGetters() {
        Assertions.assertThat(hourlyRaoRequest.getRaoRequestInstant()).isEqualTo("2023-07-25T14:13:00Z");
        Assertions.assertThat(hourlyRaoRequest.getNetworkFileUrl()).isEqualTo("file/path/network");
        Assertions.assertThat(hourlyRaoRequest.getCracFileUrl()).isEqualTo("file/path/cb");
        Assertions.assertThat(hourlyRaoRequest.getRaoParametersFileUrl()).isEqualTo("file/path/raoParameters");
        Assertions.assertThat(hourlyRaoRequest.getResultsDestination()).isEqualTo("path/to/destination");
        Assertions.assertThat(hourlyRaoRequest.hashCode()).isEqualTo(-2130351041);
    }

    @Test
    void checkHourlyRaoRequestToRaoRequestConverter() {
        final RaoRequest raoRequest = hourlyRaoRequest.toRaoRequest("id", "runId", "prefix");
        Assertions.assertThat(raoRequest.getId()).isEqualTo("id");
        Assertions.assertThat(raoRequest.getRunId()).isEqualTo("runId");
        Assertions.assertThat(raoRequest.getInstant()).contains("2023-07-25T14:13:00Z");
        Assertions.assertThat(raoRequest.getNetworkFileUrl()).isEqualTo("http://network/");
        Assertions.assertThat(raoRequest.getCracFileUrl()).isEqualTo("http://crac/");
        Assertions.assertThat(raoRequest.getRefprogFileUrl()).contains("file/path/refprog");
        Assertions.assertThat(raoRequest.getVirtualhubsFileUrl()).contains("file/path/virtualHub");
        Assertions.assertThat(raoRequest.getRealGlskFileUrl()).contains("file/path/glsk");
        Assertions.assertThat(raoRequest.getRaoParametersFileUrl()).isEqualTo("http://raoParameters/");
        Assertions.assertThat(raoRequest.getResultsDestination()).contains("CORE/CC/path/to/destination");
        Assertions.assertThat(raoRequest.getTargetEndInstant()).isNotNull();
        Assertions.assertThat(raoRequest.getEventPrefix()).contains("prefix");
    }

    @Test
    void testEquals() {
        final HourlyRaoRequest sameInstantHourlyRaoRequest = new HourlyRaoRequest(
            minioAdapter,
            "2023-07-25T14:13:00Z",
            "file/path/network",
            "file/path/cb",
            "file/path/refprog",
            "file/path/virtualHub",
            "file/path/glsk",
            "file/path/raoParameters",
            "path/to/destination");
        final HourlyRaoRequest differentInstantHourlyRaoRequest = new HourlyRaoRequest(
            minioAdapter, "2023-07-25T15:13:00Z",
            "file/path/network",
            "file/path/cb",
            "file/path/refprog",
            "file/path/virtualHub",
            "file/path/glsk",
            "file/path/raoParameters",
            "path/to/destination");

        Assertions.assertThat(hourlyRaoRequest.equals(hourlyRaoRequest)).isTrue(); // Explicit call to equals function because using "isEqualTo" checks equality with == beforehand
        Assertions.assertThat(sameInstantHourlyRaoRequest).isEqualTo(hourlyRaoRequest);
        final HourlyRaoRequest nullRaoRequest = null;
        Assertions.assertThat(hourlyRaoRequest)
            .isNotEqualTo(differentInstantHourlyRaoRequest)
            .isNotEqualTo(0)
            .isNotEqualTo(nullRaoRequest);
    }

}
