/*
 * Copyright (c) 2026, RTE (http://www.rte-france.com)
 *  This Source Code Form is subject to the terms of the Mozilla Public
 *  License, v. 2.0. If a copy of the MPL was not distributed with this
 *  file, You can obtain one at http://mozilla.org/MPL/2.0/.
 *
 */

package com.farao_community.farao.gridcapa_core_cc.app.postprocessing;

import com.farao_community.farao.gridcapa.task_manager.api.TaskParameterDto;
import com.farao_community.farao.gridcapa_core_cc.api.resource.CoreCCFileResource;
import com.farao_community.farao.gridcapa_core_cc.api.resource.CoreCCRequest;
import com.farao_community.farao.gridcapa_core_cc.api.resource.HourlyRaoRequest;
import com.farao_community.farao.gridcapa_core_cc.api.resource.HourlyRaoResult;
import com.farao_community.farao.gridcapa_core_cc.api.resource.HourlyRaoResultStatus;
import com.farao_community.farao.gridcapa_core_cc.api.resource.InternalCoreCCRequest;
import com.farao_community.farao.gridcapa_core_cc.app.entities.CgmsAndXmlHeader;
import com.farao_community.farao.gridcapa_core_cc.app.services.FileImporter;
import com.farao_community.farao.gridcapa_core_cc.app.util.CoreNetworkImporterWrapper;
import com.powsybl.commons.report.ReportNode;
import com.powsybl.glsk.commons.ZonalData;
import com.powsybl.iidm.network.Network;
import com.powsybl.iidm.network.VariantManager;
import com.powsybl.openrao.data.crac.api.Crac;
import com.powsybl.openrao.data.crac.io.fbconstraint.FbConstraintCreationContext;
import com.powsybl.openrao.data.raoresult.api.RaoResult;
import com.powsybl.openrao.data.refprog.referenceprogram.ReferenceProgram;
import com.powsybl.openrao.raoapi.parameters.RaoParameters;
import com.powsybl.openrao.virtualhubs.VirtualHubsConfiguration;
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

import java.nio.file.Path;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/**
 * @author Vincent Bochet {@literal <vincent.bochet at rte-france.com>}
 */
class CoreCCPostProcessServiceTest {
    public static final String INITIAL_VARIANT_ID = "InitialVariantId";
    public static final String INSTANT = "2026-10-15T11:30:00Z";

    @InjectMocks
    private CoreCCPostProcessService postProcessService;
    @Mock
    private FileImporter fileImporter;
    @Mock
    private FileExporterHelper fileExporterHelper;
    @Mock
    private RaoResultMerger raoResultMerger;

    private InternalCoreCCRequest internalCoreCCRequest;
    private Network cgmNetwork;
    private Network dcCgmNetwork;
    private RaoParameters raoParameters;
    private final OffsetDateTime timestamp = OffsetDateTime.parse("2021-10-03T00:30Z");
    private final CoreCCFileResource cgmFile = new CoreCCFileResource("network.txt", "http://path/to/cgm/file");
    private final CoreCCFileResource dcCgmFile = new CoreCCFileResource("dcnetwork.txt", "http://path/to/dccgm/file");
    private final CoreCCFileResource cbcoraFile = new CoreCCFileResource("cbcora.txt", "http://path/to/cbcora/file");
    private final CoreCCFileResource glskFile = new CoreCCFileResource("glsk.txt", "http://path/to/glsk/file");
    private final CoreCCFileResource refProgFile = new CoreCCFileResource("refprog.txt", "http://path/to/refProg/file");
    private final CoreCCFileResource raoRequestFile = new CoreCCFileResource("raoRequest.txt", "http://path/to/raoRequest/file");
    private final CoreCCFileResource virtualHubFile = new CoreCCFileResource("virtualHub.txt", "http://path/to/virtualHub/file");
    private final CoreCCFileResource raoParametersFile = new CoreCCFileResource("raoParameters.json", "http://path/to/raoParameters/file");

    private AutoCloseable openMocks;
    private MockedStatic<CoreNetworkImporterWrapper> coreNetworkImporterWrapperMockedStatic;

    @BeforeEach
    void setUp() {
        openMocks = MockitoAnnotations.openMocks(this);

        final List<TaskParameterDto> taskParameterList = List.of(new TaskParameterDto("USE_DC_CGM_INPUT", "BOOLEAN", "TRUE", "FALSE"));
        final String currentRunId = UUID.randomUUID().toString();

        final CoreCCRequest coreCCRequest = new CoreCCRequest("id", currentRunId, timestamp, cgmFile, dcCgmFile, cbcoraFile, glskFile, refProgFile, raoRequestFile, virtualHubFile, true, taskParameterList);

        final CgmsAndXmlHeader cgmsAndXmlHeader = Mockito.mock(CgmsAndXmlHeader.class);
        final Path pathToCgm = Path.of("/path/to/cgm");
        cgmNetwork = Mockito.mock(Network.class);
        final CgmsAndXmlHeader dcCgmsAndXmlHeader = Mockito.mock(CgmsAndXmlHeader.class);
        final Path pathToDcCgm = Path.of("/path/to/dcCgm");
        dcCgmNetwork = Mockito.mock(Network.class);

        raoParameters = Mockito.mock(RaoParameters.class);

        final VariantManager variantManager = Mockito.mock(VariantManager.class);
        Mockito.when(cgmNetwork.getVariantManager()).thenReturn(variantManager);
        Mockito.when(variantManager.getWorkingVariantId()).thenReturn(INITIAL_VARIANT_ID);

        final HourlyRaoRequest continentalHourlyRaoRequest = Mockito.mock(HourlyRaoRequest.class);
        Mockito.when(continentalHourlyRaoRequest.getRaoRequestInstant()).thenReturn(INSTANT);

        coreNetworkImporterWrapperMockedStatic = Mockito.mockStatic(CoreNetworkImporterWrapper.class);
        coreNetworkImporterWrapperMockedStatic.when(() -> CoreNetworkImporterWrapper.loadNetwork(pathToCgm)).thenReturn(cgmNetwork);
        coreNetworkImporterWrapperMockedStatic.when(() -> CoreNetworkImporterWrapper.loadNetwork(pathToDcCgm)).thenReturn(dcCgmNetwork);

        Mockito.when(fileImporter.importCgmsZip(cgmFile)).thenReturn(cgmsAndXmlHeader);
        Mockito.when(cgmsAndXmlHeader.getNetworkPath(Mockito.any())).thenReturn(pathToCgm);
        Mockito.when(fileImporter.importCgmsZip(dcCgmFile)).thenReturn(dcCgmsAndXmlHeader);
        Mockito.when(dcCgmsAndXmlHeader.getNetworkPath(Mockito.any())).thenReturn(pathToDcCgm);
        Mockito.when(fileImporter.importRaoParameters(raoParametersFile.url())).thenReturn(raoParameters);

        internalCoreCCRequest = new InternalCoreCCRequest(coreCCRequest);
        internalCoreCCRequest.setContinentalHourlyRaoRequest(continentalHourlyRaoRequest);
        internalCoreCCRequest.setRaoParameters(raoParametersFile);
    }

    @AfterEach
    void cleanUp() throws Exception {
        coreNetworkImporterWrapperMockedStatic.close();
        openMocks.close();
    }

    @Test
    void testWithException() {
        final CoreCCRequest coreCCRequest = new CoreCCRequest("id", null, null, null, null, null, null, null, null, null,  true, List.of());
        internalCoreCCRequest = new InternalCoreCCRequest(coreCCRequest);
        final HourlyRaoResult continentalHourlyRaoResult = new HourlyRaoResult(INSTANT);
        internalCoreCCRequest.setContinentalHourlyRaoResult(continentalHourlyRaoResult);
        final HourlyRaoResult semHourlyRaoResult = new HourlyRaoResult(INSTANT);
        internalCoreCCRequest.setSemHourlyRaoResult(semHourlyRaoResult);

        postProcessService.convertAndSaveReceivedRaoResult(internalCoreCCRequest);

        Mockito.verifyNoInteractions(fileExporterHelper);
        Assertions.assertThat(continentalHourlyRaoResult.getStatus()).isEqualTo(HourlyRaoResultStatus.FAILURE);
        Assertions.assertThat(semHourlyRaoResult.getStatus()).isEqualTo(HourlyRaoResultStatus.FAILURE);
    }

    @Test
    void testWithContinentalDataOnly() {
        final HourlyRaoResult continentalHourlyRaoResult = Mockito.mock(HourlyRaoResult.class);
        internalCoreCCRequest.setContinentalHourlyRaoResult(continentalHourlyRaoResult);
        final String pathToRaoResult = "/path/to/raoResult";
        Mockito.when(continentalHourlyRaoResult.getRaoResultFileUrl()).thenReturn(pathToRaoResult);

        final HourlyRaoRequest continentalHourlyRaoRequest = internalCoreCCRequest.getContinentalHourlyRaoRequest();
        Mockito.when(continentalHourlyRaoRequest.getNetworkFileUrl()).thenReturn("continentalNetworkUrl");
        final Network continentalNetwork = Mockito.mock(Network.class);
        Mockito.when(fileImporter.importNetwork("continentalNetworkUrl")).thenReturn(continentalNetwork);
        final VirtualHubsConfiguration virtualHubsConfiguration = Mockito.mock(VirtualHubsConfiguration.class);
        Mockito.when(fileImporter.importVirtualHubs(virtualHubFile)).thenReturn(virtualHubsConfiguration);
        final FbConstraintCreationContext continentalFbConstraintCreationContext = Mockito.mock(FbConstraintCreationContext.class);
        Mockito.when(fileImporter.importCbcora(cbcoraFile.url(), timestamp, continentalNetwork, virtualHubsConfiguration)).thenReturn(continentalFbConstraintCreationContext);
        final Crac continentalCrac = Mockito.mock(Crac.class);
        Mockito.when(continentalFbConstraintCreationContext.getCrac()).thenReturn(continentalCrac);
        final RaoResult raoResult = Mockito.mock(RaoResult.class);
        Mockito.when(fileImporter.importRaoResult(pathToRaoResult, continentalCrac)).thenReturn(raoResult);

        postProcessService.convertAndSaveReceivedRaoResult(internalCoreCCRequest);

        final ArgumentCaptor<CoreCCPostProcessingData> coreCCPostProcessingDataArgumentCaptor = ArgumentCaptor.forClass(CoreCCPostProcessingData.class);
        Mockito.verify(fileExporterHelper, Mockito.times(1)).exportCneToMinio(coreCCPostProcessingDataArgumentCaptor.capture());
        Mockito.verify(fileExporterHelper, Mockito.times(1)).exportNetworkToMinio(coreCCPostProcessingDataArgumentCaptor.getValue());
        Mockito.verify(fileExporterHelper, Mockito.times(1)).exportRaoResultToMinio(coreCCPostProcessingDataArgumentCaptor.getValue());
        Mockito.verify(fileExporterHelper, Mockito.times(1)).exportMetadataToMinio(coreCCPostProcessingDataArgumentCaptor.getValue());

        Assertions.assertThat(coreCCPostProcessingDataArgumentCaptor.getValue().getRequest()).isEqualTo(internalCoreCCRequest);
        Assertions.assertThat(coreCCPostProcessingDataArgumentCaptor.getValue().getAcNetwork()).isEqualTo(cgmNetwork);
        Assertions.assertThat(coreCCPostProcessingDataArgumentCaptor.getValue().getInitialAcNetworkVariantId()).isEqualTo(INITIAL_VARIANT_ID);
        Assertions.assertThat(coreCCPostProcessingDataArgumentCaptor.getValue().getDcNetwork()).isEqualTo(dcCgmNetwork);
        Assertions.assertThat(coreCCPostProcessingDataArgumentCaptor.getValue().getContinentalCracCreationContext()).isEqualTo(continentalFbConstraintCreationContext);
        Assertions.assertThat(coreCCPostProcessingDataArgumentCaptor.getValue().getSemCracCreationContext()).isNull();
        Assertions.assertThat(coreCCPostProcessingDataArgumentCaptor.getValue().getCracCreationContext()).isEqualTo(continentalFbConstraintCreationContext);
        Assertions.assertThat(coreCCPostProcessingDataArgumentCaptor.getValue().getRaoResult()).isEqualTo(raoResult);
    }

    @Test
    void testWithContinentalAndSemData() {
        internalCoreCCRequest.setSemEnabled(true);

        final VirtualHubsConfiguration virtualHubsConfiguration = Mockito.mock(VirtualHubsConfiguration.class);
        Mockito.when(fileImporter.importVirtualHubs(virtualHubFile)).thenReturn(virtualHubsConfiguration);

        final FbConstraintCreationContext fullFbConstraintCreationContext = Mockito.mock(FbConstraintCreationContext.class);
        Mockito.when(fileImporter.importCbcora(cbcoraFile.url(), timestamp, dcCgmNetwork, virtualHubsConfiguration)).thenReturn(fullFbConstraintCreationContext);
        final Crac fullCrac = Mockito.mock(Crac.class);
        Mockito.when(fullFbConstraintCreationContext.getCrac()).thenReturn(fullCrac);

        final HourlyRaoResult continentalHourlyRaoResult = Mockito.mock(HourlyRaoResult.class);
        internalCoreCCRequest.setContinentalHourlyRaoResult(continentalHourlyRaoResult);
        final String pathToContinentalRaoResult = "/path/to/continental/raoResult";
        Mockito.when(continentalHourlyRaoResult.getRaoResultFileUrl()).thenReturn(pathToContinentalRaoResult);
        Mockito.when(continentalHourlyRaoResult.getStatus()).thenReturn(HourlyRaoResultStatus.SUCCESS);

        final HourlyRaoResult semHourlyRaoResult = Mockito.mock(HourlyRaoResult.class);
        internalCoreCCRequest.setSemHourlyRaoResult(semHourlyRaoResult);
        final String pathToSemRaoResult = "/path/to/sem/raoResult";
        Mockito.when(semHourlyRaoResult.getRaoResultFileUrl()).thenReturn(pathToSemRaoResult);
        Mockito.when(semHourlyRaoResult.getStatus()).thenReturn(HourlyRaoResultStatus.SUCCESS);

        final HourlyRaoRequest continentalHourlyRaoRequest = internalCoreCCRequest.getContinentalHourlyRaoRequest();
        Mockito.when(continentalHourlyRaoRequest.getNetworkFileUrl()).thenReturn("continentalNetworkUrl");

        final HourlyRaoRequest semHourlyRaoRequest = Mockito.mock(HourlyRaoRequest.class);
        Mockito.when(semHourlyRaoRequest.getRaoRequestInstant()).thenReturn(INSTANT);
        internalCoreCCRequest.setSemHourlyRaoRequest(semHourlyRaoRequest);
        Mockito.when(semHourlyRaoRequest.getNetworkFileUrl()).thenReturn("semNetworkUrl");

        final Network continentalNetwork = Mockito.mock(Network.class);
        Mockito.when(fileImporter.importNetwork("continentalNetworkUrl")).thenReturn(continentalNetwork);
        final FbConstraintCreationContext continentalFbConstraintCreationContext = Mockito.mock(FbConstraintCreationContext.class);
        Mockito.when(fileImporter.importCbcora(cbcoraFile.url(), timestamp, continentalNetwork, virtualHubsConfiguration)).thenReturn(continentalFbConstraintCreationContext);
        final Crac continentalCrac = Mockito.mock(Crac.class);
        Mockito.when(continentalFbConstraintCreationContext.getCrac()).thenReturn(continentalCrac);
        final RaoResult continentalRaoResult = Mockito.mock(RaoResult.class);
        Mockito.when(fileImporter.importRaoResult(pathToContinentalRaoResult, continentalCrac)).thenReturn(continentalRaoResult);

        final Network semNetwork = Mockito.mock(Network.class);
        Mockito.when(fileImporter.importNetwork("semNetworkUrl")).thenReturn(semNetwork);
        final FbConstraintCreationContext semFbConstraintCreationContext = Mockito.mock(FbConstraintCreationContext.class);
        Mockito.when(fileImporter.importCbcora(cbcoraFile.url(), timestamp, semNetwork, virtualHubsConfiguration)).thenReturn(semFbConstraintCreationContext);
        final Crac semCrac = Mockito.mock(Crac.class);
        Mockito.when(semFbConstraintCreationContext.getCrac()).thenReturn(semCrac);
        final RaoResult semRaoResult = Mockito.mock(RaoResult.class);
        Mockito.when(fileImporter.importRaoResult(pathToSemRaoResult, semCrac)).thenReturn(semRaoResult);

        final ZonalData zonalData = Mockito.mock(ZonalData.class);
        Mockito.when(fileImporter.importGlsk("http://path/to/glsk/file", timestamp, dcCgmNetwork)).thenReturn(zonalData);
        final ReferenceProgram referenceProgram = Mockito.mock(ReferenceProgram.class);
        Mockito.when(fileImporter.importReferenceProgram(refProgFile, timestamp)).thenReturn(referenceProgram);

        final RaoResult mergedRaoResult = Mockito.mock(RaoResult.class);
        Mockito.when(
            raoResultMerger.mergeRaoResults(continentalRaoResult, semRaoResult, cgmNetwork, fullCrac, referenceProgram, zonalData, raoParameters, ReportNode.NO_OP, INITIAL_VARIANT_ID)
        ).thenReturn(mergedRaoResult);

        postProcessService.convertAndSaveReceivedRaoResult(internalCoreCCRequest);

        Mockito.verify(raoResultMerger, Mockito.times(1)).mergeRaoResults(
            continentalRaoResult, semRaoResult, cgmNetwork, fullCrac, referenceProgram, zonalData, raoParameters, ReportNode.NO_OP, INITIAL_VARIANT_ID
        );
        final ArgumentCaptor<CoreCCPostProcessingData> coreCCPostProcessingDataArgumentCaptor = ArgumentCaptor.forClass(CoreCCPostProcessingData.class);
        Mockito.verify(fileExporterHelper, Mockito.times(1)).exportCneToMinio(coreCCPostProcessingDataArgumentCaptor.capture());
        Mockito.verify(fileExporterHelper, Mockito.times(1)).exportNetworkToMinio(coreCCPostProcessingDataArgumentCaptor.getValue());
        Mockito.verify(fileExporterHelper, Mockito.times(1)).exportRaoResultToMinio(coreCCPostProcessingDataArgumentCaptor.getValue());
        Mockito.verify(fileExporterHelper, Mockito.times(1)).exportMetadataToMinio(coreCCPostProcessingDataArgumentCaptor.getValue());

        Assertions.assertThat(coreCCPostProcessingDataArgumentCaptor.getValue().getRequest()).isEqualTo(internalCoreCCRequest);
        Assertions.assertThat(coreCCPostProcessingDataArgumentCaptor.getValue().getAcNetwork()).isEqualTo(cgmNetwork);
        Assertions.assertThat(coreCCPostProcessingDataArgumentCaptor.getValue().getInitialAcNetworkVariantId()).isEqualTo(INITIAL_VARIANT_ID);
        Assertions.assertThat(coreCCPostProcessingDataArgumentCaptor.getValue().getDcNetwork()).isEqualTo(dcCgmNetwork);
        Assertions.assertThat(coreCCPostProcessingDataArgumentCaptor.getValue().getContinentalCracCreationContext()).isEqualTo(continentalFbConstraintCreationContext);
        Assertions.assertThat(coreCCPostProcessingDataArgumentCaptor.getValue().getSemCracCreationContext()).isEqualTo(semFbConstraintCreationContext);
        Assertions.assertThat(coreCCPostProcessingDataArgumentCaptor.getValue().getCracCreationContext()).isEqualTo(fullFbConstraintCreationContext);
        Assertions.assertThat(coreCCPostProcessingDataArgumentCaptor.getValue().getRaoResult()).isEqualTo(mergedRaoResult);
    }

    @Test
    void testWithSemDataAndFailedContinentalData() {
        internalCoreCCRequest.setSemEnabled(true);

        final VirtualHubsConfiguration virtualHubsConfiguration = Mockito.mock(VirtualHubsConfiguration.class);
        Mockito.when(fileImporter.importVirtualHubs(virtualHubFile)).thenReturn(virtualHubsConfiguration);

        final HourlyRaoResult continentalHourlyRaoResult = Mockito.mock(HourlyRaoResult.class);
        internalCoreCCRequest.setContinentalHourlyRaoResult(continentalHourlyRaoResult);
        Mockito.when(continentalHourlyRaoResult.getStatus()).thenReturn(HourlyRaoResultStatus.FAILURE);

        final HourlyRaoResult semHourlyRaoResult = Mockito.mock(HourlyRaoResult.class);
        internalCoreCCRequest.setSemHourlyRaoResult(semHourlyRaoResult);
        final String pathToSemRaoResult = "/path/to/sem/raoResult";
        Mockito.when(semHourlyRaoResult.getRaoResultFileUrl()).thenReturn(pathToSemRaoResult);
        Mockito.when(semHourlyRaoResult.getStatus()).thenReturn(HourlyRaoResultStatus.SUCCESS);

        final HourlyRaoRequest continentalHourlyRaoRequest = internalCoreCCRequest.getContinentalHourlyRaoRequest();
        Mockito.when(continentalHourlyRaoRequest.getNetworkFileUrl()).thenReturn("continentalNetworkUrl");

        final HourlyRaoRequest semHourlyRaoRequest = Mockito.mock(HourlyRaoRequest.class);
        Mockito.when(semHourlyRaoRequest.getRaoRequestInstant()).thenReturn(INSTANT);
        internalCoreCCRequest.setSemHourlyRaoRequest(semHourlyRaoRequest);
        Mockito.when(semHourlyRaoRequest.getNetworkFileUrl()).thenReturn("semNetworkUrl");

        final Network semNetwork = Mockito.mock(Network.class);
        Mockito.when(fileImporter.importNetwork("semNetworkUrl")).thenReturn(semNetwork);
        final FbConstraintCreationContext semFbConstraintCreationContext = Mockito.mock(FbConstraintCreationContext.class);
        Mockito.when(fileImporter.importCbcora(cbcoraFile.url(), timestamp, semNetwork, virtualHubsConfiguration)).thenReturn(semFbConstraintCreationContext);
        final Crac semCrac = Mockito.mock(Crac.class);
        Mockito.when(semFbConstraintCreationContext.getCrac()).thenReturn(semCrac);
        final RaoResult semRaoResult = Mockito.mock(RaoResult.class);
        Mockito.when(fileImporter.importRaoResult(pathToSemRaoResult, semCrac)).thenReturn(semRaoResult);

        postProcessService.convertAndSaveReceivedRaoResult(internalCoreCCRequest);

        final ArgumentCaptor<CoreCCPostProcessingData> coreCCPostProcessingDataArgumentCaptor = ArgumentCaptor.forClass(CoreCCPostProcessingData.class);
        Mockito.verify(fileExporterHelper, Mockito.times(1)).exportCneToMinio(coreCCPostProcessingDataArgumentCaptor.capture());
        Mockito.verify(fileExporterHelper, Mockito.times(1)).exportNetworkToMinio(coreCCPostProcessingDataArgumentCaptor.getValue());
        Mockito.verify(fileExporterHelper, Mockito.times(1)).exportRaoResultToMinio(coreCCPostProcessingDataArgumentCaptor.getValue());
        Mockito.verify(fileExporterHelper, Mockito.times(1)).exportMetadataToMinio(coreCCPostProcessingDataArgumentCaptor.getValue());

        Assertions.assertThat(coreCCPostProcessingDataArgumentCaptor.getValue().getRequest()).isEqualTo(internalCoreCCRequest);
        Assertions.assertThat(coreCCPostProcessingDataArgumentCaptor.getValue().getAcNetwork()).isEqualTo(cgmNetwork);
        Assertions.assertThat(coreCCPostProcessingDataArgumentCaptor.getValue().getInitialAcNetworkVariantId()).isEqualTo(INITIAL_VARIANT_ID);
        Assertions.assertThat(coreCCPostProcessingDataArgumentCaptor.getValue().getDcNetwork()).isEqualTo(dcCgmNetwork);
        Assertions.assertThat(coreCCPostProcessingDataArgumentCaptor.getValue().getContinentalCracCreationContext()).isNull();
        Assertions.assertThat(coreCCPostProcessingDataArgumentCaptor.getValue().getSemCracCreationContext()).isEqualTo(semFbConstraintCreationContext);
        Assertions.assertThat(coreCCPostProcessingDataArgumentCaptor.getValue().getCracCreationContext()).isEqualTo(semFbConstraintCreationContext);
        Assertions.assertThat(coreCCPostProcessingDataArgumentCaptor.getValue().getRaoResult()).isEqualTo(semRaoResult);
    }
}
