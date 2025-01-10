/*
 * Copyright (c) 2023, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.farao_community.farao.gridcapa_core_cc.app.configuration;

import com.farao_community.farao.gridcapa_core_cc.app.CoreCCListener;
import org.springframework.amqp.core.AsyncAmqpTemplate;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.AsyncRabbitTemplate;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.rabbit.listener.MessageListenerContainer;
import org.springframework.amqp.rabbit.listener.SimpleMessageListenerContainer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Optional;

/**
 * @author Ameni Walha {@literal <ameni.walha at rte-france.com>}
 */
@Configuration
public class AmqpMessagesBeans {
    private final AmqpMessagesConfiguration amqpConfiguration;

    public AmqpMessagesBeans(final AmqpMessagesConfiguration amqpConfiguration) {
        this.amqpConfiguration = amqpConfiguration;
    }

    @Bean
    AsyncAmqpTemplate asyncTemplate(RabbitTemplate rabbitTemplate) {
        AsyncRabbitTemplate asyncTemplate = new AsyncRabbitTemplate(rabbitTemplate);
        asyncTemplate.setReceiveTimeout(amqpConfiguration.getAsyncTimeOutInMilliseconds());
        return asyncTemplate;
    }

    @Bean
    public Queue coreCCRequestQueue() {
        return new Queue(amqpConfiguration.getRequestDestination());
    }

    @Bean
    public TopicExchange coreCCTopicExchange() {
        return new TopicExchange(amqpConfiguration.getRequestDestination());
    }

    @Bean
    public Binding coreCCRequestBinding() {
        return BindingBuilder.bind(coreCCRequestQueue()).to(coreCCTopicExchange()).with(Optional.ofNullable(amqpConfiguration.getRequestRoutingKey()).orElse("#"));
    }

    @Bean
    public MessageListenerContainer messageListenerContainer(ConnectionFactory connectionFactory,
                                                             Queue coreCCRequestQueue,
                                                             CoreCCListener listener) {
        SimpleMessageListenerContainer simpleMessageListenerContainer = new SimpleMessageListenerContainer();
        simpleMessageListenerContainer.setConnectionFactory(connectionFactory);
        simpleMessageListenerContainer.setQueues(coreCCRequestQueue);
        simpleMessageListenerContainer.setMessageListener(listener);
        return simpleMessageListenerContainer;
    }
}