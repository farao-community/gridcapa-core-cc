/*
 * Copyright (c) 2022, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package com.farao_community.farao.gridcapa.core_cc.app.postprocessing;

import com.farao_community.farao.gridcapa.core_cc.app.entities.RaoIntegrationTask;
import com.farao_community.farao.gridcapa.core_cc.app.messaging.LogsEventsListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.PrintWriter;
import java.util.Map;
import java.util.SortedSet;

/**
 * @author Mohamed BenRejeb {@literal <mohamed.ben-rejeb at rte-france.com>}
 */
@Service
public class LogsExporter {
    private final LogsEventsListener logsEventsListener;
    private static final Logger LOGGER = LoggerFactory.getLogger(LogsExporter.class);

    public LogsExporter(LogsEventsListener logsEventsListener) {
        this.logsEventsListener = logsEventsListener;
    }

    public void exportLogs(RaoIntegrationTask raoIntegrationTask) {
        Map<String, SortedSet<String>> logsMap = logsEventsListener.getLogsByInstant();
        logsMap.forEach((k, v) -> {
            String instant = k.substring(k.indexOf("_") + 1);
            String fileName = OutputFileNameUtil.generateLogsFileName(instant, raoIntegrationTask);
            File targetFile = new File(raoIntegrationTask.getDailyOutputs().getLogsTmpOutputPath(), fileName); //NOSONAR
            try {
                PrintWriter pw = new PrintWriter(new FileOutputStream(targetFile));
                for (String logRecord : v) {
                    pw.println(logRecord);
                }
                pw.close();
            } catch (FileNotFoundException e) {
                LOGGER.error("Error occurred while writing Rao logs to file for timestamp '{}'", instant);
            }
        });
        logsMap.clear(); // clearing memory to prepare for next raoi computation
    }

}
