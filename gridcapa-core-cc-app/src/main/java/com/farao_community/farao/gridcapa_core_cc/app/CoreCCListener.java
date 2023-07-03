/*
 * Copyright (c) 2021, RTE (http://www.rte-france.com)
 *  This Source Code Form is subject to the terms of the Mozilla Public
 *  License, v. 2.0. If a copy of the MPL was not distributed with this
 *  file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package com.farao_community.farao.gridcapa_core_cc.app;

import com.farao_community.farao.gridcapa.task_manager.api.TaskStatus;
import com.farao_community.farao.gridcapa.task_manager.api.TaskStatusUpdate;
import com.farao_community.farao.gridcapa_core_cc.api.JsonApiConverter;
import com.farao_community.farao.gridcapa_core_cc.api.exception.AbstractCoreCCException;
import com.farao_community.farao.gridcapa_core_cc.api.exception.CoreCCInvalidDataException;
import com.farao_community.farao.gridcapa_core_cc.api.resource.CoreCCRequest;
import com.farao_community.farao.gridcapa_core_cc.api.resource.CoreCCResponse;
import com.farao_community.farao.gridcapa_core_cc.api.resource.HourlyRaoResult;
import com.farao_community.farao.gridcapa_core_cc.api.resource.InternalCoreCCRequest;
import com.farao_community.farao.gridcapa_core_cc.app.configuration.AmqpMessagesConfiguration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.amqp.core.*;
import org.springframework.cloud.stream.function.StreamBridge;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * @author Godelaine de Montmorillon {@literal <godelaine.demontmorillon at rte-france.com>}
 * @author Philippe Edwards {@literal <philippe.edwards at rte-france.com>}
 */
@Component
public class CoreCCListener implements MessageListener {

    private static final Logger LOGGER = LoggerFactory.getLogger(CoreCCListener.class);
    private static final String APPLICATION_ID = "core-cc-runner";
    private static final String CONTENT_ENCODING = "UTF-8";
    private static final String CONTENT_TYPE = "application/vnd.api+json";
    private static final int PRIORITY = 1;
    private static final String TASK_STATUS_UPDATE = "task-status-update";

    private final JsonApiConverter jsonApiConverter;
    private final AmqpTemplate amqpTemplate;
    private final CoreCCHandler coreCCHandler;
    private final AmqpMessagesConfiguration amqpMessagesConfiguration;
    private final StreamBridge streamBridge;

    public CoreCCListener(CoreCCHandler coreCCHandler, AmqpTemplate amqpTemplate, AmqpMessagesConfiguration amqpMessagesConfiguration, StreamBridge streamBridge) {
        this.streamBridge = streamBridge;
        this.jsonApiConverter = new JsonApiConverter();
        this.coreCCHandler = coreCCHandler;
        this.amqpTemplate = amqpTemplate;
        this.amqpMessagesConfiguration = amqpMessagesConfiguration;
    }

    @Override
    public void onMessage(Message message) {
        String correlationId = message.getMessageProperties().getCorrelationId();
        // propagate in logs MDC the task id as an extra field to be able to match microservices logs with calculation tasks.
        // This should be done only once, as soon as the information to add in mdc is available.
        try {
            CoreCCRequest coreCCRequest = jsonApiConverter.fromJsonMessage(message.getBody(), CoreCCRequest.class);
            MDC.put("gridcapa-task-id", coreCCRequest.getId());
            streamBridge.send(TASK_STATUS_UPDATE, new TaskStatusUpdate(UUID.fromString(coreCCRequest.getId()), TaskStatus.RUNNING));
            InternalCoreCCRequest internalCoreCCRequest = new InternalCoreCCRequest(coreCCRequest);
            CoreCCResponse coreCCResponse = coreCCHandler.handleCoreCCRequest(internalCoreCCRequest);
            LOGGER.info("Core CC response written for timestamp {}", coreCCRequest.getTimestamp());
            sendCoreCCResponse(coreCCResponse, correlationId, internalCoreCCRequest.getHourlyRaoResult().getStatus());
        } catch (AbstractCoreCCException e) {
            LOGGER.error("Core CC exception occured", e);
            sendRequestErrorResponse(e, correlationId);
        } catch (RuntimeException e) {
            AbstractCoreCCException wrappingException = new CoreCCInvalidDataException("Unhandled exception: " + e.getMessage(), e);
            sendRequestErrorResponse(wrappingException, correlationId);
        }
    }

    private void sendRequestErrorResponse(AbstractCoreCCException e, String correlationId) {
        amqpTemplate.send(amqpMessagesConfiguration.coreCCResponseExchange().getName(), "", createErrorResponse(e, correlationId));
    }

    private void sendErrorResponse(String requestId, AbstractCoreCCException e, String replyTo, String correlationId) {
        streamBridge.send(TASK_STATUS_UPDATE, new TaskStatusUpdate(UUID.fromString(requestId), TaskStatus.ERROR));
        if (replyTo != null) {
            amqpTemplate.send(replyTo, createErrorResponse(e, correlationId));
        } else {
            amqpTemplate.send(amqpMessagesConfiguration.coreCCResponseExchange().getName(), "", createErrorResponse(e, correlationId));
        }
    }

    private void sendCoreCCResponse(CoreCCResponse coreCCResponse, String correlationId, HourlyRaoResult.Status status) {
        LOGGER.info("Updating task status to SUCCESS for timestamp {}", coreCCResponse.getId());
        if (status.equals(HourlyRaoResult.Status.SUCCESS)) {
            streamBridge.send(TASK_STATUS_UPDATE, new TaskStatusUpdate(UUID.fromString(coreCCResponse.getId()), TaskStatus.SUCCESS));
        } else if (status.equals(HourlyRaoResult.Status.FAILURE)) {
            streamBridge.send(TASK_STATUS_UPDATE, new TaskStatusUpdate(UUID.fromString(coreCCResponse.getId()), TaskStatus.ERROR));
        }
        amqpTemplate.send(amqpMessagesConfiguration.coreCCResponseExchange().getName(), "", createMessageResponse(coreCCResponse, correlationId));
    }

    private Message createMessageResponse(CoreCCResponse coreCCResponse, String correlationId) {
        return MessageBuilder.withBody(jsonApiConverter.toJsonMessage(coreCCResponse))
            .andProperties(buildMessageResponseProperties(correlationId))
            .build();
    }

    private Message createErrorResponse(AbstractCoreCCException exception, String correlationId) {
        return MessageBuilder.withBody(jsonApiConverter.toJsonMessage(exception))
            .andProperties(buildMessageResponseProperties(correlationId))
            .build();
    }

    private MessageProperties buildMessageResponseProperties(String correlationId) {
        return MessagePropertiesBuilder.newInstance()
            .setAppId(APPLICATION_ID)
            .setContentEncoding(CONTENT_ENCODING)
            .setContentType(CONTENT_TYPE)
            .setCorrelationId(correlationId)
            .setDeliveryMode(MessageDeliveryMode.NON_PERSISTENT)
            .setExpiration(amqpMessagesConfiguration.coreCCResponseExpiration())
            .setPriority(PRIORITY)
            .build();
    }

}
