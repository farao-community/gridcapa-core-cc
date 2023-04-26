/*
 * Copyright (c) 2021, RTE (http://www.rte-france.com)
 *  This Source Code Form is subject to the terms of the Mozilla Public
 *  License, v. 2.0. If a copy of the MPL was not distributed with this
 *  file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package com.farao_community.farao.gridcapa_core_cc.app;

import com.farao_community.farao.gridcapa_core_cc.api.JsonApiConverter;
import com.farao_community.farao.gridcapa_core_cc.api.exception.AbstractCoreCCException;
import com.farao_community.farao.gridcapa_core_cc.api.exception.CoreCCInternalException;
import com.farao_community.farao.gridcapa_core_cc.api.exception.CoreCCInvalidDataException;
import com.farao_community.farao.gridcapa_core_cc.api.resource.CoreCCRequest;
import com.farao_community.farao.gridcapa_core_cc.api.resource.CoreCCResponse;
import com.farao_community.farao.gridcapa.task_manager.api.TaskStatus;
import com.farao_community.farao.gridcapa.task_manager.api.TaskStatusUpdate;
import com.farao_community.farao.gridcapa_core_cc.app.configuration.AmqpMessagesConfiguration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.*;
import org.springframework.cloud.stream.function.StreamBridge;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * @author Ameni Walha {@literal <ameni.walha at rte-france.com>}
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
        String replyTo = message.getMessageProperties().getReplyTo();
        String correlationId = message.getMessageProperties().getCorrelationId();
        try {
            CoreCCRequest coreCCRequest = jsonApiConverter.fromJsonMessage(message.getBody(), CoreCCRequest.class);
            runCoreCCRequest(coreCCRequest, replyTo, correlationId);
        } catch (AbstractCoreCCException e) {
            LOGGER.error("Core cc exception occured", e);
            sendRequestErrorResponse(e, replyTo, correlationId);
        } catch (RuntimeException e) {
            AbstractCoreCCException wrappingException = new CoreCCInvalidDataException("Unhandled exception: " + e.getMessage(), e);
            sendRequestErrorResponse(wrappingException, replyTo, correlationId);
        }
    }

    private void sendRequestErrorResponse(AbstractCoreCCException e, String replyTo, String correlationId) {
        if (replyTo != null) {
            amqpTemplate.send(replyTo, createErrorResponse(e, correlationId));
        } else {
            amqpTemplate.send(amqpMessagesConfiguration.coreCCResponseExchange().getName(), "", createErrorResponse(e, correlationId));
        }
    }

    private void runCoreCCRequest(CoreCCRequest coreCCRequest, String replyTo, String correlationId) {
        try {
            LOGGER.info("Core cc request received: {}", coreCCRequest);
            streamBridge.send(TASK_STATUS_UPDATE, new TaskStatusUpdate(UUID.fromString(coreCCRequest.getId()), TaskStatus.RUNNING));
            CoreCCResponse coreCCResponse = coreCCHandler.handleCoreCCRequest(coreCCRequest);
            sendCoreCCResponse(coreCCResponse, replyTo, correlationId);
        } catch (AbstractCoreCCException e) {
            LOGGER.error("Core cc exception occured", e);
            sendErrorResponse(coreCCRequest.getId(), e, replyTo, correlationId);
        } catch (RuntimeException e) {
            LOGGER.error("Unknown exception occured", e);
            AbstractCoreCCException wrappingException = new CoreCCInternalException("Unknown exception", e);
            sendErrorResponse(coreCCRequest.getId(), wrappingException, replyTo, correlationId);
        }
    }

    private void sendErrorResponse(String requestId, AbstractCoreCCException e, String replyTo, String correlationId) {
        streamBridge.send(TASK_STATUS_UPDATE, new TaskStatusUpdate(UUID.fromString(requestId), TaskStatus.ERROR));
        if (replyTo != null) {
            amqpTemplate.send(replyTo, createErrorResponse(e, correlationId));
        } else {
            amqpTemplate.send(amqpMessagesConfiguration.coreCCResponseExchange().getName(), "", createErrorResponse(e, correlationId));
        }
    }

    private void sendCoreCCResponse(CoreCCResponse coreCCResponse, String replyTo, String correlationId) {
        streamBridge.send(TASK_STATUS_UPDATE, new TaskStatusUpdate(UUID.fromString(coreCCResponse.getId()), TaskStatus.SUCCESS));
        if (replyTo != null) {
            amqpTemplate.send(replyTo, createMessageResponse(coreCCResponse, correlationId));
        } else {
            amqpTemplate.send(amqpMessagesConfiguration.coreCCResponseExchange().getName(), "", createMessageResponse(coreCCResponse, correlationId));
        }
        LOGGER.info("Core cc response sent: {}", coreCCResponse);
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
