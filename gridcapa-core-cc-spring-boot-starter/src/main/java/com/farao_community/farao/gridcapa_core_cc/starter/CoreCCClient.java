/*
 * Copyright (c) 2023, RTE (http://www.rte-france.com)
 *  This Source Code Form is subject to the terms of the Mozilla Public
 *  License, v. 2.0. If a copy of the MPL was not distributed with this
 *  file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package com.farao_community.farao.gridcapa_core_cc.starter;

import com.farao_community.farao.gridcapa_core_cc.api.JsonApiConverter;
import com.farao_community.farao.gridcapa_core_cc.api.resource.CoreCCRequest;
import org.springframework.amqp.core.*;

/**
 * @author Ameni Walha {@literal <ameni.walha at rte-france.com>}
 */
public class CoreCCClient {
    private static final int DEFAULT_PRIORITY = 1;
    private static final String CONTENT_ENCODING = "UTF-8";
    private static final String CONTENT_TYPE = "application/vnd.api+json";

    private final AmqpTemplate amqpTemplate;
    private final CoreCCClientProperties coreCCClientProperties;
    private final JsonApiConverter jsonConverter;

    public CoreCCClient(AmqpTemplate amqpTemplate, CoreCCClientProperties coreCCClientProperties) {
        this.amqpTemplate = amqpTemplate;
        this.coreCCClientProperties = coreCCClientProperties;
        this.jsonConverter = new JsonApiConverter();
    }

    public void run(CoreCCRequest coreCCRequest, int priority) {
        amqpTemplate.send(coreCCClientProperties.getBinding().getDestination(),
                coreCCClientProperties.getBinding().getRoutingKey(),
                buildMessage(coreCCRequest, priority));
    }

    public void run(CoreCCRequest coreCCRequest) {
        run(coreCCRequest, DEFAULT_PRIORITY);
    }

    public Message buildMessage(CoreCCRequest coreCCRequest, int priority) {
        return MessageBuilder.withBody(jsonConverter.toJsonMessage(coreCCRequest))
                .andProperties(buildMessageProperties(priority))
                .build();
    }

    private MessageProperties buildMessageProperties(int priority) {
        return MessagePropertiesBuilder.newInstance()
                .setAppId(coreCCClientProperties.getBinding().getApplicationId())
                .setContentEncoding(CONTENT_ENCODING)
                .setContentType(CONTENT_TYPE)
                .setDeliveryMode(MessageDeliveryMode.NON_PERSISTENT)
                .setExpiration(coreCCClientProperties.getBinding().getExpiration())
                .setPriority(priority)
                .build();
    }
}
