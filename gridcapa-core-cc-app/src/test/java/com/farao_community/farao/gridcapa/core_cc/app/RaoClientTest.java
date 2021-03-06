/*
 * Copyright (c) 2022, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package com.farao_community.farao.gridcapa.core_cc.app;

import com.farao_community.farao.rao_runner.api.JsonApiConverter;
import com.farao_community.farao.rao_runner.api.resource.RaoRequest;
import com.farao_community.farao.rao_runner.api.resource.RaoResponse;
import com.farao_community.farao.rao_runner.starter.RaoRunnerClient;
import com.farao_community.farao.rao_runner.starter.RaoRunnerClientProperties;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.amqp.core.AmqpTemplate;
import org.springframework.amqp.core.Message;

import java.io.IOException;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * @author Mohamed BenRejeb {@literal <mohamed.ben-rejeb at rte-france.com>}
 */
class RaoClientTest {
    private final JsonApiConverter jsonConverter = new JsonApiConverter();

    @Test
    void checkThatRaoRunnerClientHandlesMessagesCorrectly() throws IOException {
        AmqpTemplate amqpTemplate = Mockito.mock(AmqpTemplate.class);
        RaoRunnerClient client = new RaoRunnerClient(amqpTemplate, buildProperties());
        RaoRequest raoRequest = jsonConverter.fromJsonMessage(getClass().getResourceAsStream("/raoRequestMessage.json").readAllBytes(), RaoRequest.class);

        Message responseMessage = Mockito.mock(Message.class);
        Mockito.when(responseMessage.getBody()).thenReturn(getClass().getResourceAsStream("/raoResponseMessage.json").readAllBytes());
        Mockito.when(amqpTemplate.sendAndReceive(Mockito.same("my-queue"), Mockito.any())).thenReturn(responseMessage);

        RaoResponse raoResponse = client.runRao(raoRequest);

        assertEquals("instant", raoResponse.getInstant().get());
        assertEquals(Instant.ofEpochSecond(1637052884, 944727000), raoResponse.getComputationStartInstant());
        assertEquals(Instant.ofEpochSecond(1647057884, 934927000), raoResponse.getComputationEndInstant());
    }

    private RaoRunnerClientProperties buildProperties() {
        RaoRunnerClientProperties properties = new RaoRunnerClientProperties();
        RaoRunnerClientProperties.AmqpConfiguration amqpConfiguration = new RaoRunnerClientProperties.AmqpConfiguration();
        amqpConfiguration.setQueueName("my-queue");
        amqpConfiguration.setExpiration("10000");
        properties.setAmqp(amqpConfiguration);
        return properties;
    }
}
