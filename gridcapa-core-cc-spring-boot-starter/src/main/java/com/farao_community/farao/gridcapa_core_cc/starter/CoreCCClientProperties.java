/*
 * Copyright (c) 2023, RTE (http://www.rte-france.com)
 *  This Source Code Form is subject to the terms of the Mozilla Public
 *  License, v. 2.0. If a copy of the MPL was not distributed with this
 *  file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package com.farao_community.farao.gridcapa_core_cc.starter;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * @author Ameni Walha {@literal <ameni.walha at rte-france.com>}
 */
@ConfigurationProperties("core-cc-runner")
public class CoreCCClientProperties {
    private AmqpConfiguration amqp;

    public CoreCCClientProperties.AmqpConfiguration getAmqp() {
        return amqp;
    }

    public void setAmqp(CoreCCClientProperties.AmqpConfiguration amqp) {
        this.amqp = amqp;
    }

    public static class AmqpConfiguration {
        private String queueName;
        private String expiration;
        private String applicationId;

        public String getQueueName() {
            return queueName;
        }

        public void setQueueName(String queueName) {
            this.queueName = queueName;
        }

        public String getExpiration() {
            return expiration;
        }

        public void setExpiration(String expiration) {
            this.expiration = expiration;
        }

        public String getApplicationId() {
            return applicationId;
        }

        public void setApplicationId(String applicationId) {
            this.applicationId = applicationId;
        }
    }
}
