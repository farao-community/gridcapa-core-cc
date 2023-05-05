/*
 * Copyright (c) 2023, RTE (http://www.rte-france.com)
 *  This Source Code Form is subject to the terms of the Mozilla Public
 *  License, v. 2.0. If a copy of the MPL was not distributed with this
 *  file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package com.farao_community.farao.gridcapa_core_cc.starter;

import com.farao_community.farao.gridcapa_core_cc.api.JsonApiConverter;
import org.junit.jupiter.api.Test;

/**
 * @author Ameni Walha {@literal <ameni.walha at rte-france.com>}
 */
class CoreCCClientTest {
    private final JsonApiConverter jsonApiConverter = new JsonApiConverter();

    @Test
//    void checkThatClientHandleMessageCorrectly() throws IOException {
//        AmqpTemplate amqpTemplate = Mockito.mock(AmqpTemplate.class);
//        CoreCCClient client = new CoreCCClient(amqpTemplate, buildProperties());
//        CoreCCRequest request = jsonApiConverter.fromJsonMessage(getClass().getResourceAsStream("/coreCCRequest.json").readAllBytes(), CoreCCRequest.class);
//        Message responseMessage = Mockito.mock(Message.class);
//
//        Mockito.when(responseMessage.getBody()).thenReturn(getClass().getResourceAsStream("/coreCCResponse.json").readAllBytes());
//        Mockito.when(amqpTemplate.sendAndReceive(Mockito.same("my-queue"), Mockito.any())).thenReturn(responseMessage);
//        client.run(request);
//    }

    private CoreCCClientProperties buildProperties() {
        CoreCCClientProperties properties = new CoreCCClientProperties();
        CoreCCClientProperties.AmqpConfiguration amqpConfiguration = new CoreCCClientProperties.AmqpConfiguration();
        amqpConfiguration.setQueueName("my-queue");
        amqpConfiguration.setExpiration("60000");
        amqpConfiguration.setApplicationId("application-id");
        properties.setAmqp(amqpConfiguration);
        return properties;
    }
}
