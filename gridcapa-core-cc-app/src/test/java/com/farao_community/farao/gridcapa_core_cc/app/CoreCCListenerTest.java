/*
 * Copyright (c) 2023, RTE (http://www.rte-france.com)
 *  This Source Code Form is subject to the terms of the Mozilla Public
 *  License, v. 2.0. If a copy of the MPL was not distributed with this
 *  file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package com.farao_community.farao.gridcapa_core_cc.app;

import com.farao_community.farao.gridcapa_core_cc.api.exception.CoreCCInvalidDataException;
import com.farao_community.farao.gridcapa_core_cc.api.resource.InternalCoreCCRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.Logger;
import org.springframework.amqp.core.AmqpTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.cloud.stream.function.StreamBridge;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Paths;

/**
 * @author Ameni Walha {@literal <ameni.walha at rte-france.com>}
 */
@SpringBootTest
@ExtendWith(MockitoExtension.class)
class CoreCCListenerTest {
    @MockitoBean
    public CoreCCHandler coreCCHandler;

    @MockitoBean
    public Logger businessLogger;

    @Autowired
    public CoreCCListener coreCCListener;

    @MockitoBean
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
    void resetMocks() {
        Mockito.reset(amqpTemplate, coreCCHandler, streamBridge);
    }

    @Test
    void checkThatCorrectMessageIsHandledCorrectly() throws URISyntaxException, IOException {
        final byte[] correctMessage = Files.readAllBytes(Paths.get(getClass().getResource("/validRequest.json").toURI()));
        coreCCListener.launchCoreRequest(correctMessage);
        Mockito.verify(streamBridge, Mockito.times(2)).send(Mockito.anyString(), Mockito.any());
        Mockito.verify(coreCCHandler, Mockito.times(1)).handleCoreCCRequest(Mockito.any(InternalCoreCCRequest.class));
    }

    @Test
    void checkThatInvalidMessageReturnsError() throws URISyntaxException, IOException {
        final byte[] invalidMessage = Files.readAllBytes(Paths.get(getClass().getResource("/invalidRequest.json").toURI()));
        coreCCListener.launchCoreRequest(invalidMessage);
        Mockito.verify(streamBridge, Mockito.times(0)).send(Mockito.anyString(), Mockito.any());
        Mockito.verify(coreCCHandler, Mockito.times(0)).handleCoreCCRequest(Mockito.any(InternalCoreCCRequest.class));
    }

    @Test
    void checkWithCoreCcError() throws URISyntaxException, IOException {
        final byte[] invalidMessage = Files.readAllBytes(Paths.get(getClass().getResource("/validRequest.json").toURI()));
        Mockito.doThrow(new CoreCCInvalidDataException("test")).when(coreCCHandler).handleCoreCCRequest(Mockito.any(InternalCoreCCRequest.class));
        coreCCListener.launchCoreRequest(invalidMessage);
        Mockito.verify(streamBridge, Mockito.times(2)).send(Mockito.anyString(), Mockito.any());
        Mockito.verify(coreCCHandler, Mockito.times(1)).handleCoreCCRequest(Mockito.any(InternalCoreCCRequest.class));
        Mockito.verify(businessLogger, Mockito.times(1)).error("{}: {}", "Core CC exception occurred", "test");
    }

}
