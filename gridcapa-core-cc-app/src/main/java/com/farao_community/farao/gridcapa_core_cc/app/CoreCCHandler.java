/*
 * Copyright (c) 2023, RTE (http://www.rte-france.com)
 *  This Source Code Form is subject to the terms of the Mozilla Public
 *  License, v. 2.0. If a copy of the MPL was not distributed with this
 *  file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package com.farao_community.farao.gridcapa_core_cc.app;

import com.farao_community.farao.gridcapa_core_cc.api.exception.CoreCCInternalException;
import com.farao_community.farao.gridcapa_core_cc.api.exception.CoreCCRaoException;
import com.farao_community.farao.gridcapa_core_cc.api.resource.*;
import com.farao_community.farao.gridcapa_core_cc.app.configuration.AmqpMessagesConfiguration;
import com.farao_community.farao.gridcapa_core_cc.app.postprocessing.FileExporterHelper;
import com.farao_community.farao.gridcapa_core_cc.app.preprocessing.CoreCCPreProcessService;
import com.farao_community.farao.gridcapa_core_cc.app.services.RaoRunnerService;
import com.farao_community.farao.rao_runner.api.resource.RaoResponse;
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

    public CoreCCResponse handleCoreCCRequest(InternalCoreCCRequest internalCoreCCRequest) {
        internalCoreCCRequest.setRequestReceivedInstant(Instant.now());
        setUpEventLogging(internalCoreCCRequest);
        try {
            coreCCPreProcessService.initializeTaskFromAutomatedLaunch(internalCoreCCRequest);
            runRao(internalCoreCCRequest);
        } catch (Exception e) {
            throw new CoreCCInternalException("Exception occurred:", e);
        }
        return buildCoreCCResponse(internalCoreCCRequest);
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
            RaoResponse raoResponse = raoRunnerService.run(hourlyRaoRequest.toRaoRequest(coreCCRequest.getId()));
            convertAndSaveReceivedRaoResult(coreCCRequest, raoResponse);
        } catch (CoreCCInternalException | CoreCCRaoException e) {
            handleRaoRunnerException(hourlyRaoResult, e);
        }
    }

    private void convertAndSaveReceivedRaoResult(InternalCoreCCRequest coreCCRequest, RaoResponse raoResponse) {
        HourlyRaoResult hourlyRaoResult = coreCCRequest.getHourlyRaoResult();
        try {
            hourlyRaoResult.setRaoResponseData(raoResponse);
            fileExporterHelper.exportCneToMinio(coreCCRequest);
            fileExporterHelper.exportNetworkToMinio(coreCCRequest);
            // TODO : useless raoresult export. check what's imported in post processing
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
            throw new CoreCCInternalException("Exporting metadata failed when preProcessing failed");
        }
    }

    private void handleRaoRunnerException(HourlyRaoResult hourlyRaoResult, Throwable exception) {
        hourlyRaoResult.setStatus(HourlyRaoResult.Status.FAILURE);
        hourlyRaoResult.setErrorCode(HourlyRaoResult.ErrorCode.RAO_FAILURE);
        if (exception instanceof ResourceParseException) {
            // Sync scenario : exception details from rao-runner comes wrapped into ResourceParseException on json Api Error format.
            ResourceParseException resourceParseException = (ResourceParseException) exception;
            String originCause = resourceParseException.getErrors().getErrors().get(0).getDetail();
            hourlyRaoResult.setErrorMessage(originCause);
            LOGGER.warn(RAO_FAILED_LOG_PATTERN, hourlyRaoResult.getRaoRequestInstant(), originCause);
        } else if (exception.getCause() instanceof ResourceParseException) {
            // Async scenario : exception details from rao-runner comes wrapped into ResourceParseException on json Api Error format, which is wrapped itself into a ConcurrencyException.
            ResourceParseException resourceParseException = (ResourceParseException) exception.getCause();
            String originCause = resourceParseException.getErrors().getErrors().get(0).getDetail();
            hourlyRaoResult.setErrorMessage(originCause);
            LOGGER.warn(RAO_FAILED_LOG_PATTERN, hourlyRaoResult.getRaoRequestInstant(), originCause);
        } else if (exception.getCause() instanceof AmqpReplyTimeoutException) {
            String originCause = "Timeout reached, Rao has not finished within allocated time of : " + amqpConfiguration.getAsyncTimeOutInMinutes() + " minutes";
            hourlyRaoResult.setErrorMessage(originCause);
            LOGGER.warn(RAO_FAILED_LOG_PATTERN, hourlyRaoResult.getRaoRequestInstant(), originCause);
        } else {
            // if exception is not a json api Error neither an AmqpReplyTimeoutException
            String originCause = exception.getMessage();
            hourlyRaoResult.setErrorMessage(originCause);
            LOGGER.warn(RAO_FAILED_LOG_PATTERN, hourlyRaoResult.getRaoRequestInstant(), originCause);
        }
    }

    private CoreCCResponse buildCoreCCResponse(InternalCoreCCRequest coreCCRequest) {
        return new CoreCCResponse(coreCCRequest.getId());
    }

}
