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
import com.farao_community.farao.rao_runner.api.resource.RaoSuccessResponse;
import com.github.jasminb.jsonapi.exceptions.ResourceParseException;
import com.github.jasminb.jsonapi.models.errors.Error;
import com.github.jasminb.jsonapi.models.errors.Errors;
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

    @Test
    void handleCoreCCRequestTest() throws IOException {
        Mockito.when(minioAdapter.generatePreSignedUrl(Mockito.any())).thenReturn("http://url");
        RaoSuccessResponse raoResponse = new RaoSuccessResponse.Builder()
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

        final String requestId = "Test request";
        final String networkFileName = "20210723-F119-v1-17XTSO-CS------W-to-22XCORESO------S.zip";
        final String testDirectory = "/20210723";
        final CoreCCFileResource networkFile = createFileResource(networkFileName, getClass().getResource(testDirectory + "/" + networkFileName));

        final OffsetDateTime dateTime = OffsetDateTime.parse("2021-07-22T22:30Z");
        final CoreCCFileResource refProgFile = createFileResource("", getClass().getResource(testDirectory + "/20210723-F110.xml"));
        final CoreCCFileResource raoRequestFile = createFileResource("", getClass().getResource(testDirectory + "/20210723-F302-v3.xml"));
        final CoreCCFileResource virtualHubFile = createFileResource("", getClass().getResource(testDirectory + "/20210723-F327-fake.xml"));
        final CoreCCFileResource glskFile = createFileResource("", getClass().getResource(testDirectory + "/20210723-F226-v1.xml"));
        final CoreCCFileResource cbcoraFile = createFileResource("cbcora", getClass().getResource(testDirectory + "/20210723-F301_CBCORA_hvdcvh-outage.xml"));

        final CoreCCRequest request = new CoreCCRequest(requestId, "current RunID", dateTime, networkFile, null, cbcoraFile, glskFile, refProgFile, raoRequestFile, virtualHubFile, true, List.of(new TaskParameterDto("USE_DC_CGM_INPUT", "BOOLEAN", "FALSE", "FALSE")));
        final InternalCoreCCRequest internalCoreCCRequest = new InternalCoreCCRequest(request);
        coreCCHandler.handleCoreCCRequest(internalCoreCCRequest);
        //should upload 4 artifacts: parameters + ACK + crac + network
        Mockito.verify(minioAdapter, Mockito.times(4)).uploadArtifact(Mockito.any(), Mockito.any());
    }

    @Test
    void handleRaoRunnerException() {
        final HourlyRaoResult hourlyRaoResult = new HourlyRaoResult("2021-07-22T22:30Z");
        resourceParseExceptionCase(hourlyRaoResult);
        exceptionCausedByResourceParseExceptionCase(hourlyRaoResult);
        exceptionCausedByAmqpReplyTimeoutExceptionCase(hourlyRaoResult);
        otherExceptionTypeCauseCase(hourlyRaoResult);
    }

    private CoreCCFileResource createFileResource(final String filename, final URL resource) {
        try {
            return new CoreCCFileResource(filename, resource.toURI().toURL().toString());
        } catch (final MalformedURLException | URISyntaxException | IllegalArgumentException e) {
            throw new RuntimeException(e);
        }
    }

    private void resourceParseExceptionCase(final HourlyRaoResult hourlyRaoResult) {
        final Error error = new Error();
        error.setDetail("Error message");
        final Errors errors = new Errors();
        errors.setErrors(List.of(error));
        final ResourceParseException resourceParseException = new ResourceParseException(errors);

        coreCCHandler.handleRaoRunnerException(hourlyRaoResult, resourceParseException);
        assertEquals(HourlyRaoResult.Status.FAILURE, hourlyRaoResult.getStatus());
        assertEquals(HourlyRaoResult.ErrorCode.RAO_FAILURE.getCode(), hourlyRaoResult.getErrorCodeString());
        assertEquals("Error message", hourlyRaoResult.getErrorMessage());
    }

    private void exceptionCausedByResourceParseExceptionCase(final HourlyRaoResult hourlyRaoResult) {
        final Error error = new Error();
        error.setDetail("Exception caused by ResourceParseException");
        final Errors errors = new Errors();
        errors.setErrors(List.of(error));
        final ResourceParseException resourceParseException = new ResourceParseException(errors);

        final RuntimeException exception = Mockito.mock(RuntimeException.class);
        Mockito.doReturn(resourceParseException).when(exception).getCause();

        coreCCHandler.handleRaoRunnerException(hourlyRaoResult, exception);
        assertEquals(HourlyRaoResult.Status.FAILURE, hourlyRaoResult.getStatus());
        assertEquals(HourlyRaoResult.ErrorCode.RAO_FAILURE.getCode(), hourlyRaoResult.getErrorCodeString());
        assertEquals("Exception caused by ResourceParseException", hourlyRaoResult.getErrorMessage());
    }

    private void exceptionCausedByAmqpReplyTimeoutExceptionCase(final HourlyRaoResult hourlyRaoResult) {
        final Message message = new Message("AmqpReplyTimeoutException".getBytes());
        final AmqpReplyTimeoutException amqpReplyTimeoutException = new AmqpReplyTimeoutException("Timeout exception", message);

        final RuntimeException exception = Mockito.mock(RuntimeException.class);
        Mockito.doReturn(amqpReplyTimeoutException).when(exception).getCause();

        coreCCHandler.handleRaoRunnerException(hourlyRaoResult, exception);
        assertEquals(HourlyRaoResult.Status.FAILURE, hourlyRaoResult.getStatus());
        assertEquals(HourlyRaoResult.ErrorCode.RAO_FAILURE.getCode(), hourlyRaoResult.getErrorCodeString());
        // Locale formatting problem: 10,00 v. 10.00
        final String regex = "Timeout reached, Rao has not finished within allocated time of : 10([.,])00 minutes";
        assertTrue(hourlyRaoResult.getErrorMessage().matches(regex));
    }

    private void otherExceptionTypeCauseCase(final HourlyRaoResult hourlyRaoResult) {
        final RuntimeException exception = new RuntimeException("Other exception");
        coreCCHandler.handleRaoRunnerException(hourlyRaoResult, exception);
        assertEquals(HourlyRaoResult.Status.FAILURE, hourlyRaoResult.getStatus());
        assertEquals(HourlyRaoResult.ErrorCode.RAO_FAILURE.getCode(), hourlyRaoResult.getErrorCodeString());
        assertEquals("Other exception", hourlyRaoResult.getErrorMessage());
    }
}
