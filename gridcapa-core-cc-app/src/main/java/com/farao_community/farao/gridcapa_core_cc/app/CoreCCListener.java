/*
 * Copyright (c) 2023, RTE (http://www.rte-france.com)
 *  This Source Code Form is subject to the terms of the Mozilla Public
 *  License, v. 2.0. If a copy of the MPL was not distributed with this
 *  file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package com.farao_community.farao.gridcapa_core_cc.app;

import com.farao_community.farao.gridcapa.task_manager.api.TaskStatus;
import com.farao_community.farao.gridcapa.task_manager.api.TaskStatusUpdate;
import com.farao_community.farao.gridcapa_core_cc.api.JsonApiConverter;
import com.farao_community.farao.gridcapa_core_cc.api.exception.AbstractCoreCCException;
import com.farao_community.farao.gridcapa_core_cc.api.resource.CoreCCRequest;
import com.farao_community.farao.gridcapa_core_cc.api.resource.HourlyRaoResult;
import com.farao_community.farao.gridcapa_core_cc.api.resource.InternalCoreCCRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageListener;
import org.springframework.cloud.stream.function.StreamBridge;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * @author Godelaine de Montmorillon {@literal <godelaine.demontmorillon at rte-france.com>}
 * @author Philippe Edwards {@literal <philippe.edwards at rte-france.com>}
 */
@Component
public class CoreCCListener implements MessageListener {

    private static final Logger LOGGER = LoggerFactory.getLogger(CoreCCListener.class);
    private static final String TASK_STATUS_UPDATE = "task-status-update";

    private final JsonApiConverter jsonApiConverter;
    private final CoreCCHandler coreCCHandler;
    private final StreamBridge streamBridge;

    public CoreCCListener(CoreCCHandler coreCCHandler, StreamBridge streamBridge) {
        this.streamBridge = streamBridge;
        this.jsonApiConverter = new JsonApiConverter();
        this.coreCCHandler = coreCCHandler;
    }

    @Override
    public void onMessage(Message message) {
        try {
            // propagate in logs MDC the task id as an extra field to be able to match microservices logs with calculation tasks.
            // This should be done only once, as soon as the information to add in mdc is available.
            CoreCCRequest coreCCRequest = jsonApiConverter.fromJsonMessage(message.getBody(), CoreCCRequest.class);
            MDC.put("gridcapa-task-id", coreCCRequest.getId());
            streamBridge.send(TASK_STATUS_UPDATE, new TaskStatusUpdate(UUID.fromString(coreCCRequest.getId()), TaskStatus.RUNNING));
            InternalCoreCCRequest internalCoreCCRequest = new InternalCoreCCRequest(coreCCRequest);
            coreCCHandler.handleCoreCCRequest(internalCoreCCRequest);
            LOGGER.info("Core CC response written for timestamp {}", coreCCRequest.getTimestamp());
            updateTaskStatus(internalCoreCCRequest.getId(), internalCoreCCRequest.getHourlyRaoResult().getStatus(), coreCCRequest.getTimestamp());
        } catch (AbstractCoreCCException e) {
            LOGGER.error("Core CC exception occured", e);
        } catch (RuntimeException e) {
            LOGGER.error("Unhandled exception: ", e);
        }
    }

    private void updateTaskStatus(String internalRequestId, HourlyRaoResult.Status status, OffsetDateTime timestamp) {
        if (status.equals(HourlyRaoResult.Status.SUCCESS)) {
            streamBridge.send(TASK_STATUS_UPDATE, new TaskStatusUpdate(UUID.fromString(internalRequestId), TaskStatus.SUCCESS));
            LOGGER.info("Updating task status to SUCCESS for timestamp {}", timestamp);
        } else if (status.equals(HourlyRaoResult.Status.FAILURE)) {
            streamBridge.send(TASK_STATUS_UPDATE, new TaskStatusUpdate(UUID.fromString(internalRequestId), TaskStatus.ERROR));
            LOGGER.info("Updating task status to ERROR for timestamp {}", timestamp);
        }
    }
}
