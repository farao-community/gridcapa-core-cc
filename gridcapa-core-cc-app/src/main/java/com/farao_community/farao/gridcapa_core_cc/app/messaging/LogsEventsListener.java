/*
 * Copyright (c) 2022, RTE (http://www.rte-france.com)
 */
package com.farao_community.farao.gridcapa_core_cc.app.messaging;

import com.farao_community.farao.gridcapa_core_cc.app.util.TaskUtil;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.time.OffsetDateTime;
import java.util.*;
import java.util.function.Consumer;

@Service
public class LogsEventsListener {

    ObjectMapper objectMapper = new ObjectMapper().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    // key = timestamp, List of messages written on that timestamp (can be plural)
    private final Map<String, Set<String>> logsByInstant = new TreeMap<>((o1, o2) -> {
        OffsetDateTime offsetDateTime = OffsetDateTime.parse(o1);
        OffsetDateTime offsetDateTimeOther = OffsetDateTime.parse(o2);
        return offsetDateTime.compareTo(offsetDateTimeOther);
    });
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
            // Only keep logs from rao-runner
            // TODO : only keep logs from this coreCCRequest : check id ? id seems to be concatenated to a timestamp starting with _2023
            // Ex : b17a33c0-83a3-4b22-9208-ef46ede44fdb_2023-02-02T23:00:00Z
            String loggerId = loggerEvent.getId().substring(0, loggerEvent.getId().indexOf("_") + 1);
            LOGGER.info("loggerId is {} and taskId is {}", loggerId, TaskUtil.getTaskId());
            if (loggerEvent.getServiceName().equals("rao-runner-app") && loggerId.equals(TaskUtil.getTaskId())) {
                logsByInstant.putIfAbsent(loggerEvent.getTimestamp(), createNewSet(loggerEvent));
                logsByInstant.computeIfPresent(loggerEvent.getTimestamp(), (key, val) -> {
                    val.add(loggerEvent.toString());
                    return val;
                });
            }
        } catch (Exception e) {
            LOGGER.warn("Error occurred while reading rao logs from queue, cause: {} , {}", e.getMessage(), e);
        }
    }

    public Map<String, Set<String>> getLogsByInstant() {
        return logsByInstant;
    }

    private Set<String> createNewSet(RaoLogEvent loggerEvent) {
        Set<String> logsForTs = new HashSet<>();
        logsForTs.add(loggerEvent.toString());
        return logsForTs;
    }
}
