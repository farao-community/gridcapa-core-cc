/*
 * Copyright (c) 2023, RTE (http://www.rte-france.com)
 *  This Source Code Form is subject to the terms of the Mozilla Public
 *  License, v. 2.0. If a copy of the MPL was not distributed with this
 *  file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.farao_community.farao.gridcapa_core_cc.app.util;

import com.farao_community.farao.gridcapa_core_cc.api.resource.InternalCoreCCRequest;
import com.farao_community.farao.gridcapa_core_cc.api.util.IntervalUtil;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

/**
 * @author Mohamed BenRejeb {@literal <mohamed.ben-rejeb at rte-france.com>}
 * @author Godelaine de Montmorillon {@literal <godelaine.demontmorillon at rte-france.com>}
 */
public final class NamingRules {

    private NamingRules() {
        throw new AssertionError("Utility class should not be constructed");
    }

    // -- Inputs Naming Rules
    public static final String S_INPUTS_S = "%s/inputs/%s";
    public static final String S_INPUTS_NETWORKS_S = "%s/inputs/networks/%s";
    public static final String S_INPUTS_CRACS_S = "%s/inputs/cracs/%s";

    public static final String JSON_EXTENSION = ".json";
    public static final String IIDM_EXTENSION = ".xiidm";
    public static final String JSON_RAO_PARAMETERS_FILE_NAME = "raoParameters.json";
    public static final String CGM_XML_HEADER_NAME = "^.*\\.(xml)$";
    public static final String CGM_FILE_NAME = "^[0-9]{8}_([0-9]{2}|B2)30_2D[1-7].*\\.uct";
    // -- Outputs Naming Rules
    public static final String OUTPUTS = "%s/outputs/%s";

    // DateTimeFormatter are systematically rezoned even applied on offsetDateTimes as a security measure
    public static final DateTimeFormatter UTC_HOURLY_NAME_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMdd'_'HHmm").withZone(ZoneId.of("UTC"));

    public static final DateTimeFormatter RAO_REQUEST_ACK_FILENAME_FORMATTER = DateTimeFormatter.ofPattern("'22XCORESO------S_10V1001C--00236Y_CORE-FB-302-ACK_'yyyyMMdd'-F302-<version>.xml'").withZone(IntervalUtil.ZONE_ID);
    public static final DateTimeFormatter UCT_FILENAME_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMdd'_'HH'30_2D0_UXV.uct'").withZone(IntervalUtil.ZONE_ID);
    public static final DateTimeFormatter CNE_FILENAME_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMdd'_'HH'30_'yyyyMMdd'-F299-v0-22XCORESO------S_to_17XTSO-CS------W.xml'").withZone(IntervalUtil.ZONE_ID);
    public static final DateTimeFormatter INTERMEDIATE_METADATA_FILENAME_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMdd'_'HH'30_METADATA-<version>.json'").withZone(IntervalUtil.ZONE_ID);
    public static final DateTimeFormatter RAO_RESULT_FILENAME_FORMATTER = DateTimeFormatter.ofPattern("'CASTOR-INTERNAL-RESULTS_'yyyyMMdd'_'HHmm'.json'").withZone(IntervalUtil.ZONE_ID);

    // -- Xml Response Generator constants
    public static final String XML_RESPONSE_GENERATOR_SENDER_ID = "22XCORESO------S";
    public static final String XML_RESPONSE_GENERATOR_RECEIVER_ID = "17XTSO-CS------W";

    public static String generateRaoRequestAckFileName(InternalCoreCCRequest coreCCRequest) {
        return formatVersion(RAO_REQUEST_ACK_FILENAME_FORMATTER.format(coreCCRequest.getTimestamp()), coreCCRequest.getVersion());
    }

    public static String generateUctFileName(String instant, int version) {
        String output = UCT_FILENAME_FORMATTER.format(Instant.parse(instant));
        output = output.replace("2D0", "2D" + Instant.parse(instant).atZone(IntervalUtil.ZONE_ID).getDayOfWeek().getValue())
            .replace("_UXV", "_UX" + version);
        return IntervalUtil.handle25TimestampCase(output, instant);
    }

    public static String generateCneFileName(String instant, InternalCoreCCRequest coreCCRequest) {
        String output = CNE_FILENAME_FORMATTER.format(Instant.parse(instant))
            .replace("-v0-", "-v" + coreCCRequest.getVersion() + "-");
        return IntervalUtil.handle25TimestampCase(output, instant);
    }

    public static String generateRaoResultFileName(String instant) {
        String output = RAO_RESULT_FILENAME_FORMATTER.format(Instant.parse(instant));
        return IntervalUtil.handle25TimestampCase(output, instant);
    }

    public static String generateOutputsDestinationPath(String destinationPrefix, String fileName) {
        return String.format(OUTPUTS, destinationPrefix, fileName);
    }

    public static String generateMetadataFileName(String instant, InternalCoreCCRequest coreCCRequest) {
        String output =  formatVersion(INTERMEDIATE_METADATA_FILENAME_FORMATTER.format(Instant.parse(instant)), coreCCRequest.getVersion());
        return IntervalUtil.handle25TimestampCase(output, instant);
    }

    // -- Destination keys
    public static String getDestinationKey(OffsetDateTime offsetDateTime) {
        String hourlyFolderName = offsetDateTime.format(DateTimeFormatter.ofPattern("yyyyMMdd'_'HHmm").withZone(IntervalUtil.ZONE_ID));
        return "RAO_WORKING_DIR" + "/" + IntervalUtil.handle25TimestampCase(hourlyFolderName, offsetDateTime.toInstant().toString());
    }

    public static String getAckDestinationKey(OffsetDateTime offsetDateTime) {
        String hourlyFolderName = offsetDateTime.format(DateTimeFormatter.ofPattern("yyyy-MM-dd").withZone(IntervalUtil.ZONE_ID));
        return "RAO_OUTPUTS_DIR" + "/" + IntervalUtil.handle25TimestampCase(hourlyFolderName, offsetDateTime.toInstant().toString());
    }

    private static String formatVersion(String filename, int v) {
        return filename.replace("<version>", String.format("%02d", v));
    }
}
