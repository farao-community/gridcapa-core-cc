/*
 * Copyright (c) 2023, RTE (http://www.rte-france.com)
 *  This Source Code Form is subject to the terms of the Mozilla Public
 *  License, v. 2.0. If a copy of the MPL was not distributed with this
 *  file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package com.farao_community.farao.gridcapa_core_cc.starter;

import com.farao_community.farao.gridcapa_core_cc.api.JsonApiConverter;
import com.farao_community.farao.gridcapa_core_cc.api.resource.CoreCCRequest;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.amqp.core.AmqpTemplate;
import org.springframework.amqp.core.Message;

import java.io.IOException;

/**
 * @author Ameni Walha {@literal <ameni.walha at rte-france.com>}
 */
class CoreCCClientTest {
    private final JsonApiConverter jsonApiConverter = new JsonApiConverter();

    @Test
    void checkThatClientHandleMessageCorrectly() throws IOException {
        AmqpTemplate amqpTemplate = Mockito.mock(AmqpTemplate.class);
        CoreCCClient client = new CoreCCClient(amqpTemplate, buildProperties());
        CoreCCRequest request = jsonApiConverter.fromJsonMessage(getClass().getResourceAsStream("/coreCCRequest.json").readAllBytes(), CoreCCRequest.class);
        Message responseMessage = Mockito.mock(Message.class);

        Mockito.when(responseMessage.getBody()).thenReturn(getClass().getResourceAsStream("/coreCCResponse.json").readAllBytes());
        Mockito.doNothing().when(amqpTemplate).send(Mockito.same("my-queue"), Mockito.any());
        Assertions.assertDoesNotThrow(() -> client.run(request));
    }

    private CoreCCClientProperties buildProperties() {
        CoreCCClientProperties properties = new CoreCCClientProperties();
        CoreCCClientProperties.BindingConfiguration amqpConfiguration = new CoreCCClientProperties.BindingConfiguration();
        amqpConfiguration.setDestination("my-queue");
        amqpConfiguration.setExpiration("60000");
        amqpConfiguration.setApplicationId("application-id");
        properties.setBinding(amqpConfiguration);
        return properties;
    }
}
