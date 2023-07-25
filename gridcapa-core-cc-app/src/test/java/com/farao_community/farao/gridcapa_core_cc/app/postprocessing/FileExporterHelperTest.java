/*
 * Copyright (c) 2023, RTE (http://www.rte-france.com)
 *  This Source Code Form is subject to the terms of the Mozilla Public
 *  License, v. 2.0. If a copy of the MPL was not distributed with this
 *  file, You can obtain one at http://mozilla.org/MPL/2.0/.
 *
 */

package com.farao_community.farao.gridcapa_core_cc.app.postprocessing;

import com.farao_community.farao.gridcapa_core_cc.api.resource.HourlyRaoRequest;
import com.farao_community.farao.gridcapa_core_cc.api.resource.HourlyRaoResult;
import com.farao_community.farao.gridcapa_core_cc.api.resource.InternalCoreCCRequest;
import com.farao_community.farao.gridcapa_core_cc.app.services.FileImporter;
import com.farao_community.farao.minio_adapter.starter.MinioAdapter;
import com.powsybl.iidm.network.Network;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.*;

/**
 * @author Thomas Bouquet {@literal <thomas.bouquet at rte-france.com>}
 */
public class FileExporterHelperTest {

    private String newtorkFile = "/util/TestCase12NodesHvdc.uct";
    private Path networkFilePath = Paths.get(getClass().getResource(newtorkFile).getPath());
    private Network network = Network.read(networkFilePath);
    private FileImporter fileImporter;
    private MinioAdapter minioAdapter;
    private InternalCoreCCRequest coreCCRequest;
    private HourlyRaoResult hourlyRaoResult;
    private HourlyRaoRequest hourlyRaoRequest;
    private Boolean networkIsUploaded;
    private Boolean raoResultIsUploaded;

    public FileExporterHelperTest() {
    }

    @BeforeEach
    void setUp() {
        networkIsUploaded = false;
        raoResultIsUploaded = false;
        fileImporter = Mockito.mock(FileImporter.class);
        Mockito.when(fileImporter.importNetworkFromUrl(Mockito.any())).thenReturn(network);
        minioAdapter = Mockito.mock(MinioAdapter.class);
        hourlyRaoResult = Mockito.mock(HourlyRaoResult.class);
        Mockito.when(hourlyRaoResult.getRaoRequestInstant()).thenReturn("2023-07-25T15:02:00Z");
        Mockito.when(hourlyRaoResult.getNetworkWithPraUrl()).thenReturn(newtorkFile);
        hourlyRaoRequest = Mockito.mock(HourlyRaoRequest.class);
        Mockito.when(hourlyRaoRequest.getResultsDestination()).thenReturn("/path");
        coreCCRequest = Mockito.mock(InternalCoreCCRequest.class);
        Mockito.when(coreCCRequest.getId()).thenReturn("id");
        Mockito.when(coreCCRequest.getHourlyRaoRequest()).thenReturn(hourlyRaoRequest);
        Mockito.when(coreCCRequest.getHourlyRaoResult()).thenReturn(hourlyRaoResult);
        Mockito.when(coreCCRequest.getVersion()).thenReturn(1);
    }

    @Test
    void exportNetworkToMinio() throws IOException {
        Mockito.doAnswer(answer -> networkIsUploaded = true).when(minioAdapter).uploadOutputForTimestamp(Mockito.anyString(), Mockito.any(), Mockito.any(), Mockito.any(), Mockito.any());
        FileExporterHelper fileExporterHelper = new FileExporterHelper(minioAdapter, fileImporter);
        fileExporterHelper.exportNetworkToMinio(coreCCRequest);
        assertTrue(networkIsUploaded);
    }

    @Test
    void errorWhenUploadingNetworkToMinio() {
        Mockito.when(coreCCRequest.getTimestamp()).thenThrow(new RuntimeException("Timestamp could not be retrieved."));
        FileExporterHelper fileExporterHelper = new FileExporterHelper(minioAdapter, fileImporter);
        RuntimeException exception = assertThrows(RuntimeException.class, () -> fileExporterHelper.exportNetworkToMinio(coreCCRequest));
        assertEquals("Timestamp could not be retrieved.", exception.getMessage());
        assertFalse(networkIsUploaded);
    }

    @Test
    void exportRaoResultToMinio() {
        Mockito.doAnswer(answer -> raoResultIsUploaded = true).when(minioAdapter).uploadOutputForTimestamp(Mockito.anyString(), Mockito.any(), Mockito.any(), Mockito.any(), Mockito.any());
        FileExporterHelper fileExporterHelper = new FileExporterHelper(minioAdapter, fileImporter);
        fileExporterHelper.exportRaoResultToMinio(coreCCRequest);
        assertTrue(raoResultIsUploaded);
    }
}
