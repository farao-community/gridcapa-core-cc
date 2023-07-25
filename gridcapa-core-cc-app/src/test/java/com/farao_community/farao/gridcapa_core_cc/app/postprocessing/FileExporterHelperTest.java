/*
 * Copyright (c) 2023, RTE (http://www.rte-france.com)
 *  This Source Code Form is subject to the terms of the Mozilla Public
 *  License, v. 2.0. If a copy of the MPL was not distributed with this
 *  file, You can obtain one at http://mozilla.org/MPL/2.0/.
 *
 */

package com.farao_community.farao.gridcapa_core_cc.app.postprocessing;

import com.farao_community.farao.gridcapa_core_cc.api.resource.CoreCCFileResource;
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
import java.time.Instant;

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
    private Boolean metadataIsUploaded;

    public FileExporterHelperTest() {
    }

    @BeforeEach
    void setUp() {
        networkIsUploaded = false;
        raoResultIsUploaded = false;
        metadataIsUploaded = false;
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
        String networkFileName = OutputFileNameUtil.generateUctFileName(hourlyRaoResult.getRaoRequestInstant(), coreCCRequest.getVersion());
        String networkFilePath = coreCCRequest.getHourlyRaoRequest().getResultsDestination() + "/" + networkFileName;
        assertEquals("20230725_1730_2D2_UX1.uct", networkFileName);
        assertEquals("/path/20230725_1730_2D2_UX1.uct", networkFilePath);
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
        String raoResultFileName = OutputFileNameUtil.generateRaoResultFileName(hourlyRaoResult.getRaoRequestInstant(), coreCCRequest);
        String raoResultFilePath = hourlyRaoRequest.getResultsDestination() + "/" + raoResultFileName;
        assertEquals("20230725_1730_RAO-RESULT-0V.txt", raoResultFileName);
        assertEquals("/path/20230725_1730_RAO-RESULT-0V.txt", raoResultFilePath);
    }

    @Test
    void errorWhenUploadingRaoResultToMinio() throws IOException {
        Mockito.when(coreCCRequest.getTimestamp()).thenThrow(new RuntimeException("Timestamp could not be retrieved."));
        FileExporterHelper fileExporterHelper = new FileExporterHelper(minioAdapter, fileImporter);
        RuntimeException exception = assertThrows(RuntimeException.class, () -> fileExporterHelper.exportRaoResultToMinio(coreCCRequest));
        assertEquals("Timestamp could not be retrieved.", exception.getMessage());
        assertFalse(raoResultIsUploaded);
    }

    @Test
    void exportMetadataToMinio() throws IOException {
        Mockito.doAnswer(answer -> metadataIsUploaded = true).when(minioAdapter).uploadOutputForTimestamp(Mockito.anyString(), Mockito.any(), Mockito.any(), Mockito.any(), Mockito.any());
        CoreCCFileResource raoRequest = Mockito.mock(CoreCCFileResource.class);
        Mockito.when(raoRequest.getFilename()).thenReturn("raoRequest.json");
        String instantString = "2023-07-25T16:57:00Z";
        Instant instant = Instant.parse(instantString);
        Mockito.when(coreCCRequest.getRaoRequest()).thenReturn(raoRequest);
        Mockito.when(coreCCRequest.getRequestReceivedInstant()).thenReturn(instant);
        Mockito.when(coreCCRequest.getTimeInterval()).thenReturn("interval");
        Mockito.when(coreCCRequest.getCorrelationId()).thenReturn("id");
        Mockito.when(hourlyRaoResult.getRaoRequestInstant()).thenReturn(instantString);
        Mockito.when(hourlyRaoResult.getComputationStartInstant()).thenReturn(instant);
        Mockito.when(hourlyRaoResult.getComputationEndInstant()).thenReturn(instant);
        Mockito.when(hourlyRaoResult.getStatus()).thenReturn(HourlyRaoResult.Status.RUNNING);
        Mockito.when(hourlyRaoResult.getErrorCodeString()).thenReturn("Error code");
        Mockito.when(hourlyRaoResult.getErrorMessage()).thenReturn("Error message.");
        FileExporterHelper fileExporterHelper = new FileExporterHelper(minioAdapter, fileImporter);
        fileExporterHelper.exportMetadataToMinio(coreCCRequest);
        assertTrue(metadataIsUploaded);
        String metaDataFileName = OutputFileNameUtil.generateMetadataFileName(hourlyRaoResult.getRaoRequestInstant(), coreCCRequest);
        String metaDataFilePath = hourlyRaoRequest.getResultsDestination() + "/" + metaDataFileName;
        assertEquals("20230725_1830_METADATA-01.json", metaDataFileName);
        assertEquals("/path/20230725_1830_METADATA-01.json", metaDataFilePath);
    }

    @Test
    void errorWhenUploadingMetadataToMinio() throws IOException {
        FileExporterHelper fileExporterHelper = new FileExporterHelper(minioAdapter, fileImporter);
        assertThrows(NullPointerException.class, () -> fileExporterHelper.exportMetadataToMinio(coreCCRequest));
        assertFalse(metadataIsUploaded);
    }
}
