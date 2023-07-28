/*
 * Copyright (c) 2023, RTE (http://www.rte-france.com)
 *  This Source Code Form is subject to the terms of the Mozilla Public
 *  License, v. 2.0. If a copy of the MPL was not distributed with this
 *  file, You can obtain one at http://mozilla.org/MPL/2.0/.
 *
 */

package com.farao_community.farao.gridcapa_core_cc.app.postprocessing;

import com.farao_community.farao.data.crac_api.Crac;
import com.farao_community.farao.data.crac_creation.creator.fb_constraint.crac_creator.FbConstraintCreationContext;
import com.farao_community.farao.data.crac_io_api.CracImporters;
import com.farao_community.farao.data.rao_result_api.RaoResult;
import com.farao_community.farao.data.rao_result_json.RaoResultImporter;
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
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;

import static org.junit.jupiter.api.Assertions.*;

/**
 * @author Thomas Bouquet {@literal <thomas.bouquet at rte-france.com>}
 */
public class FileExporterHelperTest {

    private String newtorkFile = "/util/TestCase12NodesHvdc.uct";
    private Path networkPath = Paths.get(getClass().getResource(newtorkFile).getPath());
    private Network network = Network.read(networkPath);
    private FileImporter fileImporter;
    private MinioAdapter minioAdapter;
    private InternalCoreCCRequest coreCCRequest;
    private HourlyRaoResult hourlyRaoResult;
    private HourlyRaoRequest hourlyRaoRequest;
    private Boolean networkIsUploaded;
    private Boolean raoResultIsUploaded;
    private Boolean metadataIsUploaded;
    private Boolean cneIsUploaded;
    private String instantString = "2023-07-25T16:57:00Z";
    private Instant instant = Instant.parse(instantString);
    private OffsetDateTime timestamp = OffsetDateTime.of(2023, 7, 27, 10, 47, 51, 0, ZoneId.of("Europe/Brussels").getRules().getOffset(LocalDateTime.now()));

    public FileExporterHelperTest() {
    }

    @BeforeEach
    void setUp() {
        networkIsUploaded = false;
        raoResultIsUploaded = false;
        metadataIsUploaded = false;
        cneIsUploaded = false;
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
        Mockito.when(coreCCRequest.getTimestamp()).thenReturn(timestamp);
        Mockito.when(coreCCRequest.getTimeInterval()).thenReturn("2023-07-25T15:02:00Z/2023-07-25T15:03:00Z");
    }

    @Test
    void exportNetworkToMinio() throws IOException {
        FileExporterHelper fileExporterHelper = new FileExporterHelper(minioAdapter, fileImporter);
        Mockito.doAnswer(answer -> networkIsUploaded = true).when(minioAdapter).uploadOutputForTimestamp(Mockito.eq("/path/20230725_1730_2D2_UX1.uct"), Mockito.any(), Mockito.any(), Mockito.any(), Mockito.any());
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
        FileExporterHelper fileExporterHelper = new FileExporterHelper(minioAdapter, fileImporter);
        Mockito.doAnswer(answer -> raoResultIsUploaded = true).when(minioAdapter).uploadOutputForTimestamp(Mockito.eq("/path/20230725_1730_RAO-RESULT-0V.txt"), Mockito.any(), Mockito.any(), Mockito.any(), Mockito.any());
        fileExporterHelper.exportRaoResultToMinio(coreCCRequest);
        assertTrue(raoResultIsUploaded);
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
        CoreCCFileResource raoRequest = Mockito.mock(CoreCCFileResource.class);
        Mockito.when(raoRequest.getFilename()).thenReturn("raoRequest.json");
        Mockito.when(coreCCRequest.getRaoRequest()).thenReturn(raoRequest);
        Mockito.when(coreCCRequest.getRequestReceivedInstant()).thenReturn(instant);
        Mockito.when(coreCCRequest.getCorrelationId()).thenReturn("id");
        Mockito.when(hourlyRaoResult.getRaoRequestInstant()).thenReturn(instantString);
        Mockito.when(hourlyRaoResult.getComputationStartInstant()).thenReturn(instant);
        Mockito.when(hourlyRaoResult.getComputationEndInstant()).thenReturn(instant);
        Mockito.when(hourlyRaoResult.getStatus()).thenReturn(HourlyRaoResult.Status.RUNNING);
        Mockito.when(hourlyRaoResult.getErrorCodeString()).thenReturn("Error code");
        Mockito.when(hourlyRaoResult.getErrorMessage()).thenReturn("Error message.");
        FileExporterHelper fileExporterHelper = new FileExporterHelper(minioAdapter, fileImporter);
        Mockito.doAnswer(answer -> metadataIsUploaded = true).when(minioAdapter).uploadOutputForTimestamp(Mockito.eq("/path/20230725_1830_METADATA-01.json"), Mockito.any(), Mockito.any(), Mockito.any(), Mockito.any());
        fileExporterHelper.exportMetadataToMinio(coreCCRequest);
        assertTrue(metadataIsUploaded);
    }

    @Test
    void errorWhenUploadingMetadataToMinio() throws IOException {
        FileExporterHelper fileExporterHelper = new FileExporterHelper(minioAdapter, fileImporter);
        assertThrows(NullPointerException.class, () -> fileExporterHelper.exportMetadataToMinio(coreCCRequest));
        assertFalse(metadataIsUploaded);
    }

    @Test
    void exportCneToMinio() throws IOException {
        FileExporterHelper fileExporterHelper = new FileExporterHelper(minioAdapter, fileImporter);

        String cracXmlFileName = "/util/crac.json";
        String cracJsonFileName = "/util/crac.json";
        Path cracJsonFilePath = Paths.get(getClass().getResource(cracJsonFileName).getPath());
        String networkFileName = "/util/TestCase12NodesHvdc.uct";
        Path networkFilePath = Paths.get(getClass().getResource(networkFileName).getPath());
        String raoResultFileName = "/util/raoResult.json";
        Path raoResultFilePath = Paths.get(getClass().getResource(raoResultFileName).getPath());
        String raoParametersFileName = "/util/raoParameters.json";
        Path raoParametersFilePath = Paths.get(getClass().getResource(raoParametersFileName).getPath());

        Mockito.when(hourlyRaoRequest.getNetworkFileUrl()).thenReturn(networkFileName);
        Mockito.when(minioAdapter.getFile(networkFileName)).thenReturn(Files.newInputStream(networkFilePath));

        CoreCCFileResource cbcora = Mockito.mock(CoreCCFileResource.class);
        Mockito.when(cbcora.getUrl()).thenReturn(cracXmlFileName);
        Mockito.when(coreCCRequest.getCbcora()).thenReturn(cbcora);
        Mockito.when(coreCCRequest.getRequestReceivedInstant()).thenReturn(instant);

        FbConstraintCreationContext fbConstraintCreationContext = Mockito.mock(FbConstraintCreationContext.class);
        Mockito.when(fbConstraintCreationContext.isCreationSuccessful()).thenReturn(true);
        Mockito.when(fbConstraintCreationContext.getTimeStamp()).thenReturn(timestamp);
        Mockito.when(fileImporter.importCrac(Mockito.any(), Mockito.any(), Mockito.any())).thenReturn(fbConstraintCreationContext);

        Crac crac = CracImporters.importCrac(cracJsonFilePath.getFileName().toString(), Files.newInputStream(cracJsonFilePath));
        RaoResult raoResult = (new RaoResultImporter()).importRaoResult(Files.newInputStream(raoResultFilePath), crac);
        Mockito.when(fileImporter.importRaoResult(Mockito.any(), Mockito.any())).thenReturn(raoResult);

        Mockito.when(hourlyRaoRequest.getCracFileUrl()).thenReturn(cracJsonFileName);
        Mockito.when(minioAdapter.getFile(cracXmlFileName)).thenReturn(Files.newInputStream(cracJsonFilePath));

        Mockito.when(hourlyRaoRequest.getRaoParametersFileUrl()).thenReturn(raoParametersFileName);
        Mockito.when(minioAdapter.getFile(raoParametersFileName)).thenReturn(Files.newInputStream(raoParametersFilePath));

        Mockito.doAnswer(answer -> cneIsUploaded = true).when(minioAdapter).uploadOutputForTimestamp(Mockito.eq("/path/20230725_1730_20230725-F299-v1-22XCORESO------S_to_17XTSO-CS------W.xml"), Mockito.any(), Mockito.any(), Mockito.any(), Mockito.any());

        fileExporterHelper.exportCneToMinio(coreCCRequest);
        assertTrue(cneIsUploaded);
    }
}
