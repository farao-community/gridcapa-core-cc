/*
 * Copyright (c) 2023, RTE (http://www.rte-france.com)
 *  This Source Code Form is subject to the terms of the Mozilla Public
 *  License, v. 2.0. If a copy of the MPL was not distributed with this
 *  file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package com.farao_community.farao.gridcapa_core_cc.app;

import com.farao_community.farao.gridcapa_core_cc.api.exception.CoreCCInternalException;
import com.farao_community.farao.gridcapa_core_cc.api.resource.CoreCCRequest;
import com.farao_community.farao.gridcapa_core_cc.api.resource.CoreCCResponse;
import com.farao_community.farao.gridcapa_core_cc.api.resource.HourlyRaoResult;
import com.farao_community.farao.gridcapa_core_cc.app.configuration.AmqpMessagesConfiguration;
import com.farao_community.farao.gridcapa_core_cc.app.postprocessing.CoreCCPostProcessService;
import com.farao_community.farao.gridcapa_core_cc.app.postprocessing.FileExporterHelper;
import com.farao_community.farao.gridcapa_core_cc.app.postprocessing.LogsExporter;
import com.farao_community.farao.gridcapa_core_cc.app.preprocessing.CoreCCPreProcessService;
import com.farao_community.farao.gridcapa_core_cc.app.services.FileImporter;
import com.farao_community.farao.rao_runner.api.resource.RaoRequest;
import com.farao_community.farao.rao_runner.api.resource.RaoResponse;
import com.farao_community.farao.rao_runner.starter.AsynchronousRaoRunnerClient;
import com.github.jasminb.jsonapi.exceptions.ResourceParseException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.amqp.core.AmqpReplyTimeoutException;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.CompletableFuture;

/**
 * @author Ameni Walha {@literal <ameni.walha at rte-france.com>}
 * @author Theo Pascoli {@literal <theo.pascoli at rte-france.com>}
 * @author Vincent Bochet {@literal <vincent.bochet at rte-france.com>}
 */
@Component
public class CoreCCHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(CoreCCHandler.class);
    private static final DateTimeFormatter TIMESTAMP_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd' 'HH:mm");

    private final CoreCCPreProcessService coreCCPreProcessService;
    private final CoreCCPostProcessService coreCCPostProcessService;
    private final Logger eventsLogger;
    private final AmqpMessagesConfiguration amqpConfiguration;
    private final FileExporterHelper fileExporterHelper;
    private final AsynchronousRaoRunnerClient raoRunnerClient;
    private final LogsExporter logsExporter;
    private final FileImporter fileImporter;
    private static final String RAO_FAILED_LOG_PATTERN = "Exception occurred in RAO computation for TimeStamp: '{}'. Origin cause: '{}'";

    public CoreCCHandler(CoreCCPreProcessService coreCCPreProcessService,
                         CoreCCPostProcessService coreCCPostProcessService,
                         Logger eventsLogger,
                         AmqpMessagesConfiguration amqpConfiguration,
                         AsynchronousRaoRunnerClient raoRunnerClient,
                         FileExporterHelper fileExporterHelper,
                         FileImporter fileImporter,
                         LogsExporter logsExporter) {
        this.coreCCPreProcessService = coreCCPreProcessService;
        this.coreCCPostProcessService = coreCCPostProcessService;
        this.amqpConfiguration = amqpConfiguration;
        this.fileExporterHelper = fileExporterHelper;
        this.eventsLogger = eventsLogger;
        this.raoRunnerClient = raoRunnerClient;
        this.fileImporter = fileImporter;
        this.logsExporter = logsExporter;
    }

    public CoreCCResponse handleCoreCCRequest(CoreCCRequest coreCCRequest, boolean isManualRun) {
        final String formattedTimestamp = setUpEventLogging(coreCCRequest);
        String outputPath;
        try {
            coreCCPreProcessService.initializeTaskFromAutomatedLaunch(coreCCRequest);
            outputPath = runRaoForEachTimeStamp(coreCCRequest, isManualRun);
        } catch (Exception e) {
            throw new CoreCCInternalException("Exception occurred:", e);
        }
        return buildCoreCCResponse(coreCCRequest, outputPath);
    }

    private static String setUpEventLogging(CoreCCRequest coreCCRequest) {
        MDC.put("gridcapa-task-id", coreCCRequest.getId());
        return TIMESTAMP_FORMATTER.format(coreCCRequest.getTimestamp());
    }

    private String runRaoForEachTimeStamp(CoreCCRequest coreCCRequest, boolean isManualRun) {
        StringBuilder outputPathBuilder = new StringBuilder();
        coreCCRequest.getHourlyRaoRequests().forEach(hourlyRaoRequest -> {
            RaoRequest raoRequest = hourlyRaoRequest.toRaoRequest(coreCCRequest.getId());
            CompletableFuture<RaoResponse> raoResponseFuture = raoRunnerClient.runRaoAsynchronously(raoRequest);
            raoResponseFuture.thenApply(raoResponse -> {

                synchronized (this) {
                    LOGGER.info("RAO computation answer received  for TimeStamp: '{}'", hourlyRaoRequest.getInstant());
                    HourlyRaoResult hourlyRaoResult = new HourlyRaoResult();
                    hourlyRaoResult.setInstant(hourlyRaoRequest.getInstant());
                    convertAndSaveAsynchronouslyReceivedRaoResult(coreCCRequest, hourlyRaoResult, raoResponse);
                    coreCCRequest.getHourlyRaoResults().add(hourlyRaoResult);
                    outputPathBuilder.append(runFinalPostProcessIfAllTimestampsAreFinished(coreCCRequest, isManualRun));
                }
                return null;
            }).exceptionally(exception -> {

                synchronized (this) {
                    HourlyRaoResult hourlyRaoResult = new HourlyRaoResult();
                    hourlyRaoResult.setInstant(hourlyRaoRequest.getInstant());
                    handleRaoRunnerException(hourlyRaoResult, exception);
                    coreCCRequest.getHourlyRaoResults().add(hourlyRaoResult);
                    outputPathBuilder.append(runFinalPostProcessIfAllTimestampsAreFinished(coreCCRequest, isManualRun));
                }
                return null;
            });
        });
        return outputPathBuilder.toString();
    }

    private void convertAndSaveAsynchronouslyReceivedRaoResult(CoreCCRequest coreCCRequest, HourlyRaoResult hourlyRaoResult, RaoResponse raoResponse) {
        try {
            hourlyRaoResult.setRaoResponseData(raoResponse);
            hourlyRaoResult.setStatus(HourlyRaoResult.Status.SUCCESS);
            fileExporterHelper.exportCneInTmpOutput(coreCCRequest, hourlyRaoResult);
            fileExporterHelper.exportNetworkInTmpOutput(coreCCRequest, hourlyRaoResult);
        } catch (Exception e) {
            //no throwing exception, just save cause and pass to next timestamp
            String errorMessage = String.format("error occurred while post processing rao outputs for timestamp: %s, Cause: %s", hourlyRaoResult.getInstant(), e.getMessage());
            LOGGER.error(errorMessage);
            hourlyRaoResult.setStatus(HourlyRaoResult.Status.FAILURE);
            hourlyRaoResult.setErrorCode(HourlyRaoResult.ErrorCode.RAO_FAILURE);
            hourlyRaoResult.setErrorMessage(errorMessage);
        }
    }

    private String runFinalPostProcessIfAllTimestampsAreFinished(CoreCCRequest coreCCRequest, boolean isManualRun) {
        try {
            long requestedRaos = coreCCRequest.getHourlyRaoRequests().size();
            long receivedRaos = coreCCRequest.getHourlyRaoResults().stream().filter(hourlyRaoResult -> hourlyRaoResult.getStatus().equals(HourlyRaoResult.Status.SUCCESS)).count() +
                coreCCRequest.getHourlyRaoResults().stream().filter(hourlyRaoResult -> hourlyRaoResult.getStatus().equals(HourlyRaoResult.Status.FAILURE)).count();
            if (requestedRaos == receivedRaos) {
                LOGGER.info("Core CC server received answers for all requested '{}' timestamps, --> proceeding to generating outputs", requestedRaos);
                coreCCRequest.setComputationEndInstant(Instant.now());
                logsExporter.exportLogs(coreCCRequest);
                return coreCCPostProcessService.postProcessHourlyResults(coreCCRequest, isManualRun);
            }
            return "";
        } catch (Exception e) {
            coreCCRequest.setStatus(CoreCCRequest.Status.FAILURE);
            return "";
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
            LOGGER.warn(RAO_FAILED_LOG_PATTERN, hourlyRaoResult.getInstant(), originCause);
        } else if (exception.getCause() instanceof ResourceParseException) {
            // Async scenario : exception details from rao-runner comes wrapped into ResourceParseException on json Api Error format, which is wrapped itself into a ConcurrencyException.
            ResourceParseException resourceParseException = (ResourceParseException) exception.getCause();
            String originCause = resourceParseException.getErrors().getErrors().get(0).getDetail();
            hourlyRaoResult.setErrorMessage(originCause);
            LOGGER.warn(RAO_FAILED_LOG_PATTERN, hourlyRaoResult.getInstant(), originCause);
        } else if (exception.getCause() instanceof AmqpReplyTimeoutException) {
            String originCause = "Timeout reached, Rao has not finished within allocated time of : " + amqpConfiguration.getAsyncTimeOutInMinutes() + " minutes";
            hourlyRaoResult.setErrorMessage(originCause);
            LOGGER.warn(RAO_FAILED_LOG_PATTERN, hourlyRaoResult.getInstant(), originCause);
        } else {
            // if exception is not a json api Error neither an AmqpReplyTimeoutException
            String originCause = exception.getMessage();
            hourlyRaoResult.setErrorMessage(originCause);
            LOGGER.warn(RAO_FAILED_LOG_PATTERN, hourlyRaoResult.getInstant(), originCause);
        }
    }

    private CoreCCResponse buildCoreCCResponse(CoreCCRequest coreCCRequest, String outputPath) {
        return new CoreCCResponse(coreCCRequest.getId(), outputPath, coreCCRequest.getComputationStartInstant(), coreCCRequest.getComputationEndInstant());
    }

}
