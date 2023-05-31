/*
 * Copyright (c) 2022, RTE (http://www.rte-france.com)
 */
package com.farao_community.farao.gridcapa_core_cc.app.messaging;

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

    String taskId;
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

    public void setTaskId(String taskIdToBeSet) {
        taskId = taskIdToBeSet;
    }

    void saveLogsByTimestamp(String loggerEventString) {
        try {
            RaoLogEvent loggerEvent = objectMapper.readValue(loggerEventString, RaoLogEvent.class);
            // Only keep logs from rao-runner
            if (Objects.nonNull(loggerEvent.getId())) {
                String loggerId = loggerEvent.getId().substring(0, loggerEvent.getId().indexOf("_"));
                if (loggerEvent.getServiceName().equals("rao-runner-app") && loggerId.equals(taskId)) {
                    logsByInstant.putIfAbsent(loggerEvent.getTimestamp(), createNewSet(loggerEvent));
                    logsByInstant.computeIfPresent(loggerEvent.getTimestamp(), (key, val) -> {
                        val.add(loggerEvent.toString());
                        return val;
                    });
                }
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
