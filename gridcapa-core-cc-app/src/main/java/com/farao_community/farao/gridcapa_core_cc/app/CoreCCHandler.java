/*
 * Copyright (c) 2023, RTE (http://www.rte-france.com)
 *  This Source Code Form is subject to the terms of the Mozilla Public
 *  License, v. 2.0. If a copy of the MPL was not distributed with this
 *  file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package com.farao_community.farao.gridcapa_core_cc.app;

import com.farao_community.farao.gridcapa_core_cc.api.exception.CoreCCInternalException;
import com.farao_community.farao.gridcapa_core_cc.api.exception.CoreCCRaoException;
import com.farao_community.farao.gridcapa_core_cc.api.resource.HourlyRaoRequest;
import com.farao_community.farao.gridcapa_core_cc.api.resource.HourlyRaoResult;
import com.farao_community.farao.gridcapa_core_cc.api.resource.InternalCoreCCRequest;
import com.farao_community.farao.gridcapa_core_cc.app.configuration.AmqpMessagesConfiguration;
import com.farao_community.farao.gridcapa_core_cc.app.postprocessing.FileExporterHelper;
import com.farao_community.farao.gridcapa_core_cc.app.preprocessing.CoreCCPreProcessService;
import com.farao_community.farao.gridcapa_core_cc.app.services.RaoRunnerService;
import com.farao_community.farao.rao_runner.api.resource.RaoSuccessResponse;
import com.github.jasminb.jsonapi.exceptions.ResourceParseException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.amqp.core.AmqpReplyTimeoutException;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.Objects;

/**
 * @author Godelaine de Montmorillon {@literal <godelaine.demontmorillon at rte-france.com>}
 * @author Philippe Edwards {@literal <philippe.edwards at rte-france.com>}
 */
@Component
public class CoreCCHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(CoreCCHandler.class);
    private static final DateTimeFormatter TIMESTAMP_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd' 'HH:mm");

    private final CoreCCPreProcessService coreCCPreProcessService;
    private final AmqpMessagesConfiguration amqpConfiguration;
    private final FileExporterHelper fileExporterHelper;
    private final RaoRunnerService raoRunnerService;

    private static final String RAO_FAILED_LOG_PATTERN = "Exception occurred in RAO computation for TimeStamp: '{}'. Origin cause: '{}'";

    public CoreCCHandler(CoreCCPreProcessService coreCCPreProcessService,
                         AmqpMessagesConfiguration amqpConfiguration,
                         RaoRunnerService raoRunnerService,
                         FileExporterHelper fileExporterHelper) {
        this.coreCCPreProcessService = coreCCPreProcessService;
        this.amqpConfiguration = amqpConfiguration;
        this.fileExporterHelper = fileExporterHelper;
        this.raoRunnerService = raoRunnerService;
    }

    public void handleCoreCCRequest(InternalCoreCCRequest internalCoreCCRequest) {
        internalCoreCCRequest.setRequestReceivedInstant(Instant.now());
        setUpEventLogging(internalCoreCCRequest);
        try {
            coreCCPreProcessService.initializeTaskFromAutomatedLaunch(internalCoreCCRequest);
            runRao(internalCoreCCRequest);
        } catch (Exception e) {
            throw new CoreCCInternalException("Exception occurred:", e);
        }
    }

    private static String setUpEventLogging(InternalCoreCCRequest coreCCRequest) {
        MDC.put("gridcapa-task-id", coreCCRequest.getId());
        return TIMESTAMP_FORMATTER.format(coreCCRequest.getTimestamp());
    }

    private void runRao(InternalCoreCCRequest coreCCRequest) {
        HourlyRaoRequest hourlyRaoRequest = coreCCRequest.getHourlyRaoRequest();
        HourlyRaoResult hourlyRaoResult;
        if (Objects.nonNull(coreCCRequest.getHourlyRaoResult())) {
            hourlyRaoResult = coreCCRequest.getHourlyRaoResult();
        } else {
            // HourlyRaoResult is not yet defined in nominal situation: raoRunnerService hasn't been called yet
            hourlyRaoResult = new HourlyRaoResult(hourlyRaoRequest.getRaoRequestInstant());
            coreCCRequest.setHourlyRaoResult(hourlyRaoResult);
        }
        if (hourlyRaoResult.getStatus().equals(HourlyRaoResult.Status.FAILURE)) {
            saveMetadataWhenPreProcessingFailed(coreCCRequest);
            LOGGER.info("Skipping RAO");
            return;
        }
        LOGGER.info("Launching RAO. CoreCCRequest id is {}", coreCCRequest.getId());
        try {
            RaoSuccessResponse raoResponse = raoRunnerService.run(hourlyRaoRequest.toRaoRequest(coreCCRequest.getId(), coreCCRequest.getRunId()));
            convertAndSaveReceivedRaoResult(coreCCRequest, raoResponse);
        } catch (CoreCCInternalException | CoreCCRaoException e) {
            handleRaoRunnerException(hourlyRaoResult, e);
        }
    }

    private void convertAndSaveReceivedRaoResult(InternalCoreCCRequest coreCCRequest, RaoSuccessResponse raoResponse) {
        HourlyRaoResult hourlyRaoResult = coreCCRequest.getHourlyRaoResult();
        try {
            hourlyRaoResult.setRaoResponseData(raoResponse);
            fileExporterHelper.exportCneToMinio(coreCCRequest);
            fileExporterHelper.exportNetworkToMinio(coreCCRequest);
            fileExporterHelper.exportRaoResultToMinio(coreCCRequest);
            // MetaData
            hourlyRaoResult.setStatus(HourlyRaoResult.Status.SUCCESS);
            fileExporterHelper.exportMetadataToMinio(coreCCRequest);
        } catch (Exception e) {
            //no throwing exception, just save cause and pass to next timestamp
            String errorMessage = String.format("error occurred while post processing rao outputs for timestamp: %s, Cause: %s", hourlyRaoResult.getRaoRequestInstant(), e);
            LOGGER.error(errorMessage);
            hourlyRaoResult.setStatus(HourlyRaoResult.Status.FAILURE);
            hourlyRaoResult.setErrorCode(HourlyRaoResult.ErrorCode.RAO_FAILURE);
            hourlyRaoResult.setErrorMessage(errorMessage);
        }
    }

    private void saveMetadataWhenPreProcessingFailed(InternalCoreCCRequest coreCCRequest) {
        try {
            fileExporterHelper.exportMetadataToMinioWhenPreProcessingFailed(coreCCRequest);
        } catch (Exception e) {
            throw new CoreCCInternalException("Exporting metadata failed when preProcessing failed", e);
        }
    }

    void handleRaoRunnerException(final HourlyRaoResult hourlyRaoResult,
                                  final Throwable exception) {
        hourlyRaoResult.setStatus(HourlyRaoResult.Status.FAILURE);
        hourlyRaoResult.setErrorCode(HourlyRaoResult.ErrorCode.RAO_FAILURE);
        if (exception instanceof final ResourceParseException resourceParseException) {
            // Sync scenario : exception details from rao-runner comes wrapped into ResourceParseException on json Api Error format.
            setErrorMessageAndLogIt(hourlyRaoResult, resourceParseException.getErrors().getErrors().get(0).getDetail());
        } else if (exception.getCause() instanceof final ResourceParseException resourceParseException) {
            // Async scenario : exception details from rao-runner comes wrapped into ResourceParseException on json Api Error format, which is wrapped itself into a ConcurrencyException.
            setErrorMessageAndLogIt(hourlyRaoResult, resourceParseException.getErrors().getErrors().get(0).getDetail());
        } else if (exception.getCause() instanceof AmqpReplyTimeoutException) {
            setErrorMessageAndLogIt(hourlyRaoResult, "Timeout reached, Rao has not finished within allocated time of : " + amqpConfiguration.getAsyncTimeOutInMinutes() + " minutes");
        } else {
            // if exception is not a json api Error neither an AmqpReplyTimeoutException
            setErrorMessageAndLogIt(hourlyRaoResult, exception.getMessage());
        }
    }

    private static void setErrorMessageAndLogIt(final HourlyRaoResult hourlyRaoResult,
                                                final String originCause) {
        hourlyRaoResult.setErrorMessage(originCause);
        LOGGER.warn(RAO_FAILED_LOG_PATTERN, hourlyRaoResult.getRaoRequestInstant(), originCause);
    }

}
