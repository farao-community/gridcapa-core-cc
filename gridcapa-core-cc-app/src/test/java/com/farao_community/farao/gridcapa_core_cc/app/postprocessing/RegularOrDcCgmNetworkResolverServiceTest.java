/*
 * Copyright (c) 2024, RTE (http://www.rte-france.com)
 *  This Source Code Form is subject to the terms of the Mozilla Public
 *  License, v. 2.0. If a copy of the MPL was not distributed with this
 *  file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package com.farao_community.farao.gridcapa_core_cc.app.postprocessing;

import com.farao_community.farao.gridcapa_core_cc.api.resource.HourlyRaoRequest;
import com.farao_community.farao.gridcapa_core_cc.api.resource.HourlyRaoResult;
import com.farao_community.farao.gridcapa_core_cc.api.resource.InternalCoreCCRequest;
import com.farao_community.farao.gridcapa_core_cc.app.entities.CgmsAndXmlHeader;
import com.farao_community.farao.gridcapa_core_cc.app.services.FileImporter;
import com.farao_community.farao.minio_adapter.starter.MinioAdapter;
import com.powsybl.iidm.network.Network;
import com.powsybl.openrao.data.cracapi.Crac;
import com.powsybl.openrao.data.cracioapi.CracImporters;
import com.powsybl.openrao.data.raoresultapi.RaoResult;
import com.powsybl.openrao.data.raoresultjson.RaoResultImporter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * @author Daniel Thirion {@literal <daniel.thirion at rte-france.com>}
 */
@ExtendWith(MockitoExtension.class)
class RegularOrDcCgmNetworkResolverServiceTest {

    @Mock
    private FileImporter fileImporter;

    @Mock
    private MinioAdapter minioAdapter;

    @InjectMocks
    private RegularOrDcCgmNetworkResolver regularOrDcCgmNetworkResolver;

    private final String networkFile = "/util/TestCase12NodesHvdc.uct";
    private final Path networkPath = Paths.get(getClass().getResource(networkFile).getPath());
    private final Network network = Network.read(networkPath);

    private InternalCoreCCRequest coreCCRequest;
    private HourlyRaoRequest hourlyRaoRequest;
    private HourlyRaoResult hourlyRaoResult;

    @BeforeEach
    void setUp() {
        coreCCRequest = mock(InternalCoreCCRequest.class);
        hourlyRaoResult = mock(HourlyRaoResult.class);
        hourlyRaoRequest = mock(HourlyRaoRequest.class);
        cgmsAndXmlHeader = mock(CgmsAndXmlHeader.class);
        when(coreCCRequest.getHourlyRaoResult()).thenReturn(hourlyRaoResult);
    }

    private CgmsAndXmlHeader cgmsAndXmlHeader;

    @Test
    void integrationTestWithDcCgmInput() throws IOException {
        final String cracJsonFileName = "/util/crac.json";
        final Path cracJsonFilePath = Paths.get(getClass().getResource(cracJsonFileName).getPath());
        final String raoResultFileName = "/util/raoResult.json";
        final Path raoResultFilePath = Paths.get(getClass().getResource(raoResultFileName).getPath());
        final Crac crac = CracImporters.importCrac(cracJsonFilePath.getFileName().toString(), Files.newInputStream(cracJsonFilePath), network);
        final RaoResult raoResult = (new RaoResultImporter()).importRaoResult(Files.newInputStream(raoResultFilePath), crac);
        //mock cgms
        when(coreCCRequest.getHourlyRaoRequest()).thenReturn(hourlyRaoRequest);
        when(fileImporter.importCgmsZip(any())).thenReturn(cgmsAndXmlHeader);
        //mock cgm path
        when(cgmsAndXmlHeader.getNetworkPath(any(Instant.class))).thenReturn(networkPath);
        //mock crac
        when(hourlyRaoResult.getRaoRequestInstant()).thenReturn("2023-05-31T00:00:00Z");
        when(hourlyRaoRequest.getCracFileUrl()).thenReturn(cracJsonFilePath.toString());

        //
        when(fileImporter.importRaoResult(null, crac)).thenReturn(raoResult);
        try (final MockedStatic<CracImporters> mockedStatic = mockStatic(CracImporters.class)) {
            mockedStatic.when(() -> CracImporters.importCrac(any(), any(), any())).thenReturn(crac);
            final Network result = regularOrDcCgmNetworkResolver.resolve(true, coreCCRequest);

            assertNotNull(result);
            //Corresponding TWT must have its current tap position switch from 0 to -6
            assertEquals(0, network.getTwoWindingsTransformer("BBE2AA1  BBE3AA1  1").getPhaseTapChanger().getTapPosition());
            assertEquals(-6, result.getTwoWindingsTransformer("BBE2AA1  BBE3AA1  1").getPhaseTapChanger().getTapPosition());
            verify(fileImporter, never()).importNetworkFromUrl(any());
        }
    }

    @Test
    void testResolveWithUseDcCGmInputFalse() {
        when(fileImporter.importNetworkFromUrl(any())).thenReturn(network);

        final Network result = regularOrDcCgmNetworkResolver.resolve(false, coreCCRequest);

        assertNotNull(result);
        verify(fileImporter).importNetworkFromUrl(any());
        verify(fileImporter, never()).importCgmsZip(any());
        verify(fileImporter, never()).importCrac(any(), any(), eq(network));
    }
}
