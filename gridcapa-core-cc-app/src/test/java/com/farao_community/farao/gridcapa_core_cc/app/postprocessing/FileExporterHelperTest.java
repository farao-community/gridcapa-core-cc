/*
 * Copyright (c) 2023, RTE (http://www.rte-france.com)
 *  This Source Code Form is subject to the terms of the Mozilla Public
 *  License, v. 2.0. If a copy of the MPL was not distributed with this
 *  file, You can obtain one at http://mozilla.org/MPL/2.0/.
 *
 */

package com.farao_community.farao.gridcapa_core_cc.app.postprocessing;

import com.farao_community.farao.data.core_cne_exporter.xsd.CriticalNetworkElementMarketDocument;
import com.farao_community.farao.data.crac_api.Crac;
import com.farao_community.farao.data.crac_creation.creator.fb_constraint.crac_creator.FbConstraintCreationContext;
import com.farao_community.farao.data.crac_io_api.CracImporters;
import com.farao_community.farao.data.rao_result_api.RaoResult;
import com.farao_community.farao.data.rao_result_json.RaoResultImporter;
import com.farao_community.farao.gridcapa_core_cc.api.resource.CoreCCFileResource;
import com.farao_community.farao.gridcapa_core_cc.api.resource.HourlyRaoRequest;
import com.farao_community.farao.gridcapa_core_cc.api.resource.HourlyRaoResult;
import com.farao_community.farao.gridcapa_core_cc.api.resource.InternalCoreCCRequest;
import com.farao_community.farao.gridcapa_core_cc.app.MinioFileWriter;
import com.farao_community.farao.gridcapa_core_cc.app.services.FileImporter;
import com.farao_community.farao.gridcapa_core_cc.app.util.CoreNetworkImporterWrapper;
import com.farao_community.farao.gridcapa_core_cc.app.util.JaxbUtil;
import com.farao_community.farao.minio_adapter.starter.MinioAdapter;
import com.farao_community.farao.minio_adapter.starter.MinioAdapterProperties;
import com.powsybl.iidm.network.Identifiable;
import com.powsybl.iidm.network.Network;
import io.minio.MinioClient;
import org.apache.commons.io.FileUtils;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.io.*;
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
class FileExporterHelperTest {

    private final String networkFile = "/util/TestCase12NodesHvdc.uct";
    private final Path networkPath = Paths.get(getClass().getResource(networkFile).getPath());
    private final Network network = Network.read(networkPath);
    private FileImporter fileImporter;
    private MinioAdapter minioAdapter;
    private InternalCoreCCRequest coreCCRequest;
    private HourlyRaoResult hourlyRaoResult;
    private HourlyRaoRequest hourlyRaoRequest;
    private final String instantString = "2023-07-25T16:57:00Z";
    private final Instant instant = Instant.parse(instantString);
    private final OffsetDateTime timestamp = OffsetDateTime.of(2023, 7, 27, 10, 47, 51, 0, ZoneId.of("Europe/Brussels").getRules().getOffset(LocalDateTime.now()));
    private final MinioAdapterProperties properties = Mockito.mock(MinioAdapterProperties.class);
    private final MinioClient minioClient = Mockito.mock(MinioClient.class);
    private final MinioFileWriter minioFileWriter = new MinioFileWriter(properties, minioClient);
    private static final String TEMP_DIR = System.getProperty("java.io.tmpdir");

    @BeforeEach
    void setUp() {
        fileImporter = Mockito.mock(FileImporter.class);
        Mockito.when(fileImporter.importNetworkFromUrl(Mockito.any())).thenReturn(network);
        Mockito.doReturn(FileExporterHelperTest.class.getResourceAsStream("/fileExporterHelper/raoResult.json")).when(fileImporter).importFileUrlAsInputStream("raoResult.json");
        minioAdapter = Mockito.mock(MinioAdapter.class);
        hourlyRaoResult = Mockito.mock(HourlyRaoResult.class);
        Mockito.when(hourlyRaoResult.getRaoRequestInstant()).thenReturn("2023-07-25T15:02:00Z");
        Mockito.when(hourlyRaoResult.getNetworkWithPraUrl()).thenReturn(networkFile);
        Mockito.when(hourlyRaoResult.getRaoResultFileUrl()).thenReturn("raoResult.json");
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
        FileExporterHelper fileExporterHelper = new FileExporterHelper(minioFileWriter, fileImporter);
        fileExporterHelper.exportNetworkToMinio(coreCCRequest);
        String generatedFilePath = TEMP_DIR + "/gridcapa-core-cc/CORE_CC/CGM_OUT/2023-07-27T10:47:51+02:00/2023-07-27T11:47:51+02:00/path/20230725_1730_2D2_UX1.uct";
        removeCreationDateFromUct(new File(generatedFilePath));
        assertFilesContentEqual("/fileExporterHelper/uploadedNetwork.uct", generatedFilePath);

        Network networkAfterPostTreatment = CoreNetworkImporterWrapper.loadNetwork(Path.of(generatedFilePath));

        assertTrue(networkAfterPostTreatment.getLoadStream().noneMatch(load -> load.getId().contains("_virtualLoad")));
        assertTrue(networkAfterPostTreatment.getLoadStream().noneMatch(Identifiable::isFictitious));
        assertNull(networkAfterPostTreatment.getGenerator("XLI_OB1A"));
        assertNull(networkAfterPostTreatment.getGenerator("XLI_OB1B"));
    }

    @Test
    void errorWhenUploadingNetworkToMinio() {
        Mockito.when(coreCCRequest.getTimestamp()).thenThrow(new RuntimeException("Timestamp could not be retrieved."));
        FileExporterHelper fileExporterHelper = new FileExporterHelper(minioAdapter, fileImporter);
        RuntimeException exception = assertThrows(RuntimeException.class, () -> fileExporterHelper.exportNetworkToMinio(coreCCRequest));
        assertEquals("Timestamp could not be retrieved.", exception.getMessage());
    }

    @Test
    void exportRaoResultToMinio() throws IOException {
        FileExporterHelper fileExporterHelper = new FileExporterHelper(minioFileWriter, fileImporter);
        fileExporterHelper.exportRaoResultToMinio(coreCCRequest);
        String generatedFilePath = TEMP_DIR + "/gridcapa-core-cc/CORE_CC/RAO_RESULT/2023-07-27T10:47:51+02:00/2023-07-27T11:47:51+02:00/path/raoResult.json";
        assertFilesContentEqual("/fileExporterHelper/uploadedRaoResult.json", generatedFilePath);
    }

    @Test
    void errorWhenUploadingRaoResultToMinio() {
        Mockito.when(coreCCRequest.getTimestamp()).thenThrow(new RuntimeException("Timestamp could not be retrieved."));
        FileExporterHelper fileExporterHelper = new FileExporterHelper(minioAdapter, fileImporter);
        RuntimeException exception = assertThrows(RuntimeException.class, () -> fileExporterHelper.exportRaoResultToMinio(coreCCRequest));
        assertEquals("Timestamp could not be retrieved.", exception.getMessage());
    }

    @Test
    void exportMetadataToMinio() throws IOException {
        CoreCCFileResource raoRequest = Mockito.mock(CoreCCFileResource.class);
        Mockito.when(raoRequest.getFilename()).thenReturn("raoRequest.json");
        Mockito.when(coreCCRequest.getRaoRequest()).thenReturn(raoRequest);
        Mockito.when(coreCCRequest.getRequestReceivedInstant()).thenReturn(instant);
        Mockito.when(coreCCRequest.getCorrelationId()).thenReturn("00000000-0000-0000-0000-000000000001");
        Mockito.when(hourlyRaoResult.getRaoRequestInstant()).thenReturn(instantString);
        Mockito.when(hourlyRaoResult.getComputationStartInstant()).thenReturn(instant);
        Mockito.when(hourlyRaoResult.getComputationEndInstant()).thenReturn(instant.plusSeconds(60));
        Mockito.when(hourlyRaoResult.getStatus()).thenReturn(HourlyRaoResult.Status.SUCCESS);
        Mockito.when(hourlyRaoResult.getErrorCodeString()).thenReturn("0");
        Mockito.when(hourlyRaoResult.getErrorMessage()).thenReturn("Error message.");
        FileExporterHelper fileExporterHelper = new FileExporterHelper(minioFileWriter, fileImporter);
        fileExporterHelper.exportMetadataToMinio(coreCCRequest);
        String generatedFilePath = TEMP_DIR + "/gridcapa-core-cc/CORE_CC/METADATA/2023-07-27T10:47:51+02:00/2023-07-27T11:47:51+02:00/path/20230725_1830_METADATA-01.json";
        assertFilesContentEqual("/fileExporterHelper/uploadedMetadata.json", generatedFilePath);
    }

    @Test
    void errorWhenUploadingMetadataToMinio() {
        FileExporterHelper fileExporterHelper = new FileExporterHelper(minioAdapter, fileImporter);
        assertThrows(NullPointerException.class, () -> fileExporterHelper.exportMetadataToMinio(coreCCRequest));
    }

    @Test
    void exportCneToMinio() throws IOException {
        FileExporterHelper fileExporterHelper = new FileExporterHelper(minioFileWriter, fileImporter);

        String cracXmlFileName = "/util/crac.json";
        String cracJsonFileName = "/util/crac.json";
        Path cracJsonFilePath = Paths.get(getClass().getResource(cracJsonFileName).getPath());
        String networkFileName = "/util/TestCase12NodesHvdc.uct";
        String raoResultFileName = "/util/raoResult.json";
        Path raoResultFilePath = Paths.get(getClass().getResource(raoResultFileName).getPath());
        String raoParametersFileName = "/util/raoParameters.json";

        Mockito.when(hourlyRaoRequest.getNetworkFileUrl()).thenReturn(networkFileName);

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
        Mockito.when(hourlyRaoRequest.getRaoParametersFileUrl()).thenReturn(raoParametersFileName);

        fileExporterHelper.exportCneToMinio(coreCCRequest);

        String generatedFilePath = TEMP_DIR + "/gridcapa-core-cc/CORE_CC/CNE/2023-07-27T10:47:51+02:00/2023-07-27T11:47:51+02:00/path/20230725_1730_20230725-F299-v1-22XCORESO------S_to_17XTSO-CS------W.xml";
        removeCreationDateInCne(new File(generatedFilePath));
        assertFilesContentEqual("/fileExporterHelper/uploadedCne.xml", generatedFilePath);

        try (FileInputStream inputStreamCne = new FileInputStream(generatedFilePath)) {
            CriticalNetworkElementMarketDocument cneFile = JaxbUtil.unmarshalContent(CriticalNetworkElementMarketDocument.class, inputStreamCne);
            assertEquals("22XCORESO------S-20230727-F299v1", cneFile.getMRID());
            assertEquals("1", cneFile.getRevisionNumber());
            assertEquals("10Y1001C--00059P", cneFile.getDomainMRID().getValue());
            assertEquals("A48", cneFile.getProcessProcessType());
            assertEquals("A44", cneFile.getSenderMarketParticipantMarketRoleType());
            assertEquals("22XCORESO------S", cneFile.getSenderMarketParticipantMRID().getValue());
            assertEquals("A36", cneFile.getReceiverMarketParticipantMarketRoleType());
            assertEquals("17XTSO-CS------W", cneFile.getReceiverMarketParticipantMRID().getValue());
            assertEquals("2023-07-25T15:02Z", cneFile.getTimePeriodTimeInterval().getStart());
            assertEquals("2023-07-25T15:03Z", cneFile.getTimePeriodTimeInterval().getEnd());
        } catch (Exception e) {
            fail("Failed to read generated CNE file");
        }
    }

    private void removeCreationDateFromUct(File file) throws IOException {
        BufferedReader reader = new BufferedReader(new FileReader(file));
        String pattern = "\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}\\.\\d{3}(?:\\+\\d{2}:\\d{2})?Z?";
        String replaceBy = "yyyy-MM-ddTHH:mm:ss.SSS+HH:mm";
        String line;
        StringBuilder oldText = new StringBuilder();
        while ((line = reader.readLine()) != null) {
            oldText.append(line).append("\r\n");
        }
        reader.close();
        String newText = oldText.toString().replaceAll(pattern, replaceBy);
        FileWriter writer = new FileWriter(file.getAbsolutePath());
        writer.write(newText);
        writer.close();
    }

    public static void removeCreationDateInCne(File file) throws IOException {
        BufferedReader reader = new BufferedReader(new FileReader(file));
        String pattern = "<createdDateTime>\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}Z</createdDateTime>";
        String replaceBy = "<createdDateTime>yyyy-MM-ddTHH:mm:ssZ</createdDateTime>";
        String line;
        StringBuilder oldText = new StringBuilder();
        while ((line = reader.readLine()) != null) {
            oldText.append(line).append("\r\n");
        }
        reader.close();
        String newText = oldText.toString().replaceAll(pattern, replaceBy);
        FileWriter writer = new FileWriter(file.getAbsolutePath());
        writer.write(newText);
        writer.close();
    }

    private static void assertFilesContentEqual(String resource, String generatedFile) throws IOException {
        String expectedFileContents = new String(FileExporterHelperTest.class.getResourceAsStream(resource).readAllBytes()).replace("\r", "");
        String actualFileContents = new String(Files.newInputStream(Paths.get(generatedFile)).readAllBytes()).replace("\r", "");
        assertEquals(expectedFileContents, actualFileContents);
    }

    @AfterAll
    public static void deleteTemporaryDirectory() throws IOException {
        FileUtils.deleteDirectory(new File(TEMP_DIR + "/gridcapa-core-cc"));
    }

}
