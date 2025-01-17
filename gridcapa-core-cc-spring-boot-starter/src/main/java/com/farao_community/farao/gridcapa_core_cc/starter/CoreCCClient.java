/*
 * Copyright (c) 2023, RTE (http://www.rte-france.com)
 *  This Source Code Form is subject to the terms of the Mozilla Public
 *  License, v. 2.0. If a copy of the MPL was not distributed with this
 *  file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package com.farao_community.farao.gridcapa_core_cc.starter;

import com.farao_community.farao.gridcapa_core_cc.api.JsonApiConverter;
import com.farao_community.farao.gridcapa_core_cc.api.resource.CoreCCRequest;
import org.springframework.amqp.core.AmqpTemplate;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageBuilder;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.core.MessagePropertiesBuilder;
import org.springframework.stereotype.Component;

/**
 * @author Ameni Walha {@literal <ameni.walha at rte-france.com>}
 */
@Component
public class CoreCCClient {
    private static final int DEFAULT_PRIORITY = 1;
    private static final String CONTENT_ENCODING = "UTF-8";
    private static final String CONTENT_TYPE = "application/vnd.api+json";

    private final AmqpTemplate amqpTemplate;
    private final CoreCCClientProperties coreCCClientProperties;
    private final JsonApiConverter jsonConverter;

    public CoreCCClient(final AmqpTemplate amqpTemplate, final CoreCCClientProperties coreCCClientProperties) {
        this.amqpTemplate = amqpTemplate;
        this.coreCCClientProperties = coreCCClientProperties;
        this.jsonConverter = new JsonApiConverter();
    }

    public void run(final CoreCCRequest coreCCRequest, final int priority) {
        amqpTemplate.send(coreCCClientProperties.binding().destination(),
                coreCCClientProperties.binding().routingKey(),
                buildMessage(coreCCRequest, priority));
    }

    public void run(final CoreCCRequest coreCCRequest) {
        run(coreCCRequest, DEFAULT_PRIORITY);
    }

    public Message buildMessage(final CoreCCRequest coreCCRequest, final int priority) {
        return MessageBuilder.withBody(jsonConverter.toJsonMessage(coreCCRequest))
                .andProperties(buildMessageProperties(priority))
                .build();
    }

    private MessageProperties buildMessageProperties(final int priority) {
        return MessagePropertiesBuilder.newInstance()
                .setAppId(coreCCClientProperties.binding().applicationId())
                .setContentEncoding(CONTENT_ENCODING)
                .setContentType(CONTENT_TYPE)
                .setDeliveryMode(MessageDeliveryMode.NON_PERSISTENT)
                .setExpiration(coreCCClientProperties.binding().expiration())
                .setPriority(priority)
                .build();
    }
}
