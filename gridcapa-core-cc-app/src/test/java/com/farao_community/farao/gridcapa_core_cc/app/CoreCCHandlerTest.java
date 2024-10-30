/*
 * Copyright (c) 2024, RTE (http://www.rte-france.com)
 *  This Source Code Form is subject to the terms of the Mozilla Public
 *  License, v. 2.0. If a copy of the MPL was not distributed with this
 *  file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package com.farao_community.farao.gridcapa_core_cc.app;

import com.farao_community.farao.gridcapa.task_manager.api.TaskParameterDto;
import com.farao_community.farao.gridcapa_core_cc.api.resource.CoreCCFileResource;
import com.farao_community.farao.gridcapa_core_cc.api.resource.CoreCCRequest;
import com.farao_community.farao.gridcapa_core_cc.api.resource.HourlyRaoResult;
import com.farao_community.farao.gridcapa_core_cc.api.resource.InternalCoreCCRequest;
import com.farao_community.farao.gridcapa_core_cc.app.postprocessing.FileExporterHelper;
import com.farao_community.farao.gridcapa_core_cc.app.services.RaoRunnerService;
import com.farao_community.farao.minio_adapter.starter.MinioAdapter;
import com.farao_community.farao.rao_runner.api.resource.RaoResponse;
import com.github.jasminb.jsonapi.exceptions.ResourceParseException;
import com.github.jasminb.jsonapi.models.errors.Error;
import com.github.jasminb.jsonapi.models.errors.Errors;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.amqp.core.AmqpReplyTimeoutException;
import org.springframework.amqp.core.Message;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URISyntaxException;
import java.net.URL;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * @author Ameni Walha {@literal <ameni.walha at rte-france.com>}
 */
@SpringBootTest
class CoreCCHandlerTest {

    @Autowired
    private CoreCCHandler coreCCHandler;

    @MockBean
    private MinioAdapter minioAdapter;

    @MockBean
    private RaoRunnerService raoRunnerService;

    @MockBean
    private FileExporterHelper fileExporterHelper;

    private InternalCoreCCRequest internalCoreCCRequest;

    @BeforeEach
    void setUp() {
        internalCoreCCRequest = createInternalCoreCCRequest();
    }

    @Test
    void handleCoreCCRequestTest() throws IOException {
        Mockito.when(minioAdapter.generatePreSignedUrl(Mockito.any())).thenReturn("http://url");
        RaoResponse raoResponse = new RaoResponse.RaoResponseBuilder()
                .withId("id")
                .withInstant("instant")
                .withNetworkWithPraFileUrl("praUrl")
                .withCracFileUrl("cracUrl")
                .withRaoResultFileUrl("raoUrl")
                .withComputationStartInstant(Instant.now())
                .withComputationEndInstant(Instant.now())
                .build();
        Mockito.when(raoRunnerService.run(Mockito.any())).thenReturn(raoResponse);
        Mockito.doNothing().when(Mockito.mock(FileExporterHelper.class)).exportCneToMinio(Mockito.any());
        Mockito.doNothing().when(Mockito.mock(FileExporterHelper.class)).exportNetworkToMinio(Mockito.any());
        Mockito.doNothing().when(Mockito.mock(FileExporterHelper.class)).exportRaoResultToMinio(Mockito.any());
        Mockito.doNothing().when(Mockito.mock(FileExporterHelper.class)).exportMetadataToMinio(Mockito.any());

        String requestId = "Test request";
        String networkFileName = "20210723-F119-v1-17XTSO-CS------W-to-22XCORESO------S.zip";
        String testDirectory = "/20210723";
        CoreCCFileResource networkFile = createFileResource(networkFileName, getClass().getResource(testDirectory + "/" + networkFileName));

        OffsetDateTime dateTime = OffsetDateTime.parse("2021-07-22T22:30Z");
        CoreCCFileResource refProgFile = createFileResource("", getClass().getResource(testDirectory + "/20210723-F110.xml"));
        CoreCCFileResource raoRequestFile = createFileResource("", getClass().getResource(testDirectory + "/20210723-F302-v3.xml"));
        CoreCCFileResource virtualHubFile = createFileResource("", getClass().getResource(testDirectory + "/20210723-F327-fake.xml"));
        CoreCCFileResource glskFile = createFileResource("", getClass().getResource(testDirectory + "/20210723-F226-v1.xml"));
        CoreCCFileResource cbcoraFile = createFileResource("cbcora", getClass().getResource(testDirectory + "/20210723-F301_CBCORA_hvdcvh-outage.xml"));

        CoreCCRequest request = new CoreCCRequest(requestId, "current RunID", dateTime, networkFile, null, cbcoraFile, glskFile, refProgFile, raoRequestFile, virtualHubFile, true, List.of(new TaskParameterDto("USE_DC_CGM_INPUT", "BOOLEAN", "FALSE", "FALSE")));
        InternalCoreCCRequest internalCoreCCRequest = new InternalCoreCCRequest(request);
        coreCCHandler.handleCoreCCRequest(internalCoreCCRequest);
        //should upload 7 artifacts: parameters + ACK + crac + network
        Mockito.verify(minioAdapter, Mockito.times(4)).uploadArtifact(Mockito.any(), Mockito.any());
        // TODO : delete generated tmp dir
    }

    private CoreCCFileResource createFileResource(String filename, URL resource) {
        try {
            return new CoreCCFileResource(filename, resource.toURI().toURL().toString());
        } catch (MalformedURLException | URISyntaxException e) {
            throw new RuntimeException(e);
        }
    }


    @Test
    void handleRaoRunnerException() {
        HourlyRaoResult hourlyRaoResult = new HourlyRaoResult("2021-07-22T22:30Z");
        resourceParseExceptionCase(hourlyRaoResult);
        exceptionCausedByResourceParseExceptionCase(hourlyRaoResult);
        exceptionCausedByAmqpReplyTimeoutExceptionCase(hourlyRaoResult);
        otherExceptionTypeCauseCase(hourlyRaoResult);
    }

    private void resourceParseExceptionCase(HourlyRaoResult hourlyRaoResult) {
        Error error = new Error();
        error.setDetail("Error message");
        Errors errors = new Errors();
        errors.setErrors(List.of(error));
        ResourceParseException resourceParseException = new ResourceParseException(errors);

        coreCCHandler.handleRaoRunnerException(hourlyRaoResult, resourceParseException);
        assertEquals(HourlyRaoResult.Status.FAILURE, hourlyRaoResult.getStatus());
        assertEquals(HourlyRaoResult.ErrorCode.RAO_FAILURE.getCode(), hourlyRaoResult.getErrorCodeString());
        assertEquals("Error message", hourlyRaoResult.getErrorMessage());
    }

    private void exceptionCausedByResourceParseExceptionCase(HourlyRaoResult hourlyRaoResult) {
        Error error = new Error();
        error.setDetail("Exception caused by ResourceParseException");
        Errors errors = new Errors();
        errors.setErrors(List.of(error));
        ResourceParseException resourceParseException = new ResourceParseException(errors);

        RuntimeException exception = Mockito.mock(RuntimeException.class);
        Mockito.doReturn(resourceParseException).when(exception).getCause();

        coreCCHandler.handleRaoRunnerException(hourlyRaoResult, exception);
        assertEquals(HourlyRaoResult.Status.FAILURE, hourlyRaoResult.getStatus());
        assertEquals(HourlyRaoResult.ErrorCode.RAO_FAILURE.getCode(), hourlyRaoResult.getErrorCodeString());
        assertEquals("Exception caused by ResourceParseException", hourlyRaoResult.getErrorMessage());
    }

    private void exceptionCausedByAmqpReplyTimeoutExceptionCase(HourlyRaoResult hourlyRaoResult) {
        Message message = new Message("AmqpReplyTimeoutException".getBytes());
        AmqpReplyTimeoutException amqpReplyTimeoutException = new AmqpReplyTimeoutException("Timeout exception", message);

        RuntimeException exception = Mockito.mock(RuntimeException.class);
        Mockito.doReturn(amqpReplyTimeoutException).when(exception).getCause();

        coreCCHandler.handleRaoRunnerException(hourlyRaoResult, exception);
        assertEquals(HourlyRaoResult.Status.FAILURE, hourlyRaoResult.getStatus());
        assertEquals(HourlyRaoResult.ErrorCode.RAO_FAILURE.getCode(), hourlyRaoResult.getErrorCodeString());
        // Locale formatting problem: 10,00 v. 10.00
        String regex = "Timeout reached, Rao has not finished within allocated time of : 10(\\.|,)00 minutes";
        assertTrue(hourlyRaoResult.getErrorMessage().matches(regex));
    }

    private void otherExceptionTypeCauseCase(HourlyRaoResult hourlyRaoResult) {
        RuntimeException exception = new RuntimeException("Other exception");
        coreCCHandler.handleRaoRunnerException(hourlyRaoResult, exception);
        assertEquals(HourlyRaoResult.Status.FAILURE, hourlyRaoResult.getStatus());
        assertEquals(HourlyRaoResult.ErrorCode.RAO_FAILURE.getCode(), hourlyRaoResult.getErrorCodeString());
        assertEquals("Other exception", hourlyRaoResult.getErrorMessage());
    }
}
