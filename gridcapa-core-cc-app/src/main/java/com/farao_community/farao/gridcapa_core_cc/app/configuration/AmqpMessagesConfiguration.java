/*
 * Copyright (c) 2023, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.farao_community.farao.gridcapa_core_cc.app.configuration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

/**
 * @author Ameni Walha {@literal <ameni.walha at rte-france.com>}
 */
@Configuration
public class AmqpMessagesConfiguration {

    @Value("${core-cc-runner.bindings.request.destination}")
    private String requestDestination;
    @Value("${core-cc-runner.bindings.request.routing-key}")
    private String requestRoutingKey;
    @Value("${core-cc-runner.async-time-out}")
    private long asyncTimeOut;

    public String getRequestDestination() {
        return requestDestination;
    }

    public String getRequestRoutingKey() {
        return requestRoutingKey;
    }

    public long getAsyncTimeOutInMilliseconds() {
        return asyncTimeOut;
    }

    public String getAsyncTimeOutInMinutes() {
        return String.format("%.2f", (double) asyncTimeOut / 1000 / 60);
    }
}
