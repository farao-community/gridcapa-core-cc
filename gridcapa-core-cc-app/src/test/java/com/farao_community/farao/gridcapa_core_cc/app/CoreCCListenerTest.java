/*
 * Copyright (c) 2021, RTE (http://www.rte-france.com)
 *  This Source Code Form is subject to the terms of the Mozilla Public
 *  License, v. 2.0. If a copy of the MPL was not distributed with this
 *  file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package com.farao_community.farao.gridcapa_core_cc.app;

import com.farao_community.farao.gridcapa_core_cc.api.resource.CoreCCResponse;
import com.farao_community.farao.gridcapa_core_cc.api.resource.InternalCoreCCRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.core.AmqpTemplate;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageBuilder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.cloud.stream.function.StreamBridge;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.Instant;

/**
 * @author Ameni Walha {@literal <ameni.walha at rte-france.com>}
 */
@SpringBootTest
@ExtendWith(MockitoExtension.class)
class CoreCCListenerTest {
    @MockBean
    public CoreCCHandler coreCCHandler;

    @Autowired
    public CoreCCListener coreCCListener;

    @MockBean
    private StreamBridge streamBridge;

    @Autowired
    private AmqpTemplate amqpTemplate;

    @TestConfiguration
    static class ProcessPublicationServiceTestConfiguration {
        @Bean
        @Primary
        public AmqpTemplate amqpTemplate() {
            return Mockito.mock(AmqpTemplate.class);
        }
    }

    @BeforeEach
    public void resetMocks() {
        Mockito.reset(amqpTemplate, coreCCHandler, streamBridge);
    }

    @Test
    void checkThatCorrectMessageIsHandledCorrectly() throws URISyntaxException, IOException {
        byte[] correctMessage = Files.readAllBytes(Paths.get(getClass().getResource("/validRequest.json").toURI()));
        Message message = MessageBuilder.withBody(correctMessage).build();
        String resultFileUrl = "fileUrl";
        Instant computationStartInstant = Instant.now();
        Instant computationEndInstant = Instant.now();
        CoreCCResponse coreCCResponse = new CoreCCResponse("c7fc89da-dcd7-40d2-8d63-b8aef0a1ecdf", computationStartInstant, computationEndInstant);
        Mockito.when(coreCCHandler.handleCoreCCRequest(Mockito.any(InternalCoreCCRequest.class))).thenReturn(coreCCResponse);
        coreCCListener.onMessage(message);
        Mockito.verify(streamBridge, Mockito.times(1)).send(Mockito.anyString(), Mockito.any());
        Mockito.verify(coreCCHandler, Mockito.times(1)).handleCoreCCRequest(Mockito.any(InternalCoreCCRequest.class));
    }

    @Test
    void checkThatInvalidMessageReturnsError() throws URISyntaxException, IOException {
        byte[] invalidMessage = Files.readAllBytes(Paths.get(getClass().getResource("/invalidRequest.json").toURI()));
        Message message = MessageBuilder.withBody(invalidMessage).build();
        coreCCListener.onMessage(message);
        Mockito.verify(streamBridge, Mockito.times(0)).send(Mockito.anyString(), Mockito.any());
        Mockito.verify(coreCCHandler, Mockito.times(0)).handleCoreCCRequest(Mockito.any(InternalCoreCCRequest.class));
    }

}
