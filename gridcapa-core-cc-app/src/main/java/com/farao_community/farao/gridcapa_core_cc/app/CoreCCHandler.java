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
import com.farao_community.farao.gridcapa_core_cc.app.postprocessing.CoreCCPostProcessService;
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

    private static final String RAO_FAILED_LOG_PATTERN = "Exception occurred in RAO computation for timestamp '{}'. Origin cause: '{}'";

    private final CoreCCPreProcessService coreCCPreProcessService;
    private final CoreCCPostProcessService coreCCPostProcessService;
    private final AmqpMessagesConfiguration amqpConfiguration;
    private final FileExporterHelper fileExporterHelper;
    private final RaoRunnerService raoRunnerService;

    public CoreCCHandler(final CoreCCPreProcessService coreCCPreProcessService,
                         final CoreCCPostProcessService coreCCPostProcessService,
                         final AmqpMessagesConfiguration amqpConfiguration,
                         final RaoRunnerService raoRunnerService,
                         final FileExporterHelper fileExporterHelper) {
        this.coreCCPreProcessService = coreCCPreProcessService;
        this.coreCCPostProcessService = coreCCPostProcessService;
        this.amqpConfiguration = amqpConfiguration;
        this.fileExporterHelper = fileExporterHelper;
        this.raoRunnerService = raoRunnerService;
    }

    public void handleCoreCCRequest(final InternalCoreCCRequest internalCoreCCRequest) {
        internalCoreCCRequest.setRequestReceivedInstant(Instant.now());
        setUpEventLogging(internalCoreCCRequest);
        try {
            coreCCPreProcessService.initializeTaskFromAutomatedLaunch(internalCoreCCRequest);
            runRaoForAllAreas(internalCoreCCRequest);
        } catch (final Exception e) {
            throw new CoreCCInternalException("Exception occurred:", e);
        }
    }

    private static String setUpEventLogging(final InternalCoreCCRequest coreCCRequest) {
        MDC.put("gridcapa-task-id", coreCCRequest.getId());
        return TIMESTAMP_FORMATTER.format(coreCCRequest.getTimestamp());
    }

    private void runRaoForAllAreas(final InternalCoreCCRequest coreCCRequest) {
        boolean semPreProcessingFailedOrDisabled = !coreCCRequest.isSemActivated();
        if (coreCCRequest.isSemActivated()) {
            semPreProcessingFailedOrDisabled = runRaoOnSemArea(coreCCRequest);
        }

        boolean continentalPreProcessingFailed = runRaoOnContinentalArea(coreCCRequest);

        // At this point, coreCCRequest necessarily contains a non-null continentalHourlyRaoResult and, is SEM is activated, a non-null semHourlyRaoResult

        // If both SEM and continental pre-processing failed, then we must export metadata only
        if (semPreProcessingFailedOrDisabled && continentalPreProcessingFailed) {
            saveMetadataWhenPreProcessingFailed(coreCCRequest);
        } else if (allRaoFailed(coreCCRequest)) {
            // If the RAO failed, we can't generate output files
            // TODO Should we generate a fallback metadata file as for the failed preprocessing?
        } else {
            coreCCPostProcessService.convertAndSaveReceivedRaoResult(coreCCRequest);
        }
    }

    private static boolean allRaoFailed(final InternalCoreCCRequest coreCCRequest) {
        final String raoFailureCode = HourlyRaoResult.ErrorCode.RAO_FAILURE.getCode();
        final HourlyRaoResult continentalHourlyRaoResult = coreCCRequest.getContinentalHourlyRaoResult();
        final HourlyRaoResult semHourlyRaoResult = coreCCRequest.getSemHourlyRaoResult();

        final boolean continentalRaoFailed = continentalHourlyRaoResult.getStatus() == HourlyRaoResult.Status.FAILURE
            && Objects.equals(continentalHourlyRaoResult.getErrorCodeString(), raoFailureCode);
        final boolean semRaoFailedOrDisabled = semHourlyRaoResult == null
            || semHourlyRaoResult.getStatus() == HourlyRaoResult.Status.FAILURE
            && Objects.equals(semHourlyRaoResult.getErrorCodeString(), raoFailureCode);

        return continentalRaoFailed && semRaoFailedOrDisabled;
    }

    private boolean runRaoOnSemArea(final InternalCoreCCRequest coreCCRequest) {
        final HourlyRaoRequest semHourlyRaoRequest = coreCCRequest.getSemHourlyRaoRequest();
        HourlyRaoResult semHourlyRaoResult = coreCCRequest.getSemHourlyRaoResult();

        if (semHourlyRaoResult == null) {
            // HourlyRaoResult is not yet defined in nominal situation: raoRunnerService hasn't been called yet
            semHourlyRaoResult = new HourlyRaoResult(semHourlyRaoRequest.getRaoRequestInstant());
            coreCCRequest.setSemHourlyRaoResult(semHourlyRaoResult);
        }

        final boolean semPreProcessingFailed = semHourlyRaoResult.getStatus().equals(HourlyRaoResult.Status.FAILURE);
        if (semPreProcessingFailed) {
            LOGGER.info("Skipping RAO on SEM area");
        } else {
            LOGGER.info("Launching RAO on SEM area. CoreCCRequest id is {}", coreCCRequest.getId());
            runRaoAndSetResponse(coreCCRequest, semHourlyRaoRequest, semHourlyRaoResult);
        }
        return semPreProcessingFailed;
    }

    private boolean runRaoOnContinentalArea(final InternalCoreCCRequest coreCCRequest) {
        final HourlyRaoRequest continentalHourlyRaoRequest = coreCCRequest.getContinentalHourlyRaoRequest();
        HourlyRaoResult continentalHourlyRaoResult = coreCCRequest.getContinentalHourlyRaoResult();

        if (continentalHourlyRaoResult == null) {
            // HourlyRaoResult is not yet defined in nominal situation: raoRunnerService hasn't been called yet
            continentalHourlyRaoResult = new HourlyRaoResult(continentalHourlyRaoRequest.getRaoRequestInstant());
            coreCCRequest.setContinentalHourlyRaoResult(continentalHourlyRaoResult);
        }

        final boolean continentalPreProcessingFailed = continentalHourlyRaoResult.getStatus().equals(HourlyRaoResult.Status.FAILURE);
        if (continentalPreProcessingFailed) {
            LOGGER.info("Skipping RAO on continental area");
        } else {
            LOGGER.info("Launching RAO on continental area. CoreCCRequest id is {}", coreCCRequest.getId());
            runRaoAndSetResponse(coreCCRequest, continentalHourlyRaoRequest, continentalHourlyRaoResult);
        }
        return continentalPreProcessingFailed;
    }

    private void runRaoAndSetResponse(final InternalCoreCCRequest coreCCRequest,
                                      final HourlyRaoRequest hourlyRaoRequest,
                                      final HourlyRaoResult hourlyRaoResult) {
        try {
            final RaoSuccessResponse raoResponse = raoRunnerService.run(
                hourlyRaoRequest.toRaoRequest(coreCCRequest.getId(), coreCCRequest.getRunId())
            );
            hourlyRaoResult.setRaoResponseData(raoResponse);
            hourlyRaoResult.setStatus(HourlyRaoResult.Status.SUCCESS);
        } catch (final CoreCCInternalException | CoreCCRaoException e) {
            handleRaoRunnerException(hourlyRaoResult, e);
        }
    }

    private void saveMetadataWhenPreProcessingFailed(final InternalCoreCCRequest coreCCRequest) {
        try {
            fileExporterHelper.exportMetadataToMinioWhenPreProcessingFailed(coreCCRequest);
        } catch (final Exception e) {
            throw new CoreCCInternalException("Exporting metadata failed when preProcessing failed", e);
        }
    }

    void handleRaoRunnerException(final HourlyRaoResult hourlyRaoResult,
                                  final Throwable exception) {
        hourlyRaoResult.setStatus(HourlyRaoResult.Status.FAILURE);
        hourlyRaoResult.setErrorCode(HourlyRaoResult.ErrorCode.RAO_FAILURE);
        if (exception instanceof final ResourceParseException resourceParseException) {
            // Sync scenario : exception details from rao-runner comes wrapped into ResourceParseException on json Api Error format.
            setErrorMessageAndLogIt(hourlyRaoResult, resourceParseException.getErrors().getErrors().getFirst().getDetail());
        } else if (exception.getCause() instanceof final ResourceParseException resourceParseException) {
            // Async scenario : exception details from rao-runner comes wrapped into ResourceParseException on json Api Error format, which is wrapped itself into a ConcurrencyException.
            setErrorMessageAndLogIt(hourlyRaoResult, resourceParseException.getErrors().getErrors().getFirst().getDetail());
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
