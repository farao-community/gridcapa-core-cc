/*
 * Copyright (c) 2026, RTE (http://www.rte-france.com)
 *  This Source Code Form is subject to the terms of the Mozilla Public
 *  License, v. 2.0. If a copy of the MPL was not distributed with this
 *  file, You can obtain one at http://mozilla.org/MPL/2.0/.
 *
 */

package com.farao_community.farao.gridcapa_core_cc.app.postprocessing;

import com.farao_community.farao.gridcapa_core_cc.api.resource.CoreCCFileResource;
import com.farao_community.farao.gridcapa_core_cc.api.resource.HourlyRaoRequest;
import com.farao_community.farao.gridcapa_core_cc.api.resource.HourlyRaoResult;
import com.farao_community.farao.gridcapa_core_cc.api.resource.HourlyRaoResultErrorCode;
import com.farao_community.farao.gridcapa_core_cc.api.resource.HourlyRaoResultStatus;
import com.farao_community.farao.gridcapa_core_cc.api.resource.InternalCoreCCRequest;
import com.farao_community.farao.gridcapa_core_cc.app.util.NetworkUtil;
import com.farao_community.farao.minio_adapter.starter.MinioAdapter;
import com.powsybl.commons.report.ReportNode;
import com.powsybl.iidm.network.Network;
import com.powsybl.openrao.data.crac.api.Crac;
import com.powsybl.openrao.data.crac.api.State;
import com.powsybl.openrao.data.crac.api.parameters.CracCreationParameters;
import com.powsybl.openrao.data.crac.api.parameters.JsonCracCreationParameters;
import com.powsybl.openrao.data.crac.io.fbconstraint.FbConstraintCreationContext;
import com.powsybl.openrao.data.crac.io.fbconstraint.FbConstraintImporter;
import com.powsybl.openrao.data.crac.io.fbconstraint.parameters.FbConstraintCracCreationParameters;
import com.powsybl.openrao.data.raoresult.api.RaoResult;
import com.powsybl.openrao.raoapi.json.JsonRaoParameters;
import com.powsybl.openrao.raoapi.parameters.RaoParameters;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

import static org.mockito.Mockito.times;

/**
 * @author Vincent Bochet {@literal <vincent.bochet at rte-france.com>}
 */
class FileExporterHelperTest {

    @InjectMocks
    private FileExporterHelper fileExporterHelper;
    @Mock
    private MinioAdapter minioAdapter;

    private AutoCloseable openMocks;
    private MockedStatic<NetworkUtil> networkUtilMockedStatic;

    private OffsetDateTime timestamp = OffsetDateTime.of(2026, 10, 15, 12, 30, 0, 0, ZoneOffset.of("+2"));
    private final HourlyRaoRequest continentalHourlyRaoRequest = Mockito.mock(HourlyRaoRequest.class);
    private final HourlyRaoResult continentalHourlyRaoResult = Mockito.mock(HourlyRaoResult.class);
    private final HourlyRaoRequest semHourlyRaoRequest = Mockito.mock(HourlyRaoRequest.class);
    private final HourlyRaoResult semHourlyRaoResult = Mockito.mock(HourlyRaoResult.class);
    private final InternalCoreCCRequest internalCoreCCRequest = Mockito.mock(InternalCoreCCRequest.class);

    @BeforeEach
    void setUp() {
        openMocks = MockitoAnnotations.openMocks(this);
        networkUtilMockedStatic = Mockito.mockStatic(NetworkUtil.class);
    }

    @AfterEach
    void cleanUp() throws Exception {
        networkUtilMockedStatic.close();
        openMocks.close();
    }

    @Test
    void exportNetworkToMinioTest() {
        final String networkFile = "/util/TestCase12NodesHvdc.uct";
        final Path networkPath = Paths.get(getClass().getResource(networkFile).getPath());
        final Network network = Network.read(networkPath);

        // Given
        initializeInternalCoreCCRequestMockForMetadata();
        Mockito.when(continentalHourlyRaoRequest.getRaoRequestInstant()).thenReturn(timestamp.minusMinutes(10).toInstant().toString());

        final CoreCCPostProcessingData postProcessingData = new CoreCCPostProcessingData();
        postProcessingData.setRequest(internalCoreCCRequest);
        postProcessingData.setAcNetwork(network);
        postProcessingData.setInitialAcNetworkVariantId(network.getVariantManager().getWorkingVariantId());
        final FbConstraintCreationContext fbConstraintCreationContext = Mockito.mock(FbConstraintCreationContext.class);
        final Crac crac = Mockito.mock(Crac.class);
        Mockito.when(fbConstraintCreationContext.getCrac()).thenReturn(crac);
        final State preventiveState = Mockito.mock(State.class);
        Mockito.when(crac.getPreventiveState()).thenReturn(preventiveState);
        postProcessingData.setCracCreationContext(fbConstraintCreationContext);
        final RaoResult raoResult = Mockito.mock(RaoResult.class);
        postProcessingData.setRaoResult(raoResult);

        networkUtilMockedStatic.when(() -> NetworkUtil.applyActivatedRemedialActionsForState(network, raoResult, preventiveState)).thenAnswer(invocation -> null);
        networkUtilMockedStatic.when(() -> NetworkUtil.applyWorkaround(network)).thenAnswer(invocation -> null);

        // When
        fileExporterHelper.exportNetworkToMinio(postProcessingData);

        // Then
        networkUtilMockedStatic.verify(() -> NetworkUtil.applyActivatedRemedialActionsForState(network, raoResult, preventiveState), times(1));
        networkUtilMockedStatic.verify(() -> NetworkUtil.applyWorkaround(network), times(1));

        final ArgumentCaptor<InputStream> inputStreamArgumentCaptor = ArgumentCaptor.forClass(InputStream.class);
        Mockito.verify(minioAdapter, Mockito.times(1)).uploadOutputForTimestamp(
            Mockito.eq("/path/to/destination/20261015_1230_2D4_UX4.uct"), inputStreamArgumentCaptor.capture(), Mockito.eq("CORE_CC"), Mockito.eq("CGM_OUT"), Mockito.eq(timestamp)
        );
        Assertions.assertThat(inputStreamArgumentCaptor.getValue()).isNotNull();
        final Network networkOutput = Network.read("networkOutput.uct", inputStreamArgumentCaptor.getValue());
        Assertions.assertThat(networkOutput).isNotNull();
    }

    @Test
    void exportCneToMinioTest() throws IOException {
        // Given
        timestamp = OffsetDateTime.of(2026, 3, 26, 0, 30, 0, 0, ZoneOffset.UTC);
        final Network network = Network.read("continental_20260325_2300.xiidm", getClass().getResourceAsStream("/fileExporterHelper/continental_20260325_2300.xiidm"));

        final CracCreationParameters cracCreationParameters = JsonCracCreationParameters.read(getClass().getResourceAsStream("/crac/cracCreationParameters.json"));
        cracCreationParameters.addExtension(FbConstraintCracCreationParameters.class, new FbConstraintCracCreationParameters());
        cracCreationParameters.getExtension(FbConstraintCracCreationParameters.class).setTimestamp(timestamp);

        final InputStream cracInputStream = getClass().getResourceAsStream("/fileExporterHelper/20260326-F301-v1-17XTSO-CS------W-to-22XCORESO------S.xml");
        final FbConstraintCreationContext fbConstraintCreationContext = (FbConstraintCreationContext) new FbConstraintImporter().importData(cracInputStream, cracCreationParameters, network);
        final Crac crac = fbConstraintCreationContext.getCrac();

        final InputStream raoResultInputStream = getClass().getResourceAsStream("/fileExporterHelper/raoResult.json");
        final RaoResult raoResult = RaoResult.read(raoResultInputStream, crac);

        final RaoParameters raoParameters = JsonRaoParameters.read(getClass().getResourceAsStream("/fileExporterHelper/raoParameters.json"), ReportNode.NO_OP);

        initializeInternalCoreCCRequestMockForMetadata();
        Mockito.when(internalCoreCCRequest.getTimeInterval()).thenReturn("2026-03-25T23:00Z/2026-03-26T23:00Z");
        Mockito.when(continentalHourlyRaoRequest.getRaoRequestInstant()).thenReturn(timestamp.minusMinutes(10).toInstant().toString());

        final CoreCCPostProcessingData postProcessingData = new CoreCCPostProcessingData();
        postProcessingData.setRequest(internalCoreCCRequest);
        postProcessingData.setCracCreationContext(fbConstraintCreationContext);
        postProcessingData.setRaoResult(raoResult);
        postProcessingData.setRaoParameters(raoParameters);

        // When
        fileExporterHelper.exportCneToMinio(postProcessingData);

        // Then
        final ArgumentCaptor<InputStream> inputStreamArgumentCaptor = ArgumentCaptor.forClass(InputStream.class);
        Mockito.verify(minioAdapter, Mockito.times(1)).uploadOutputForTimestamp(
            Mockito.eq("/path/to/destination/20260326_0130_20260326-F299-v4-22XCORESO------S_to_17XTSO-CS------W.xml"), inputStreamArgumentCaptor.capture(), Mockito.eq("CORE_CC"), Mockito.eq("CNE"), Mockito.eq(timestamp)
        );
        Assertions.assertThat(inputStreamArgumentCaptor.getValue()).isNotNull();
        String cneOutput = new String(inputStreamArgumentCaptor.getValue().readAllBytes());
        cneOutput = cneOutput.replaceAll("<createdDateTime>.*</createdDateTime>", "<createdDateTime>2026-10-15T15:00:00Z</createdDateTime>");
        cneOutput = cneOutput.replaceAll("<mRID>RAseries_.*</mRID>", "<mRID>RAseries_12345</mRID>");
        String expectedCne = new String(getClass().getResourceAsStream("/fileExporterHelper/expectedCne.xml").readAllBytes());
        Assertions.assertThat(cneOutput).isEqualTo(expectedCne);
    }

    @Test
    void exportRaoResultToMinioTest() throws IOException {
        // Given
        final Network network = Network.read("continental_20260325_2300.xiidm", getClass().getResourceAsStream("/fileExporterHelper/continental_20260325_2300.xiidm"));

        final CracCreationParameters cracCreationParameters = JsonCracCreationParameters.read(getClass().getResourceAsStream("/crac/cracCreationParameters.json"));
        cracCreationParameters.addExtension(FbConstraintCracCreationParameters.class, new FbConstraintCracCreationParameters());
        cracCreationParameters.getExtension(FbConstraintCracCreationParameters.class).setTimestamp(timestamp);

        final InputStream cracInputStream = getClass().getResourceAsStream("/fileExporterHelper/continental_20260325_2300.json");
        final Crac crac = Crac.read("continental_20260325_2300.json", cracInputStream, network);
        final FbConstraintCreationContext fbConstraintCreationContext = Mockito.mock(FbConstraintCreationContext.class);
        Mockito.when(fbConstraintCreationContext.getCrac()).thenReturn(crac);

        final InputStream raoResultInputStream = getClass().getResourceAsStream("/fileExporterHelper/raoResult.json");
        final RaoResult raoResult = RaoResult.read(raoResultInputStream, crac);

        final RaoParameters raoParameters = JsonRaoParameters.read(getClass().getResourceAsStream("/fileExporterHelper/raoParameters.json"), ReportNode.NO_OP);

        initializeInternalCoreCCRequestMockForMetadata();
        Mockito.when(continentalHourlyRaoRequest.getRaoRequestInstant()).thenReturn(timestamp.minusMinutes(10).toInstant().toString());

        final CoreCCPostProcessingData postProcessingData = new CoreCCPostProcessingData();
        postProcessingData.setRequest(internalCoreCCRequest);
        postProcessingData.setCracCreationContext(fbConstraintCreationContext);
        postProcessingData.setRaoResult(raoResult);
        postProcessingData.setRaoParameters(raoParameters);

        // When
        fileExporterHelper.exportRaoResultToMinio(postProcessingData);

        // Then
        final ArgumentCaptor<InputStream> inputStreamArgumentCaptor = ArgumentCaptor.forClass(InputStream.class);
        Mockito.verify(minioAdapter, Mockito.times(1)).uploadOutputForTimestamp(
            Mockito.eq("/path/to/destination/CASTOR-INTERNAL-RESULTS_20261015_1230.json"), inputStreamArgumentCaptor.capture(), Mockito.eq("CORE_CC"), Mockito.eq("RAO_RESULT"), Mockito.eq(timestamp)
        );
        Assertions.assertThat(inputStreamArgumentCaptor.getValue()).isNotNull();
        final RaoResult raoResultOutput = RaoResult.read(inputStreamArgumentCaptor.getValue(), crac);
        Assertions.assertThat(raoResultOutput).isNotNull();
    }

    @Test
    void exportMetadataToMinioTest() throws IOException {
        // Given
        initializeInternalCoreCCRequestMockForMetadata();
        Mockito.when(internalCoreCCRequest.isSemEnabled()).thenReturn(true);
        initializeContinentalHourlyRequestAndResultMocksForMetadata();
        Mockito.when(continentalHourlyRaoResult.getStatus()).thenReturn(HourlyRaoResultStatus.SUCCESS);
        Mockito.when(continentalHourlyRaoResult.getErrorCodeString()).thenReturn(HourlyRaoResultErrorCode.RUNNING.getCode());
        initializeSemHourlyRequestAndResultMocksForMetadata();
        Mockito.when(semHourlyRaoResult.getStatus()).thenReturn(HourlyRaoResultStatus.SUCCESS);
        Mockito.when(semHourlyRaoResult.getErrorCodeString()).thenReturn(HourlyRaoResultErrorCode.RUNNING.getCode());

        final CoreCCPostProcessingData coreCCPostProcessingData = new CoreCCPostProcessingData();
        coreCCPostProcessingData.setRequest(internalCoreCCRequest);

        // When
        fileExporterHelper.exportMetadataToMinio(coreCCPostProcessingData);

        // Then
        final ArgumentCaptor<InputStream> inputStreamArgumentCaptor = ArgumentCaptor.forClass(InputStream.class);
        Mockito.verify(minioAdapter, times(1)).uploadOutputForTimestamp(
            Mockito.eq("/path/to/destination/20261015_1230_METADATA-04.json"),
            inputStreamArgumentCaptor.capture(),
            Mockito.eq("CORE_CC"),
            Mockito.eq("METADATA"),
            Mockito.eq(timestamp));

        Assertions.assertThat(inputStreamArgumentCaptor.getValue()).isNotNull();
        final String metadataOutput = new String(inputStreamArgumentCaptor.getValue().readAllBytes());
        Assertions.assertThat(metadataOutput).isEqualTo(
            """
                {"raoRequestFileName":"raoRequest.json","requestReceivedInstant":"2026-10-15T10:30:00Z","raoRequestInstant":"2026-10-15T10:20:00Z","timeInterval":null,"correlationId":"00000000-0000-0000-0000-000000000001","version":4,"continentalComputationStart":"2026-10-15T10:40:00Z","continentalComputationEnd":"2026-10-15T10:42:00Z","continentalComputationStatus":"SUCCESS","continentalComputationErrorCode":"0","continentalComputationErrorMessage":"Error message for Continental","semComputationStart":"2026-10-15T10:50:00Z","semComputationEnd":"2026-10-15T10:52:00Z","semComputationStatus":"SUCCESS","semComputationErrorCode":"0","semComputationErrorMessage":"Error message for SEM"}"""
        );
    }

    @Test
    void exportMetadataToMinioWhenPreProcessingFailedAndSemIsEnabledTest() throws IOException {
        // Given
        initializeInternalCoreCCRequestMockForMetadata();
        Mockito.when(internalCoreCCRequest.isSemEnabled()).thenReturn(true);
        initializeContinentalHourlyRequestAndResultMocksForMetadata();
        initializeSemHourlyRequestAndResultMocksForMetadata();

        // When
        fileExporterHelper.exportMetadataToMinioWhenPreProcessingFailed(internalCoreCCRequest);

        // Then
        final ArgumentCaptor<InputStream> inputStreamArgumentCaptor = ArgumentCaptor.forClass(InputStream.class);
        Mockito.verify(minioAdapter, times(1)).uploadOutputForTimestamp(
            Mockito.eq("/path/to/destination/20261015_1230_METADATA-04.json"),
            inputStreamArgumentCaptor.capture(),
            Mockito.eq("CORE_CC"),
            Mockito.eq("METADATA"),
            Mockito.eq(timestamp));

        Assertions.assertThat(inputStreamArgumentCaptor.getValue()).isNotNull();
        final String metadataOutput = new String(inputStreamArgumentCaptor.getValue().readAllBytes());
        Assertions.assertThat(metadataOutput).isEqualTo(
            """
                {"raoRequestFileName":"raoRequest.json","requestReceivedInstant":"2026-10-15T10:30:00Z","raoRequestInstant":"2026-10-15T10:20:00Z","timeInterval":null,"correlationId":"00000000-0000-0000-0000-000000000001","version":4,"continentalComputationStart":"2026-10-15T10:40:00Z","continentalComputationEnd":"2026-10-15T10:42:00Z","continentalComputationStatus":"FAILURE","continentalComputationErrorCode":"2","continentalComputationErrorMessage":"Error message for Continental","semComputationStart":"2026-10-15T10:50:00Z","semComputationEnd":"2026-10-15T10:52:00Z","semComputationStatus":"FAILURE","semComputationErrorCode":"2","semComputationErrorMessage":"Error message for SEM"}"""
        );
    }

    @Test
    void exportMetadataToMinioWhenPreProcessingFailedAndSemIsDisabledTest() throws IOException {
        // Given
        initializeInternalCoreCCRequestMockForMetadata();
        Mockito.when(internalCoreCCRequest.isSemEnabled()).thenReturn(false);
        initializeContinentalHourlyRequestAndResultMocksForMetadata();

        // When
        fileExporterHelper.exportMetadataToMinioWhenPreProcessingFailed(internalCoreCCRequest);

        // Then
        final ArgumentCaptor<InputStream> inputStreamArgumentCaptor = ArgumentCaptor.forClass(InputStream.class);
        Mockito.verify(minioAdapter, times(1)).uploadOutputForTimestamp(
            Mockito.eq("/path/to/destination/20261015_1230_METADATA-04.json"),
            inputStreamArgumentCaptor.capture(),
            Mockito.eq("CORE_CC"),
            Mockito.eq("METADATA"),
            Mockito.eq(timestamp));

        Assertions.assertThat(inputStreamArgumentCaptor.getValue()).isNotNull();
        final String metadataOutput = new String(inputStreamArgumentCaptor.getValue().readAllBytes());
        Assertions.assertThat(metadataOutput).isEqualTo(
            """
                {"raoRequestFileName":"raoRequest.json","requestReceivedInstant":"2026-10-15T10:30:00Z","raoRequestInstant":"2026-10-15T10:20:00Z","timeInterval":null,"correlationId":"00000000-0000-0000-0000-000000000001","version":4,"continentalComputationStart":"2026-10-15T10:40:00Z","continentalComputationEnd":"2026-10-15T10:42:00Z","continentalComputationStatus":"FAILURE","continentalComputationErrorCode":"2","continentalComputationErrorMessage":"Error message for Continental","semComputationStart":null,"semComputationEnd":null,"semComputationStatus":null,"semComputationErrorCode":null,"semComputationErrorMessage":null}"""
        );
    }

    private void initializeInternalCoreCCRequestMockForMetadata() {
        Mockito.when(internalCoreCCRequest.getTimestamp()).thenReturn(timestamp);
        Mockito.when(internalCoreCCRequest.getContinentalHourlyRaoRequest()).thenReturn(continentalHourlyRaoRequest);
        Mockito.when(internalCoreCCRequest.getContinentalHourlyRaoResult()).thenReturn(continentalHourlyRaoResult);
        Mockito.when(internalCoreCCRequest.getSemHourlyRaoRequest()).thenReturn(semHourlyRaoRequest);
        Mockito.when(internalCoreCCRequest.getSemHourlyRaoResult()).thenReturn(semHourlyRaoResult);
        Mockito.when(internalCoreCCRequest.getVersion()).thenReturn(4);
        Mockito.when(internalCoreCCRequest.getRaoRequest()).thenReturn(new CoreCCFileResource("raoRequest.json", "http://path/to/raoRequest.json"));
        Mockito.when(internalCoreCCRequest.getRequestReceivedInstant()).thenReturn(timestamp.toInstant());
        Mockito.when(internalCoreCCRequest.getCorrelationId()).thenReturn("00000000-0000-0000-0000-000000000001");
        Mockito.when(internalCoreCCRequest.getDestinationPath()).thenReturn("/path/to/destination");
    }

    private void initializeContinentalHourlyRequestAndResultMocksForMetadata() {
        Mockito.when(continentalHourlyRaoRequest.getResultsDestination()).thenReturn("/path/to/destination/continental");
        Mockito.when(continentalHourlyRaoRequest.getRaoRequestInstant()).thenReturn(timestamp.minusMinutes(10).toInstant().toString());
        Mockito.when(continentalHourlyRaoResult.getRaoRequestInstant()).thenReturn(timestamp.minusMinutes(10).toInstant().toString());
        Mockito.when(continentalHourlyRaoResult.getStatus()).thenReturn(HourlyRaoResultStatus.FAILURE);
        Mockito.when(continentalHourlyRaoResult.getComputationStartInstant()).thenReturn(timestamp.plusMinutes(10).toInstant());
        Mockito.when(continentalHourlyRaoResult.getComputationEndInstant()).thenReturn(timestamp.plusMinutes(12).toInstant());
        Mockito.when(continentalHourlyRaoResult.getErrorCodeString()).thenReturn(HourlyRaoResultErrorCode.TS_PREPROCESSING_FAILURE.getCode());
        Mockito.when(continentalHourlyRaoResult.getErrorMessage()).thenReturn("Error message for Continental");
    }

    private void initializeSemHourlyRequestAndResultMocksForMetadata() {
        Mockito.when(semHourlyRaoRequest.getResultsDestination()).thenReturn("/path/to/destination/sem");
        Mockito.when(semHourlyRaoRequest.getRaoRequestInstant()).thenReturn(timestamp.minusMinutes(30).toInstant().toString());
        Mockito.when(semHourlyRaoResult.getRaoRequestInstant()).thenReturn(timestamp.minusMinutes(30).toInstant().toString());
        Mockito.when(semHourlyRaoResult.getStatus()).thenReturn(HourlyRaoResultStatus.FAILURE);
        Mockito.when(semHourlyRaoResult.getComputationStartInstant()).thenReturn(timestamp.plusMinutes(20).toInstant());
        Mockito.when(semHourlyRaoResult.getComputationEndInstant()).thenReturn(timestamp.plusMinutes(22).toInstant());
        Mockito.when(semHourlyRaoResult.getErrorCodeString()).thenReturn(HourlyRaoResultErrorCode.TS_PREPROCESSING_FAILURE.getCode());
        Mockito.when(semHourlyRaoResult.getErrorMessage()).thenReturn("Error message for SEM");
    }
}
