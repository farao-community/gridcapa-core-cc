/*
 * Copyright (c) 2023, RTE (http://www.rte-france.com)
 *  This Source Code Form is subject to the terms of the Mozilla Public
 *  License, v. 2.0. If a copy of the MPL was not distributed with this
 *  file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package com.farao_community.farao.gridcapa_core_cc.starter;

import org.springframework.amqp.core.AmqpTemplate;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * @author Ameni Walha {@literal <ameni.walha at rte-france.com>}
 */
@Configuration
@EnableConfigurationProperties(CoreCCClientProperties.class)
public class CoreCCClientAutoConfiguration {
    private final CoreCCClientProperties clientProperties;

    public CoreCCClientAutoConfiguration(CoreCCClientProperties clientProperties) {
        this.clientProperties = clientProperties;
    }

    @Bean
    public CoreCCClient coreCCClient(AmqpTemplate amqpTemplate) {
        return new CoreCCClient(amqpTemplate, clientProperties);
    }
}
