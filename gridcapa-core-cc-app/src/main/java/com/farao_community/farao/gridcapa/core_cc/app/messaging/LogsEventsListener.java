/*
 * Copyright (c) 2022, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package com.farao_community.farao.gridcapa.core_cc.app.messaging;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.util.*;
import java.util.function.Consumer;

@Service
public class LogsEventsListener {

    ObjectMapper objectMapper = new ObjectMapper().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    // key= RAO-request id = raoIntegrationTaskId_Timestamp   // value: set of logs sorted  by timestamp
    private final Map<String, SortedSet<String>> logsByInstant = new HashMap<>();
    private static final Logger LOGGER = LoggerFactory.getLogger(LogsEventsListener.class);

    @Bean
    public Consumer<Flux<String>> handleTaskLogEventUpdate() {
        return f -> f
            .onErrorContinue((t, r) -> LOGGER.error(t.getMessage(), t))
            .subscribe(this::saveLogsByTimestamp);
    }

    void saveLogsByTimestamp(String loggerEventString) {
        try {
            RaoLogEvent loggerEvent = objectMapper.readValue(loggerEventString, RaoLogEvent.class);
            logsByInstant.putIfAbsent(loggerEvent.getId(), createNewSet(loggerEvent));
            logsByInstant.computeIfPresent(loggerEvent.getId(), (key, logsList) -> {
                logsList.add(loggerEvent.toString());
                return logsList;
            });
        } catch (Exception e) {
            LOGGER.warn("Error occurred while reading rao logs from queue, cause: " + e.getMessage(), e);
        }
    }

    private SortedSet<String> createNewSet(RaoLogEvent loggerEvent) {
        SortedSet<String> logsForTs = new TreeSet<>();
        logsForTs.add(loggerEvent.toString());
        return logsForTs;
    }

    public Map<String, SortedSet<String>> getLogsByInstant() {
        return logsByInstant;
    }
}
