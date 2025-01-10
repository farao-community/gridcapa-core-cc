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
import com.farao_community.farao.gridcapa_core_cc.api.resource.InternalCoreCCRequest;
import com.farao_community.farao.gridcapa_core_cc.app.domain.CoreCCTaskParameters;
import com.farao_community.farao.gridcapa_core_cc.app.entities.CgmsAndXmlHeader;
import com.farao_community.farao.gridcapa_core_cc.app.inputs.rao_request.Header;
import com.farao_community.farao.gridcapa_core_cc.app.inputs.rao_request.Payload;
import com.farao_community.farao.gridcapa_core_cc.app.inputs.rao_request.RequestMessage;
import com.farao_community.farao.gridcapa_core_cc.app.inputs.rao_response.ResponseMessage;
import com.farao_community.farao.gridcapa_core_cc.app.services.FileImporter;
import com.farao_community.farao.minio_adapter.starter.MinioAdapter;
import com.powsybl.iidm.network.Network;
import com.powsybl.openrao.data.crac.api.Crac;
import com.powsybl.openrao.data.crac.io.fbconstraint.FbConstraintCreationContext;
import com.unicorn.request.request_payload.RequestItem;
import com.unicorn.request.request_payload.RequestItems;
import com.unicorn.response.response_payload.File;
import com.unicorn.response.response_payload.Files;
import com.unicorn.response.response_payload.ResponseItem;
import com.unicorn.response.response_payload.ResponseItems;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.slf4j.Logger;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * @author Daniel Thirion {@literal <daniel.thirion at rte-france.com>}
 */
class CoreCCPreProcessServiceTest {

    public static final String TIME_INTERVAL_6_7 = "2024-06-25T06:00:00Z/2024-06-25T07:00:00Z";
    public static final OffsetDateTime REQUEST_TIMESTAMP = OffsetDateTime.parse("2024-06-25T06:00:00Z");
    private final String networkFile = "/util/TestCase12NodesHvdc.uct";
    private final String dcNetworkFile = "/util/DCTestCase12NodesHvdc.uct";
    private final Path networkPath = Paths.get(getClass().getResource(networkFile).getPath());
    private final Path dcNetworkPath = Paths.get(getClass().getResource(dcNetworkFile).getPath());
    private final Network network = Network.read(networkPath);

    @Mock
    private MinioAdapter minioAdapter;

    @Mock
    private RaoParametersService raoParametersService;

    @Mock
    private FileImporter fileImporter;

    @InjectMocks
    private CoreCCPreProcessService coreCCPreProcessService;

    @Mock
    private Logger businessLogger;

    @BeforeEach
    public void setUp() {
        MockitoAnnotations.openMocks(this);
    }

    @Test
    void testInitializeTaskFromRequestWithNoTimestamp() {
        // Setup CoreCCRequest and InternalCoreCCRequest
        final CoreCCFileResource dummyFileResource = mock(CoreCCFileResource.class);
        when(dummyFileResource.getUrl()).thenReturn("fakeUrl");
        final TaskParameterDto taskParameterDto = new TaskParameterDto("USE_DC_CGM_INPUT", "BOOLEAN", "FALSE", "FALSE");
        final CoreCCRequest coreCCRequestEntity = new CoreCCRequest(
                "id", "current RunID", OffsetDateTime.now(), dummyFileResource, dummyFileResource, dummyFileResource,
                dummyFileResource, dummyFileResource, dummyFileResource,
                dummyFileResource, List.of(taskParameterDto));
        final InternalCoreCCRequest coreCCRequest = new InternalCoreCCRequest(coreCCRequestEntity);

        prepareAndMockRaoRequest(dummyFileResource);
        // mock cgms and xml header
        prepareAndMockCgmsAndXmlHeader(dummyFileResource, false, null, null);

        coreCCPreProcessService.initializeTaskFromAutomatedLaunch(coreCCRequest);

        verifyDefaultRaoRequest(coreCCRequest);
        assertEquals("Missing raoRequest", coreCCRequest.getHourlyRaoResult().getErrorMessage());

    }

    @Test
    void testInitializeTaskFromRequestWithCgmInput() throws IOException {
        // Setup CoreCCRequest and InternalCoreCCRequest
        final CoreCCFileResource dummyFileResource = mock(CoreCCFileResource.class);
        when(dummyFileResource.getUrl()).thenReturn("fakeUrl");
        final TaskParameterDto taskParameterDto = new TaskParameterDto("USE_DC_CGM_INPUT", "BOOLEAN", "FALSE", "FALSE");
        final CoreCCRequest coreCCRequestEntity = new CoreCCRequest(
                "id", "current RunID", REQUEST_TIMESTAMP, dummyFileResource, null, dummyFileResource,
                dummyFileResource, dummyFileResource, dummyFileResource,
                dummyFileResource, List.of(taskParameterDto));
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
    }

    @Test
    void testInitializeTaskFromRequestWithDCCgmInput() throws IOException {
        // Setup CoreCCRequest and InternalCoreCCRequest
        final CoreCCFileResource dummyDCCgmFileResource = mock(CoreCCFileResource.class);
        when(dummyDCCgmFileResource.getUrl()).thenReturn("dcCgmfakeUrl");
        final CoreCCFileResource dummyFileResource = mock(CoreCCFileResource.class);
        when(dummyFileResource.getUrl()).thenReturn("fakeUrl");
        final CoreCCRequest coreCCRequestEntity = new CoreCCRequest(
                "id", "current RunID", REQUEST_TIMESTAMP, dummyFileResource, dummyDCCgmFileResource, dummyFileResource,
                dummyFileResource, dummyFileResource, dummyFileResource,
                dummyFileResource, createTaskParametersList(true));
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
    }

    @Test
    void testInitializeTaskFromRequestWhenExceptionThrown() {
        // Setup CoreCCRequest and InternalCoreCCRequest
        final CoreCCFileResource dummyFileResource = mock(CoreCCFileResource.class);
        when(dummyFileResource.getUrl()).thenReturn("fakeUrl");
        final CoreCCRequest coreCCRequestEntity = new CoreCCRequest(
                "id", "current RunID", REQUEST_TIMESTAMP, dummyFileResource, dummyFileResource, dummyFileResource,
                dummyFileResource, dummyFileResource, dummyFileResource,
                dummyFileResource, createTaskParametersList(true));
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
        assertEquals("Error occurred while trying to import inputs at timestamp: 2024-06-25T06:00:00Z. Origin cause : Exception occurred while importing CRAC file: null", coreCCRequest.getHourlyRaoResult().getErrorMessage());
        assertEquals("2024-06-25T06:00:00Z", coreCCRequest.getHourlyRaoResult().getRaoRequestInstant());
    }

    private static Stream<Arguments> provideResolveCgmPathArgs() {
        return Stream.of(
                Arguments.of(true, true),
                Arguments.of(true, false),
                Arguments.of(false, false)
        );
    }

    @ParameterizedTest
    @MethodSource("provideResolveCgmPathArgs")
    void testResolveCgmPath(final boolean useDcCgmInput,
                            final boolean dcCgmPathIsNull) {
        final CgmsAndXmlHeader dcCgmsHeader = mock(CgmsAndXmlHeader.class);
        final CgmsAndXmlHeader cgmHeader = mock(CgmsAndXmlHeader.class);
        final CoreCCTaskParameters parameters = new CoreCCTaskParameters(createTaskParametersList(useDcCgmInput));
        final Path dcCgmPath = mock(Path.class);
        final Path cgmPath = mock(Path.class);

        when(dcCgmsHeader.getNetworkPath(null)).thenReturn(dcCgmPathIsNull ? null : dcCgmPath);
        when(cgmHeader.getNetworkPath(null)).thenReturn(cgmPath);

        final Path resolvedPath = coreCCPreProcessService.resolveCgmPath(dcCgmsHeader, null, cgmHeader, parameters);
        //DC CGM path is used only if parameter is true and path actually exists
        assertEquals(useDcCgmInput && !dcCgmPathIsNull ? dcCgmPath : cgmPath, resolvedPath);
        //Log if DC CGM is required but absent
        final int nbOfTimesBusinessLogsCalled = useDcCgmInput && dcCgmPathIsNull ? 1 : 0;
        verify(businessLogger, times(nbOfTimesBusinessLogsCalled)).warn(anyString());
    }

    private List<TaskParameterDto> createTaskParametersList(final boolean parameter) {
        final TaskParameterDto parameterDto = new TaskParameterDto("USE_DC_CGM_INPUT",
                "BOOLEAN",
                parameter ? "TRUE" : "FALSE",
                "FALSE");
        return Collections.singletonList(parameterDto);
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void testInfoLogIfDefaultParameters(final boolean parameter) {
        final CoreCCRequest coreCCRequest = Mockito.mock(CoreCCRequest.class);
        final List<TaskParameterDto> parameterDtoList = createTaskParametersList(parameter);
        when(coreCCRequest.getTaskParameterList()).thenReturn(parameterDtoList);
        final InternalCoreCCRequest internalCoreCCRequest = new InternalCoreCCRequest(coreCCRequest);
        final CoreCCTaskParameters coreCCTaskParameters = new CoreCCTaskParameters(parameterDtoList);

        coreCCPreProcessService.logCoreCCParameters(internalCoreCCRequest, coreCCTaskParameters);
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

    private void mockCrac() throws IOException {
        final Path cracJsonFilePath = Paths.get(getClass().getResource("/util/crac.json").getPath());
        final Crac crac = Crac.read(cracJsonFilePath.getFileName().toString(), java.nio.file.Files.newInputStream(cracJsonFilePath), network);
        final FbConstraintCreationContext cracCreationContext = Mockito.mock(FbConstraintCreationContext.class);
        when(fileImporter.importCrac(any(), any(), any())).thenReturn(cracCreationContext);
        when(cracCreationContext.getCrac()).thenReturn(crac);
    }

    private static void verifyNominalHourlyData(final InternalCoreCCRequest coreCCRequest,
                                                final String raoParametersFileUrl) {
        final HourlyRaoRequest hourlyRaoRequest = coreCCRequest.getHourlyRaoRequest();
        assertNotNull(hourlyRaoRequest);
        assertEquals("RAO_WORKING_DIR/20240625_0800/inputs/networks/20240625_0600.xiidm", hourlyRaoRequest.getNetworkFileUrl());
        assertEquals("RAO_WORKING_DIR/20240625_0800/inputs/cracs/20240625_0600.json", hourlyRaoRequest.getCracFileUrl());
        assertEquals(raoParametersFileUrl, hourlyRaoRequest.getRaoParametersFileUrl());
        //In nominal case, hourly rao result remains empty
        assertNull(coreCCRequest.getHourlyRaoResult());
    }

    private static void verifyDefaultRaoRequest(final InternalCoreCCRequest coreCCRequest) {
        final HourlyRaoRequest hourlyRaoRequest = coreCCRequest.getHourlyRaoRequest();
        final HourlyRaoResult hourlyRaoResult = coreCCRequest.getHourlyRaoResult();
        assertNotNull(hourlyRaoRequest);
        assertNotNull(hourlyRaoResult);
        assertEquals(HourlyRaoResult.Status.FAILURE, hourlyRaoResult.getStatus());
        assertEquals(HourlyRaoResult.ErrorCode.TS_PREPROCESSING_FAILURE.getCode(), hourlyRaoResult.getErrorCodeString());
        assertNull(hourlyRaoResult.getRaoResultFileUrl());
        assertNull(hourlyRaoResult.getNetworkWithPraUrl());
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
        Mockito.when(fileImporter.importRaoRequest(dummyFileResource)).thenReturn(raoRequestMessage);
    }

}
