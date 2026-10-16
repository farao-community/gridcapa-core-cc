/*
 * Copyright (c) 2024, RTE (http://www.rte-france.com)
 *  This Source Code Form is subject to the terms of the Mozilla Public
 *  License, v. 2.0. If a copy of the MPL was not distributed with this
 *  file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package com.farao_community.farao.gridcapa_core_cc.app.preprocessing;

import com.farao_community.farao.gridcapa.task_manager.api.TaskParameterDto;
import com.farao_community.farao.gridcapa_core_cc.api.resource.CoreCCFileResource;
import com.farao_community.farao.gridcapa_core_cc.api.resource.CoreCCRequest;
import com.farao_community.farao.gridcapa_core_cc.api.resource.HourlyRaoRequest;
import com.farao_community.farao.gridcapa_core_cc.api.resource.HourlyRaoResult;
import com.farao_community.farao.gridcapa_core_cc.api.resource.HourlyRaoResultErrorCode;
import com.farao_community.farao.gridcapa_core_cc.api.resource.HourlyRaoResultStatus;
import com.farao_community.farao.gridcapa_core_cc.api.resource.InternalCoreCCRequest;
import com.farao_community.farao.gridcapa_core_cc.app.domain.CoreCCTaskParameters;
import com.farao_community.farao.gridcapa_core_cc.app.entities.CgmsAndXmlHeader;
import com.farao_community.farao.gridcapa_core_cc.app.inputs.rao_request.Header;
import com.farao_community.farao.gridcapa_core_cc.app.inputs.rao_request.Payload;
import com.farao_community.farao.gridcapa_core_cc.app.inputs.rao_request.RequestMessage;
import com.farao_community.farao.gridcapa_core_cc.app.inputs.rao_response.ResponseMessage;
import com.farao_community.farao.gridcapa_core_cc.app.services.FileImporter;
import com.farao_community.farao.gridcapa_core_cc.app.util.NamingRules;
import com.farao_community.farao.minio_adapter.starter.MinioAdapter;
import com.powsybl.iidm.network.Network;
import com.powsybl.openrao.data.crac.api.Crac;
import com.powsybl.openrao.data.crac.api.parameters.CracCreationParameters;
import com.powsybl.openrao.data.crac.io.fbconstraint.FbConstraintCreationContext;
import com.powsybl.openrao.data.crac.io.fbconstraint.FbConstraintImporter;
import com.powsybl.openrao.data.crac.io.fbconstraint.parameters.FbConstraintCracCreationParameters;
import com.powsybl.openrao.virtualhubs.MarketArea;
import com.powsybl.openrao.virtualhubs.VirtualHub;
import com.powsybl.openrao.virtualhubs.VirtualHubsConfiguration;
import com.unicorn.request.request_payload.RequestItem;
import com.unicorn.request.request_payload.RequestItems;
import com.unicorn.response.response_payload.File;
import com.unicorn.response.response_payload.Files;
import com.unicorn.response.response_payload.ResponseItem;
import com.unicorn.response.response_payload.ResponseItems;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedConstruction;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.slf4j.Logger;

import javax.xml.datatype.DatatypeConfigurationException;
import javax.xml.datatype.DatatypeFactory;
import javax.xml.datatype.XMLGregorianCalendar;
import java.io.ByteArrayInputStream;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * @author Daniel Thirion {@literal <daniel.thirion at rte-france.com>}
 * @author Vincent Bochet {@literal <vincent.bochet at rte-france.com>}
 */
class CoreCCPreProcessServiceTest {

    public static final String TIME_INTERVAL_6_7 = "2024-06-25T06:00:00Z/2024-06-25T07:00:00Z";
    public static final OffsetDateTime REQUEST_TIMESTAMP = OffsetDateTime.parse("2024-06-25T06:00:00Z");
    private final String networkFile = "/util/TestCase12NodesHvdc.uct";
    private final String dcNetworkFile = "/util/DCTestCase12NodesHvdc.uct";
    private final Path networkPath = Paths.get(getClass().getResource(networkFile).getPath());
    private final Path dcNetworkPath = Paths.get(getClass().getResource(dcNetworkFile).getPath());
    private final Network network = Network.read(networkPath);

    @InjectMocks
    private CoreCCPreProcessService coreCCPreProcessService;

    @Mock
    private Logger businessLogger;

    @Mock
    private FileImporter fileImporter;

    @Mock
    private MinioAdapter minioAdapter;

    @Mock
    private RaoParametersService raoParametersService;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
    }

    @Test
    void testInitializeTaskFromRequestWithNoTimestamp() {
        // Setup CoreCCRequest and InternalCoreCCRequest
        final CoreCCFileResource dummyFileResource = mock(CoreCCFileResource.class);
        when(dummyFileResource.url()).thenReturn("fakeUrl");
        final CoreCCFileResource dummyVirtualHubsFileResource = mock(CoreCCFileResource.class);
        final VirtualHubsConfiguration virtualHubsMock = mock(VirtualHubsConfiguration.class);
        Mockito.when(fileImporter.importVirtualHubs(dummyVirtualHubsFileResource)).thenReturn(virtualHubsMock);
        Mockito.when(raoParametersService.uploadJsonRaoParameters(any(), eq(virtualHubsMock), any()))
            .thenReturn("/path/to/virtualhubs_failed_url.xml");
        final TaskParameterDto taskParameterDto = new TaskParameterDto("USE_DC_CGM_INPUT", "BOOLEAN", "FALSE", "FALSE");
        final CoreCCRequest coreCCRequestEntity = new CoreCCRequest(
            "id", "current RunID", OffsetDateTime.now(),
            dummyFileResource, dummyFileResource, dummyFileResource, dummyFileResource, dummyFileResource, dummyFileResource,
            dummyVirtualHubsFileResource,
            List.of(taskParameterDto)
        );
        final InternalCoreCCRequest coreCCRequest = new InternalCoreCCRequest(coreCCRequestEntity);

        prepareAndMockRaoRequest(dummyFileResource);
        // mock cgms and xml header
        prepareAndMockCgmsAndXmlHeader(dummyFileResource, false, null, null);

        coreCCPreProcessService.initializeTaskFromAutomatedLaunch(coreCCRequest);

        verifyDefaultRaoRequest(coreCCRequest);
        Assertions.assertThat(coreCCRequest.getContinentalHourlyRaoRequest()).isNotNull();
        Assertions.assertThat(coreCCRequest.getContinentalHourlyRaoResult()).isNotNull();
        Assertions.assertThat(coreCCRequest.getContinentalHourlyRaoResult().getErrorMessage()).isEqualTo("Missing raoRequest");
        Assertions.assertThat(coreCCRequest.isSemEnabled()).isFalse();
    }

    @Test
    void testInitializeTaskFromRequestWithCgmInput() throws IOException {
        // Setup CoreCCRequest and InternalCoreCCRequest
        final CoreCCFileResource dummyFileResource = mock(CoreCCFileResource.class);
        when(dummyFileResource.url()).thenReturn("fakeUrl");
        final CoreCCFileResource dummyVirtualHubsFileResource = mock(CoreCCFileResource.class);
        final VirtualHubsConfiguration virtualHubsMock = mock(VirtualHubsConfiguration.class);
        Mockito.when(fileImporter.importVirtualHubs(dummyVirtualHubsFileResource)).thenReturn(virtualHubsMock);
        final TaskParameterDto taskParameterDto = new TaskParameterDto("USE_DC_CGM_INPUT", "BOOLEAN", "FALSE", "FALSE");
        final CoreCCRequest coreCCRequestEntity = new CoreCCRequest(
            "id", "current RunID", REQUEST_TIMESTAMP,
            dummyFileResource, null, dummyFileResource, dummyFileResource, dummyFileResource, dummyFileResource,
            dummyVirtualHubsFileResource,
            List.of(taskParameterDto)
        );
        final InternalCoreCCRequest coreCCRequest = new InternalCoreCCRequest(coreCCRequestEntity);
        //
        prepareAndMockRaoRequest(dummyFileResource);
        // mock imports
        final String raoParametersFileUrl = "raoParametersFileUrl";
        when(raoParametersService.uploadJsonRaoParameters(any(), any(), any())).thenReturn(raoParametersFileUrl);
        // mock cgms and xml header
        prepareAndMockCgmsAndXmlHeader(dummyFileResource, true, networkFile, networkPath);

        // mock crac
        mockCrac();
        // When
        coreCCPreProcessService.initializeTaskFromAutomatedLaunch(coreCCRequest);
        //
        verifyNominalHourlyData(coreCCRequest, raoParametersFileUrl);
        Assertions.assertThat(coreCCRequest.getContinentalHourlyRaoRequest()).isNotNull();
        Assertions.assertThat(coreCCRequest.getContinentalHourlyRaoResult()).isNull();
        Assertions.assertThat(coreCCRequest.isSemEnabled()).isFalse();
    }

    @Test
    void testInitializeTaskFromRequestWithDCCgmInput() throws IOException {
        // Setup CoreCCRequest and InternalCoreCCRequest
        final CoreCCFileResource dummyDCCgmFileResource = mock(CoreCCFileResource.class);
        when(dummyDCCgmFileResource.url()).thenReturn("dcCgmfakeUrl");
        final CoreCCFileResource dummyFileResource = mock(CoreCCFileResource.class);
        when(dummyFileResource.url()).thenReturn("fakeUrl");
        final CoreCCFileResource dummyVirtualHubsFileResource = mock(CoreCCFileResource.class);
        final VirtualHubsConfiguration virtualHubsMock = mock(VirtualHubsConfiguration.class);
        Mockito.when(fileImporter.importVirtualHubs(dummyVirtualHubsFileResource)).thenReturn(virtualHubsMock);
        final CoreCCRequest coreCCRequestEntity = new CoreCCRequest(
            "id", "current RunID", REQUEST_TIMESTAMP,
            dummyFileResource, dummyDCCgmFileResource, dummyFileResource, dummyFileResource, dummyFileResource, dummyFileResource,
            dummyVirtualHubsFileResource,
            createTaskParametersList(true)
        );
        final InternalCoreCCRequest coreCCRequest = new InternalCoreCCRequest(coreCCRequestEntity);
        prepareAndMockRaoRequest(dummyFileResource);
        // mock imports
        final String raoParametersFileUrl = "raoParametersFileUrl";
        when(raoParametersService.uploadJsonRaoParameters(any(), any(), any())).thenReturn(raoParametersFileUrl);
        // mock cgms and xml header
        prepareAndMockCgmsAndXmlHeader(dummyFileResource, true, networkFile, networkPath);
        prepareAndMockCgmsAndXmlHeader(dummyDCCgmFileResource, true, dcNetworkFile, dcNetworkPath);
        // mock crac
        mockCrac();
        //
        coreCCPreProcessService.initializeTaskFromAutomatedLaunch(coreCCRequest);
        //
        verifyNominalHourlyData(coreCCRequest, raoParametersFileUrl);
        Assertions.assertThat(coreCCRequest.getContinentalHourlyRaoRequest()).isNotNull();
        Assertions.assertThat(coreCCRequest.getContinentalHourlyRaoResult()).isNull();
        Assertions.assertThat(coreCCRequest.isSemEnabled()).isFalse();
    }

    @Test
    void testInitializeTaskFromRequestWithSemEnabled() throws IOException {
        // Setup CoreCCRequest and InternalCoreCCRequest
        final CoreCCFileResource dummyFileResource = mock(CoreCCFileResource.class);
        when(dummyFileResource.url()).thenReturn("fakeUrl");
        final CoreCCFileResource dummyVirtualHubsFileResource = mock(CoreCCFileResource.class);
        final VirtualHubsConfiguration virtualHubsMock = mock(VirtualHubsConfiguration.class);
        Mockito.when(fileImporter.importVirtualHubs(dummyVirtualHubsFileResource)).thenReturn(virtualHubsMock);
        final MarketArea marketArea = new MarketArea("SEM", "xxx", true, false);
        Mockito.when(virtualHubsMock.getMarketAreas()).thenReturn(List.of(marketArea));
        final VirtualHub virtualHub = new VirtualHub("SEM_CI", "xxx", true, false, "", marketArea, null);
        Mockito.when(virtualHubsMock.getVirtualHubs()).thenReturn(List.of(virtualHub));
        final TaskParameterDto taskParameterDto = new TaskParameterDto("USE_DC_CGM_INPUT", "BOOLEAN", "FALSE", "FALSE");
        final CoreCCRequest coreCCRequestEntity = new CoreCCRequest(
            "id", "current RunID", REQUEST_TIMESTAMP,
            dummyFileResource, null, dummyFileResource, dummyFileResource, dummyFileResource, dummyFileResource,
            dummyVirtualHubsFileResource,
            List.of(taskParameterDto)
        );
        final InternalCoreCCRequest coreCCRequest = new InternalCoreCCRequest(coreCCRequestEntity);
        //
        prepareAndMockRaoRequest(dummyFileResource);
        // mock imports
        final String raoParametersFileUrl = "raoParametersFileUrl";
        when(raoParametersService.uploadJsonRaoParameters(any(), any(), any())).thenReturn(raoParametersFileUrl);
        // mock cgms and xml header
        prepareAndMockCgmsAndXmlHeader(dummyFileResource, true, networkFile, networkPath);

        // mock crac
        mockCrac();
        // When
        coreCCPreProcessService.initializeTaskFromAutomatedLaunch(coreCCRequest);
        //
        verifyNominalHourlyData(coreCCRequest, raoParametersFileUrl);
        Assertions.assertThat(coreCCRequest.getContinentalHourlyRaoRequest()).isNotNull();
        Assertions.assertThat(coreCCRequest.getContinentalHourlyRaoResult()).isNull();
        Assertions.assertThat(coreCCRequest.isSemEnabled()).isTrue();
        Assertions.assertThat(coreCCRequest.getSemHourlyRaoRequest()).isNotNull();
        Assertions.assertThat(coreCCRequest.getSemHourlyRaoResult()).isNull();
    }

    @Test
    void testInitializeTaskFromRequestWhenExceptionThrown() {
        // Setup CoreCCRequest and InternalCoreCCRequest
        final CoreCCFileResource dummyFileResource = mock(CoreCCFileResource.class);
        when(dummyFileResource.url()).thenReturn("fakeUrl");
        final CoreCCFileResource dummyVirtualHubsFileResource = mock(CoreCCFileResource.class);
        final VirtualHubsConfiguration virtualHubsMock = mock(VirtualHubsConfiguration.class);
        Mockito.when(fileImporter.importVirtualHubs(dummyVirtualHubsFileResource)).thenReturn(virtualHubsMock);
        final CoreCCRequest coreCCRequestEntity = new CoreCCRequest(
            "id", "current RunID", REQUEST_TIMESTAMP,
            dummyFileResource, dummyFileResource, dummyFileResource, dummyFileResource, dummyFileResource, dummyFileResource,
            dummyVirtualHubsFileResource,
            createTaskParametersList(true)
        );
        final InternalCoreCCRequest coreCCRequest = new InternalCoreCCRequest(coreCCRequestEntity);
        prepareAndMockRaoRequest(dummyFileResource);
        // mock imports
        final String raoParametersFileUrl = "raoParametersFileUrl";
        when(raoParametersService.uploadJsonRaoParameters(any(), any(), any())).thenReturn(raoParametersFileUrl);
        // mock cgms and xml header
        prepareAndMockCgmsAndXmlHeader(dummyFileResource, true, networkFile, networkPath);
        // When
        coreCCPreProcessService.initializeTaskFromAutomatedLaunch(coreCCRequest);
        //
        verifyDefaultRaoRequest(coreCCRequest);
        Assertions.assertThat(coreCCRequest.getContinentalHourlyRaoResult().getErrorMessage())
            .isEqualTo("Error occurred while trying to import inputs at timestamp: 2024-06-25T06:00:00Z. Origin cause : Exception occurred while importing CRAC file: null");
        Assertions.assertThat(coreCCRequest.getContinentalHourlyRaoResult().getRaoRequestInstant()).isEqualTo("2024-06-25T06:00:00Z");
    }

    private List<TaskParameterDto> createTaskParametersList(final boolean parameter) {
        final TaskParameterDto parameterDto = new TaskParameterDto(
            "USE_DC_CGM_INPUT",
            "BOOLEAN",
            parameter ? "TRUE" : "FALSE",
            "FALSE"
        );
        return Collections.singletonList(parameterDto);
    }

    private void mockCrac() throws IOException {
        final Path cracJsonFilePath = Paths.get(getClass().getResource("/util/crac.json").getPath());
        final Crac crac = Crac.read(cracJsonFilePath.getFileName().toString(), java.nio.file.Files.newInputStream(cracJsonFilePath), network);
        final FbConstraintCreationContext cracCreationContext = Mockito.mock(FbConstraintCreationContext.class);
        when(fileImporter.importCbcora(any(), any(), any(), any())).thenReturn(cracCreationContext);
        when(cracCreationContext.getCrac()).thenReturn(crac);
    }

    private static void verifyNominalHourlyData(final InternalCoreCCRequest coreCCRequest,
                                                final String raoParametersFileUrl) {
        final HourlyRaoRequest continentalHourlyRaoRequest = coreCCRequest.getContinentalHourlyRaoRequest();
        Assertions.assertThat(continentalHourlyRaoRequest).isNotNull();
        Assertions.assertThat(continentalHourlyRaoRequest.getNetworkFileUrl()).isEqualTo("RAO_WORKING_DIR/20240625_0800/inputs/networks/continental_20240625_0600.xiidm");
        Assertions.assertThat(continentalHourlyRaoRequest.getCracFileUrl()).isEqualTo("RAO_WORKING_DIR/20240625_0800/inputs/cracs/continental_20240625_0600.json");
        Assertions.assertThat(continentalHourlyRaoRequest.getRaoParametersFileUrl()).isEqualTo(raoParametersFileUrl);
        Assertions.assertThat(continentalHourlyRaoRequest.getResultsDestination()).isEqualTo("RAO_WORKING_DIR/20240625_0800/hourly_rao_results/continental");
        //In nominal case, hourly rao result remains empty
        Assertions.assertThat(coreCCRequest.getContinentalHourlyRaoResult()).isNull();
        if (coreCCRequest.isSemEnabled()) {
            final HourlyRaoRequest semHourlyRaoRequest = coreCCRequest.getSemHourlyRaoRequest();
            Assertions.assertThat(semHourlyRaoRequest).isNotNull();
            Assertions.assertThat(semHourlyRaoRequest.getNetworkFileUrl()).isEqualTo("RAO_WORKING_DIR/20240625_0800/inputs/networks/sem_20240625_0600.xiidm");
            Assertions.assertThat(semHourlyRaoRequest.getCracFileUrl()).isEqualTo("RAO_WORKING_DIR/20240625_0800/inputs/cracs/sem_20240625_0600.json");
            Assertions.assertThat(semHourlyRaoRequest.getRaoParametersFileUrl()).isEqualTo(raoParametersFileUrl);
            Assertions.assertThat(semHourlyRaoRequest.getResultsDestination()).isEqualTo("RAO_WORKING_DIR/20240625_0800/hourly_rao_results/sem");
            //In nominal case, hourly rao result remains empty
            Assertions.assertThat(coreCCRequest.getContinentalHourlyRaoResult()).isNull();
        }
    }

    private static void verifyDefaultRaoRequest(final InternalCoreCCRequest coreCCRequest) {
        final HourlyRaoRequest hourlyRaoRequest = coreCCRequest.getContinentalHourlyRaoRequest();
        final HourlyRaoResult hourlyRaoResult = coreCCRequest.getContinentalHourlyRaoResult();
        Assertions.assertThat(hourlyRaoRequest).isNotNull();
        Assertions.assertThat(hourlyRaoResult).isNotNull();
        Assertions.assertThat(hourlyRaoResult.getStatus()).isEqualTo(HourlyRaoResultStatus.FAILURE);
        Assertions.assertThat(hourlyRaoResult.getErrorCodeString()).isEqualTo(HourlyRaoResultErrorCode.TS_PREPROCESSING_FAILURE.getCode());
        Assertions.assertThat(hourlyRaoResult.getRaoResultFileUrl()).isNull();
        Assertions.assertThat(hourlyRaoResult.getNetworkWithPraUrl()).isNull();
    }

    private void prepareAndMockCgmsAndXmlHeader(final CoreCCFileResource dummyFileResource,
                                                final boolean mockFiles,
                                                final String fileUrl,
                                                final Path dcNetworkPath) {
        final ResponseMessage responseMessage = new ResponseMessage();
        final com.farao_community.farao.gridcapa_core_cc.app.inputs.rao_response.Payload payloadResponse = new com.farao_community.farao.gridcapa_core_cc.app.inputs.rao_response.Payload();
        final ResponseItems responseItems = Mockito.mock(ResponseItems.class);
        responseMessage.setPayload(payloadResponse);

        final CgmsAndXmlHeader cgmsAndXmlHeader = new CgmsAndXmlHeader(responseMessage, mockFiles ? List.of(dcNetworkPath) : new ArrayList<>());
        when(fileImporter.importCgmsZip(dummyFileResource)).thenReturn(cgmsAndXmlHeader);
        payloadResponse.setResponseItems(responseItems);
        final List<ResponseItem> responseItemList = new ArrayList<>();
        if (mockFiles) {
            final ResponseItem responseItem = new ResponseItem();
            responseItem.setTimeInterval(TIME_INTERVAL_6_7);
            final Files files = Mockito.mock(Files.class);
            final File file = new File();
            file.setUrl(fileUrl);
            when(files.getFile()).thenReturn(List.of(file));
            responseItem.setFiles(files);
            responseItemList.add(responseItem);
        }
        when(responseItems.getResponseItem()).thenReturn(responseItemList);
        when(responseItems.getTimeInterval()).thenReturn(TIME_INTERVAL_6_7);
    }

    private void prepareAndMockRaoRequest(final CoreCCFileResource dummyFileResource) {
        final RequestMessage raoRequestMessage = new RequestMessage();
        final Payload payload = new Payload();
        final RequestItem requestItem = new RequestItem();
        requestItem.setTimeInterval(TIME_INTERVAL_6_7);
        final RequestItems requestItems = Mockito.mock(RequestItems.class);
        when(requestItems.getTimeInterval()).thenReturn(TIME_INTERVAL_6_7);
        when(requestItems.getRequestItem()).thenReturn(List.of(requestItem));
        payload.setRequestItems(requestItems);
        raoRequestMessage.setPayload(payload);
        final Header header = new Header();
        header.setCorrelationID("ID");
        raoRequestMessage.setHeader(header);
        Mockito.when(fileImporter.importRaoRequest(dummyFileResource.url())).thenReturn(raoRequestMessage);
    }

    @ParameterizedTest
    @CsvSource({"true", "false"})
    void getRequestAndResultFailureTest(final boolean isSem) {
        // Given
        final OffsetDateTime offsetDateTime = OffsetDateTime.of(2026, 10, 9, 0, 0, 0, 0, ZoneOffset.UTC);
        final Instant utcInstant = offsetDateTime.toInstant();
        final String destinationPath = "path/to/destination";

        // When
        final CoreCCPreProcessService.RequestResult requestAndResult = coreCCPreProcessService.getRequestAndResult(
            null,
            utcInstant,
            null, null, null, null, null, null,
            destinationPath,
            isSem
        );

        // Then
        Mockito.verify(minioAdapter, times(0)).uploadArtifact(any(), any());

        final String continentalOrSem = isSem ? "sem" : "continental";
        Assertions.assertThat(requestAndResult).isNotNull();
        Assertions.assertThat(requestAndResult.raoRequest())
            .isNotNull()
            .hasFieldOrPropertyWithValue("raoRequestInstant", "2026-10-09T00:00:00Z")
            .hasFieldOrPropertyWithValue("networkFileUrl", null)
            .hasFieldOrPropertyWithValue("cracFileUrl", null)
            .hasFieldOrPropertyWithValue("refprogFileUrl", null)
            .hasFieldOrPropertyWithValue("virtualHubsConfigurationFileUrl", null)
            .hasFieldOrPropertyWithValue("realGlskFileUrl", null)
            .hasFieldOrPropertyWithValue("raoParametersFileUrl", null)
            .hasFieldOrPropertyWithValue("resultsDestination", destinationPath + "/" + continentalOrSem);
        Assertions.assertThat(requestAndResult.raoResult())
            .isNotNull()
            .hasFieldOrPropertyWithValue("raoRequestInstant", "2026-10-09T00:00:00Z")
            .hasFieldOrPropertyWithValue("errorCode", HourlyRaoResultErrorCode.TS_PREPROCESSING_FAILURE)
            .hasFieldOrPropertyWithValue("errorMessage", "Error occurred while trying to import inputs at timestamp: 2026-10-09T00:00:00Z. Origin cause : Cannot invoke \"com.farao_community.farao.gridcapa_core_cc.app.domain.CoreCCTaskParameters.isUseDcCgmInput()\" because \"parameters\" is null")
            .hasFieldOrPropertyWithValue("status", HourlyRaoResultStatus.FAILURE);
    }

    @ParameterizedTest
    @CsvSource({"true", "false"})
    void getRequestAndResultTest(final boolean isSem) {
        // Given
        final OffsetDateTime offsetDateTime = OffsetDateTime.of(2026, 10, 9, 0, 0, 0, 0, ZoneOffset.UTC);
        final Network dcNetwork = Network.read("core_sem_fake_network.uct", getClass().getResourceAsStream("/sem-integration/core_sem_fake_network.uct"));
        final InternalCoreCCRequest coreCCRequest = Mockito.mock(InternalCoreCCRequest.class);
        Mockito.when(coreCCRequest.getCbcora()).thenReturn(new CoreCCFileResource("cbcora.xml", getClass().getResource("/sem-integration/cbcora.xml").getPath()));
        Mockito.when(coreCCRequest.getRefProg()).thenReturn(new CoreCCFileResource("refprog.xml", "/sem-integration/refprog.xml"));
        Mockito.when(coreCCRequest.getGlsk()).thenReturn(new CoreCCFileResource("glsk.xml", "/sem-integration/glsk.xml"));
        Mockito.when(coreCCRequest.getVirtualHub()).thenReturn(new CoreCCFileResource("virtualhub.xml", "/sem-integration/virtualhub.xml"));
        final CracCreationParameters cracCreationParameters = new CracCreationParameters();
        cracCreationParameters.addExtension(FbConstraintCracCreationParameters.class, new FbConstraintCracCreationParameters());
        cracCreationParameters.getExtension(FbConstraintCracCreationParameters.class).setTimestamp(offsetDateTime);

        final FbConstraintCreationContext cracCreationContext = (FbConstraintCreationContext) new FbConstraintImporter().importData(
            getClass().getResourceAsStream("/sem-integration/cbcora.xml"),
            cracCreationParameters,
            dcNetwork
        );
        Mockito.when(fileImporter.importCbcora(anyString(), any(), any(), any())).thenReturn(cracCreationContext);
        final Instant utcInstant = offsetDateTime.toInstant();
        final CgmsAndXmlHeader dcCgmsAndXmlHeader = Mockito.mock(CgmsAndXmlHeader.class);
        final Path dcCgmPath = Path.of(getClass().getResource("/sem-integration/core_sem_fake_network.uct").getPath());
        Mockito.when(dcCgmsAndXmlHeader.getNetworkPath(utcInstant)).thenReturn(dcCgmPath);
        final CgmsAndXmlHeader cgmsAndXmlHeader = null;
        final VirtualHubsConfiguration virtualHubsConfiguration = Mockito.mock(VirtualHubsConfiguration.class);
        final CoreCCTaskParameters parameters = new CoreCCTaskParameters(createTaskParametersList(true));
        final String destinationKey = "destinationKey";
        final String raoParametersFileUrl = "/sem-integration/rao-parameters.json";
        final String destinationPath = "path/to/destination";

        final AtomicReference<byte[]> semNetworkInputStreamBytes = new AtomicReference<>();
        final AtomicReference<byte[]> continentalNetworkInputStreamBytes = new AtomicReference<>();
        Mockito.doAnswer(invocation -> {
            final InputStream inputStream = invocation.getArgument(1);
            semNetworkInputStreamBytes.set(inputStream.readAllBytes());
            return null;
        }).when(minioAdapter).uploadArtifact(eq("destinationKey/inputs/networks/sem_20261009_0000.xiidm"), any());
        Mockito.doAnswer(invocation -> {
            final InputStream inputStream = invocation.getArgument(1);
            continentalNetworkInputStreamBytes.set(inputStream.readAllBytes());
            return null;
        }).when(minioAdapter).uploadArtifact(eq("destinationKey/inputs/networks/continental_20261009_0000.xiidm"), any());

        // When
        final CoreCCPreProcessService.RequestResult requestAndResult = coreCCPreProcessService.getRequestAndResult(
            coreCCRequest,
            utcInstant,
            dcCgmsAndXmlHeader,
            cgmsAndXmlHeader,
            virtualHubsConfiguration,
            parameters,
            destinationKey,
            raoParametersFileUrl,
            destinationPath,
            isSem
        );

        // Then
        Mockito.verify(minioAdapter, times(2)).uploadArtifact(any(), any());

        if (isSem) {
            Assertions.assertThat(continentalNetworkInputStreamBytes).hasNullValue();
            Assertions.assertThat(semNetworkInputStreamBytes).doesNotHaveNullValue();
            final Network exportedSemNetwork = Network.read("exportedSemNetwork.xiidm", new ByteArrayInputStream(semNetworkInputStreamBytes.get()));
            Assertions.assertThat(exportedSemNetwork.getGenerator("4IE3AA1 _generator")).isNotNull();
            Assertions.assertThat(exportedSemNetwork.getGenerator("FFR3AA1 _generator")).isNull();
        } else {
            Assertions.assertThat(semNetworkInputStreamBytes).hasNullValue();
            Assertions.assertThat(continentalNetworkInputStreamBytes).doesNotHaveNullValue();
            final Network exportedContinentalNetwork = Network.read("exportedContinentalNetwork.xiidm", new ByteArrayInputStream(continentalNetworkInputStreamBytes.get()));
            Assertions.assertThat(exportedContinentalNetwork.getGenerator("4IE3AA1 _generator")).isNull();
            Assertions.assertThat(exportedContinentalNetwork.getGenerator("FFR3AA1 _generator")).isNotNull();
        }

        final String continentalOrSem = isSem ? "sem" : "continental";
        Assertions.assertThat(requestAndResult).isNotNull();
        Assertions.assertThat(requestAndResult.raoRequest())
            .isNotNull()
            .hasFieldOrPropertyWithValue("raoRequestInstant", "2026-10-09T00:00:00Z")
            .hasFieldOrPropertyWithValue("networkFileUrl", "destinationKey/inputs/networks/" + continentalOrSem + "_20261009_0000.xiidm")
            .hasFieldOrPropertyWithValue("cracFileUrl", "destinationKey/inputs/cracs/" + continentalOrSem + "_20261009_0000.json")
            .hasFieldOrPropertyWithValue("refprogFileUrl", "/sem-integration/refprog.xml")
            .hasFieldOrPropertyWithValue("virtualHubsConfigurationFileUrl", "/sem-integration/virtualhub.xml")
            .hasFieldOrPropertyWithValue("realGlskFileUrl", "/sem-integration/glsk.xml")
            .hasFieldOrPropertyWithValue("raoParametersFileUrl", "/sem-integration/rao-parameters.json")
            .hasFieldOrPropertyWithValue("resultsDestination", "path/to/destination/" + continentalOrSem);
        Assertions.assertThat(requestAndResult.raoResult()).isNull();
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void logCoreCCParametersTest(final boolean parameter) {
        // Given
        final CoreCCRequest coreCCRequest = Mockito.mock(CoreCCRequest.class);
        final List<TaskParameterDto> parameterDtoList = createTaskParametersList(parameter);
        when(coreCCRequest.getTaskParameterList()).thenReturn(parameterDtoList);
        final InternalCoreCCRequest internalCoreCCRequest = new InternalCoreCCRequest(coreCCRequest);
        final CoreCCTaskParameters coreCCTaskParameters = new CoreCCTaskParameters(parameterDtoList);

        // When
        coreCCPreProcessService.logCoreCCParameters(internalCoreCCRequest, coreCCTaskParameters);

        // Then
        if (parameter) {
            verify(businessLogger).warn("""
                                            Core CC task parameters : '{
                                            \t"USE_DC_CGM_INPUT": true
                                            }'""");
        } else {
            verify(businessLogger).info("""
                                            Core CC task parameters : '{
                                            \t"USE_DC_CGM_INPUT": false
                                            }'""");
        }
    }

    @ParameterizedTest
    @CsvSource({"true,true", "true,false", "false,false"})
    void resolveCgmPathTest(final boolean useDcCgmInput, final boolean dcCgmPathIsNull) {
        // Given
        final CgmsAndXmlHeader dcCgmsHeader = mock(CgmsAndXmlHeader.class);
        final CgmsAndXmlHeader cgmHeader = mock(CgmsAndXmlHeader.class);
        final CoreCCTaskParameters parameters = new CoreCCTaskParameters(createTaskParametersList(useDcCgmInput));
        final Path dcCgmPath = mock(Path.class);
        final Path cgmPath = mock(Path.class);

        when(dcCgmsHeader.getNetworkPath(null)).thenReturn(dcCgmPathIsNull ? null : dcCgmPath);
        when(cgmHeader.getNetworkPath(null)).thenReturn(cgmPath);

        // When
        final Path resolvedPath = coreCCPreProcessService.resolveCgmPath(dcCgmsHeader, null, cgmHeader, parameters);

        // Then
        // DC CGM path is used only if parameter is true and path actually exists
        if (useDcCgmInput && !dcCgmPathIsNull) {
            Assertions.assertThat(resolvedPath).isEqualTo(dcCgmPath);
        } else {
            Assertions.assertThat(resolvedPath).isEqualTo(cgmPath);
        }
        // Log if DC CGM is required but absent
        final int nbOfTimesBusinessLogsCalled = useDcCgmInput && dcCgmPathIsNull ? 1 : 0;
        Mockito.verify(businessLogger, times(nbOfTimesBusinessLogsCalled)).warn("DC_CGM not available, using CGM input instead.");
    }

    @Test
    void uploadIidmNetworkTest() {
        // Given
        final OffsetDateTime offsetDateTime = OffsetDateTime.of(2026, 10, 9, 0, 0, 0, 0, ZoneOffset.UTC);
        final Network networkMock = Mockito.mock(Network.class);
        Mockito.doNothing().when(networkMock).write(eq("XIIDM"), eq(null), any(Path.class));
        final DateTimeFormatter utcHourlyNameFormatter = NamingRules.UTC_HOURLY_NAME_FORMATTER; // This seems useless, but I don't know why, the test gets stuck if I don't include this assignment *before* the MockedConstruction...
        try (MockedConstruction<FileInputStream> mockedFileInputStream = Mockito.mockConstruction(FileInputStream.class)) {
            // When
            coreCCPreProcessService.uploadIidmNetwork("destinationKey", Path.of("path/to/network.uct"), networkMock, "prefix", offsetDateTime.toInstant());

            // Then
            final ArgumentCaptor<String> destinationCaptor = ArgumentCaptor.forClass(String.class);
            final ArgumentCaptor<InputStream> inputStreamCaptor = ArgumentCaptor.forClass(InputStream.class);
            Mockito.verify(minioAdapter, Mockito.times(1)).uploadArtifact(destinationCaptor.capture(), inputStreamCaptor.capture());

            Assertions.assertThat(destinationCaptor.getValue()).isEqualTo("destinationKey/inputs/networks/prefix_20261009_0000.xiidm");
            Assertions.assertThat(inputStreamCaptor.getValue()).isEqualTo(mockedFileInputStream.constructed().getFirst());
        }
    }

    @Test
    void uploadJsonCracTest() throws IOException {
        // Given
        final OffsetDateTime offsetDateTime = OffsetDateTime.of(2026, 10, 9, 0, 0, 0, 0, ZoneOffset.UTC);
        final InternalCoreCCRequest coreCCRequest = Mockito.mock(InternalCoreCCRequest.class);
        Mockito.when(coreCCRequest.getCbcora()).thenReturn(new CoreCCFileResource("cbcoraFilename", "cbcoraUrl"));

        final FbConstraintCreationContext fbConstraintCreationContext = mock(FbConstraintCreationContext.class);
        Mockito.when(fileImporter.importCbcora(eq("cbcoraUrl"), any(), eq(network), any())).thenReturn(fbConstraintCreationContext);
        final Crac crac = mock(Crac.class);
        Mockito.when(fbConstraintCreationContext.getCrac()).thenReturn(crac);
        Mockito.doAnswer(invocationOnMock -> {
            final OutputStream outputStream = invocationOnMock.getArgument(1);
            outputStream.write("This is a test".getBytes(StandardCharsets.UTF_8));
            return null;
        }).when(crac).write(eq("JSON"), any());

        // When
        coreCCPreProcessService.uploadJsonCrac(coreCCRequest, "destinationKey", "prefix", offsetDateTime.toInstant(), network, null);

        // Then
        final ArgumentCaptor<String> destinationCaptor = ArgumentCaptor.forClass(String.class);
        final ArgumentCaptor<InputStream> inputStreamCaptor = ArgumentCaptor.forClass(InputStream.class);
        Mockito.verify(minioAdapter, Mockito.times(1)).uploadArtifact(destinationCaptor.capture(), inputStreamCaptor.capture());

        Assertions.assertThat(destinationCaptor.getValue()).isEqualTo("destinationKey/inputs/cracs/prefix_20261009_0000.json");
        Assertions.assertThat(inputStreamCaptor.getValue()).isNotNull();
        Assertions.assertThat(new String(inputStreamCaptor.getValue().readAllBytes()))
            .isEqualTo("This is a test");
    }

    @Test
    void sendRaoRequestAcknowledgementIfAckAlreadyUploadedTest() throws DatatypeConfigurationException {
        // Given
        final Header header = new Header();
        header.setVerb("create");
        header.setNoun("OptimizedRemedialActions");
        header.setRevision(BigInteger.valueOf(1));
        header.setContext("PRODUCTION");
        header.setCorrelationID("correlationId");
        XMLGregorianCalendar timestamp = DatatypeFactory.newInstance().newXMLGregorianCalendar(LocalDate.now().toString());
        header.setTimestamp(timestamp);
        final RequestMessage requestMessage = new RequestMessage();
        requestMessage.setHeader(header);
        requestMessage.setPayload(new Payload());
        final InternalCoreCCRequest coreCCRequest = Mockito.mock(InternalCoreCCRequest.class);
        Mockito.when(coreCCRequest.getTimestamp()).thenReturn(OffsetDateTime.now());

        Mockito.when(minioAdapter.fileExists(any())).thenReturn(true);

        // When
        coreCCPreProcessService.sendRaoRequestAcknowledgment(coreCCRequest, "destinationKey", requestMessage);

        // Then
        Mockito.verify(minioAdapter, Mockito.times(0)).uploadArtifact(any(), any());
    }

    @Test
    void sendRaoRequestAcknowledgementIfAckNotUploadedTest() throws DatatypeConfigurationException, IOException {
        // Given
        final LocalDate localDate = LocalDate.of(2026, 10, 9);
        final OffsetDateTime offsetDateTime = OffsetDateTime.of(2026, 10, 9, 0, 0, 0, 0, ZoneOffset.UTC);
        final Header header = new Header();
        header.setVerb("create");
        header.setNoun("OptimizedRemedialActions");
        header.setRevision(BigInteger.valueOf(1));
        header.setContext("PRODUCTION");
        header.setCorrelationID("correlationId");
        final XMLGregorianCalendar timestamp = DatatypeFactory.newInstance().newXMLGregorianCalendar(localDate.toString());
        header.setTimestamp(timestamp);
        final RequestMessage requestMessage = new RequestMessage();
        requestMessage.setHeader(header);
        requestMessage.setPayload(new Payload());
        final InternalCoreCCRequest coreCCRequest = Mockito.mock(InternalCoreCCRequest.class);
        Mockito.when(coreCCRequest.getTimestamp()).thenReturn(offsetDateTime);
        Mockito.when(coreCCRequest.getVersion()).thenReturn(5);

        Mockito.when(minioAdapter.fileExists(any())).thenReturn(false);

        // When
        coreCCPreProcessService.sendRaoRequestAcknowledgment(coreCCRequest, "destinationKey", requestMessage);

        // Then
        final ArgumentCaptor<String> destinationCaptor = ArgumentCaptor.forClass(String.class);
        final ArgumentCaptor<InputStream> inputStreamCaptor = ArgumentCaptor.forClass(InputStream.class);
        Mockito.verify(minioAdapter, Mockito.times(1)).uploadArtifact(destinationCaptor.capture(), inputStreamCaptor.capture());

        Assertions.assertThat(destinationCaptor.getValue()).isEqualTo("destinationKey/outputs/22XCORESO------S_10V1001C--00236Y_CORE-FB-302-ACK_20261009-F302-05.xml");
        Assertions.assertThat(inputStreamCaptor.getValue()).isNotNull();
        Assertions.assertThat(new String(inputStreamCaptor.getValue().readAllBytes()))
            .isEqualTo(
                """
                    <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
                    <ResponseMessage xmlns="http://iec.ch/TC57/2011/schema/message" xmlns:ns2="http://unicorn.com/Response/response-payload" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance">
                        <Header>
                            <Verb>reply</Verb>
                            <Noun>OptimizedRemedialActions</Noun>
                            <Revision>1</Revision>
                            <Context>PRODUCTION</Context>
                            <Timestamp></Timestamp>
                            <Source>22XCORESO------S</Source>
                            <MessageID>22XCORESO------S-20261009-F302-ACK</MessageID>
                            <CorrelationID>correlationId</CorrelationID>
                        </Header>
                        <Reply>
                            <Result>OK</Result>
                        </Reply>
                    </ResponseMessage>
                    """
            );
    }
}
