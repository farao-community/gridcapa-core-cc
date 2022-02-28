/*
 * Copyright (c) 2022, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package com.farao_community.farao.gridcapa.core_cc.app.postprocessing;

import com.farao_community.farao.gridcapa.core_cc.app.constants.OutputsNamingRules;
import com.farao_community.farao.gridcapa.core_cc.app.entities.RaoIntegrationTask;
import com.farao_community.farao.gridcapa.core_cc.app.util.IntervalUtil;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;

/**
 * @author Mohamed BenRejeb {@literal <mohamed.ben-rejeb at rte-france.com>}
 */
public final class OutputFileNameUtil {

    private OutputFileNameUtil() {
    }

    public static String generateRaoRequestAckFileName(RaoIntegrationTask raoIntegrationTask) {
        return OutputsNamingRules.RAO_REQUEST_ACK_FILENAME_FORMATTER.format(IntervalUtil.getBusinessDay(raoIntegrationTask.getTimeInterval()))
                .replace("0V", String.format("%02d", raoIntegrationTask.getVersion()));
    }

    public static String generateRaoIResponseFileName(RaoIntegrationTask raoIntegrationTask) {
        return OutputsNamingRules.RAO_INTEGRATION_RESPONSE_FILENAME_FORMATTER.format(IntervalUtil.getBusinessDay(raoIntegrationTask.getTimeInterval()))
                .replace("0V", String.format("%02d", raoIntegrationTask.getVersion()));
    }

    public static String generateUctFileName(String instant, int version) {
        String output = OutputsNamingRules.UCT_FILENAME_FORMATTER.format(Instant.parse(instant));
        output = output.replace("2D0", "2D" + Instant.parse(instant).atZone(OutputsNamingRules.ZONE_ID).getDayOfWeek().getValue())
                .replace("_UXV", "_UX" + version);
        return handle25TimestampCase(output, instant);
    }

    public static String generateCneFileName(String instant, RaoIntegrationTask raoIntegrationTask) {
        String output = OutputsNamingRules.CNE_FILENAME_FORMATTER.format(Instant.parse(instant))
                .replace("-v0-", "-v" + raoIntegrationTask.getVersion() + "-");
        return handle25TimestampCase(output, instant);
    }

    public static String generateOptimizedCbFileName(RaoIntegrationTask raoIntegrationTask) {
        return OutputsNamingRules.OPTIMIZED_CB_FILENAME_FORMATTER.format(IntervalUtil.getBusinessDay(raoIntegrationTask.getTimeInterval()))
                .replace("0V", String.format("%02d", raoIntegrationTask.getVersion()));
    }

    public static String generateOutputsDestinationPath(String destinationPrefix, String fileName) {
        return String.format(OutputsNamingRules.OUTPUTS, destinationPrefix, fileName);
    }

    public static String generateCgmZipName(RaoIntegrationTask raoIntegrationTask) {
        return OutputsNamingRules.UCT_OUTPUT_FORMATTER.format(IntervalUtil.getBusinessDay(raoIntegrationTask.getTimeInterval()))
                .replace("0V", String.format("%02d", raoIntegrationTask.getVersion()));
    }

    public static String generateCneZipName(RaoIntegrationTask raoIntegrationTask) {
        return OutputsNamingRules.CNE_OUTPUT_FORMATTER.format(IntervalUtil.getBusinessDay(raoIntegrationTask.getTimeInterval()))
                .replace("0V", String.format("%02d", raoIntegrationTask.getVersion()));
    }

    public static String generateMetadataFileName(RaoIntegrationTask raoIntegrationTask) {
        return OutputsNamingRules.METADATA_FILENAME_FORMATTER.format(IntervalUtil.getBusinessDay(raoIntegrationTask.getTimeInterval()))
                .replace("0V", String.format("%02d", raoIntegrationTask.getVersion()));
    }

    public static String generateCracCreationReportFileName(String instant, RaoIntegrationTask raoIntegrationTask) {
        String output = OutputsNamingRules.CRAC_CREATION_REPORT_FILENAME_FORMATTER.format(Instant.parse(instant))
            .replace("0V", String.format("%02d", raoIntegrationTask.getVersion()));
        return handle25TimestampCase(output, instant);
    }

    public static String generateLogsFileName(String instant, RaoIntegrationTask raoIntegrationTask) {
        String output = OutputsNamingRules.RAO_LOGS_FILENAME_FORMATTER.format(Instant.parse(instant))
            .replace("0V", String.format("%02d", raoIntegrationTask.getVersion()));
        return handle25TimestampCase(output, instant);
    }

    public static String generateLogsZipName(RaoIntegrationTask raoIntegrationTask) {
        return OutputsNamingRules.LOGS_OUTPUT_FORMATTER.format(IntervalUtil.getBusinessDay(raoIntegrationTask.getTimeInterval()))
            .replace("0V", String.format("%02d", raoIntegrationTask.getVersion()));
    }

    /**
     * For 25-timestamp business day, replace the duplicate hour "_HH30_" with "_BH30_"
     */
    private static String handle25TimestampCase(String filename, String instant) {
        ZoneOffset previousOffset = OffsetDateTime.from(Instant.parse(instant).minus(1, ChronoUnit.HOURS).atZone(OutputsNamingRules.ZONE_ID)).getOffset();
        ZoneOffset currentOffset = OffsetDateTime.from(Instant.parse(instant).atZone(OutputsNamingRules.ZONE_ID)).getOffset();
        if (previousOffset == ZoneOffset.ofHours(2) && currentOffset == ZoneOffset.ofHours(1)) {
            return filename.replace("_0", "_B");
        } else {
            return filename;
        }
    }
}
