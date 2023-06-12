/*
 * Copyright (c) 2021, RTE (http://www.rte-france.com)
 */
package com.farao_community.farao.gridcapa_core_cc.app.postprocessing;

import com.farao_community.farao.gridcapa_core_cc.api.resource.InternalCoreCCRequest;
import com.farao_community.farao.gridcapa_core_cc.app.constants.OutputsNamingRules;

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

    public static String generateRaoRequestAckFileName(InternalCoreCCRequest coreCCRequest) {
        return OutputsNamingRules.RAO_REQUEST_ACK_FILENAME_FORMATTER.format(coreCCRequest.getTimestamp())
                .replace("0V", String.format("%02d", coreCCRequest.getVersion()));
    }

    public static String generateCoreCCResponseFileName(InternalCoreCCRequest coreCCRequest) {
        return OutputsNamingRules.CORE_CC_RESPONSE_FILENAME_FORMATTER.format(coreCCRequest.getTimestamp())
                .replace("0V", String.format("%02d", coreCCRequest.getVersion()));
    }

    public static String generateUctFileName(String instant, int version) {
        String output = OutputsNamingRules.UCT_FILENAME_FORMATTER.format(Instant.parse(instant));
        output = output.replace("2D0", "2D" + Instant.parse(instant).atZone(OutputsNamingRules.ZONE_ID).getDayOfWeek().getValue())
                .replace("_UXV", "_UX" + version);
        return handle25TimestampCase(output, instant);
    }

    public static String generateCneFileName(String instant, InternalCoreCCRequest coreCCRequest) {
        String output = OutputsNamingRules.CNE_FILENAME_FORMATTER.format(Instant.parse(instant))
                .replace("-v0-", "-v" + coreCCRequest.getVersion() + "-");
        return handle25TimestampCase(output, instant);
    }

    public static String generateRaoResultFileName(String instant, InternalCoreCCRequest coreCCRequest) {
        String output = OutputsNamingRules.RAO_RESULT_FILENAME_FORMATTER.format(Instant.parse(instant))
            .replace("-v0-", "-v" + coreCCRequest.getVersion() + "-");
        return handle25TimestampCase(output, instant);
    }

    public static String generateOptimizedCbFileName(InternalCoreCCRequest coreCCRequest) {
        return OutputsNamingRules.OPTIMIZED_CB_FILENAME_FORMATTER.format(coreCCRequest.getTimestamp())
                .replace("0V", String.format("%02d", coreCCRequest.getVersion()));
    }

    public static String generateOutputsDestinationPath(String destinationPrefix, String fileName) {
        return String.format(OutputsNamingRules.OUTPUTS, destinationPrefix, fileName);
    }

    public static String generateCgmZipName(InternalCoreCCRequest coreCCRequest) {
        return OutputsNamingRules.UCT_OUTPUT_FORMATTER.format(coreCCRequest.getTimestamp())
                .replace("0V", String.format("%02d", coreCCRequest.getVersion()));
    }

    public static String generateCneZipName(InternalCoreCCRequest coreCCRequest) {
        return OutputsNamingRules.CNE_OUTPUT_FORMATTER.format(coreCCRequest.getTimestamp())
                .replace("0V", String.format("%02d", coreCCRequest.getVersion()));
    }

    public static String generateMetadataFileName(String instant, InternalCoreCCRequest coreCCRequest) {
        return OutputsNamingRules.INTERMEDIATE_METADATA_FILENAME_FORMATTER.format(Instant.parse(instant))
                .replace("0V", String.format("%02d", coreCCRequest.getVersion()));
    }

    public static String generateCracCreationReportFileName(String instant, InternalCoreCCRequest coreCCRequest) {
        String output = OutputsNamingRules.RAO_LOGS_FILENAME_FORMATTER.format(Instant.parse(instant))
            .replace("0V", String.format("%02d", coreCCRequest.getVersion()));
        return handle25TimestampCase(output, instant);
    }

    public static String generateLogsZipName(InternalCoreCCRequest coreCCRequest) {
        return OutputsNamingRules.LOGS_OUTPUT_FORMATTER.format(coreCCRequest.getTimestamp())
            .replace("0V", String.format("%02d", coreCCRequest.getVersion()));
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
