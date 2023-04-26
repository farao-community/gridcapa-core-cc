/*
 * Copyright (c) 2021, RTE (http://www.rte-france.com)
 */
package com.farao_community.farao.gridcapa_core_cc.app.constants;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

/**
 * @author Mohamed BenRejeb {@literal <mohamed.ben-rejeb at rte-france.com>}
 */
public final class OutputsNamingRules {
    public static final ZoneId ZONE_ID = ZoneId.of("Europe/Brussels");

    private OutputsNamingRules() {
        throw new AssertionError("Utility class should not be constructed");
    }

    public static final String OUTPUTS = "%s/outputs/%s"; // destination/filename

    public static final DateTimeFormatter RAO_REQUEST_ACK_FILENAME_FORMATTER = DateTimeFormatter.ofPattern("'CASTOR-RAO_22VCOR0CORE0PRDI_RTE-F302-ACK_'yyyyMMdd'-F302-0V.xml'").withZone(ZONE_ID);
    public static final DateTimeFormatter UCT_FILENAME_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMdd'_'HH'30_2D0_UXV.uct'").withZone(ZONE_ID);
    public static final DateTimeFormatter UCT_OUTPUT_FORMATTER = DateTimeFormatter.ofPattern("'CASTOR-RAO_22VCOR0CORE0PRDI_RTE-F304_'yyyyMMdd'-F304-0V.zip'").withZone(ZONE_ID);
    public static final DateTimeFormatter RAO_INTEGRATION_RESPONSE_FILENAME_FORMATTER = DateTimeFormatter.ofPattern("'CASTOR-RAO_22VCOR0CORE0PRDI_RTE-F305_'yyyyMMdd'-F305-0V.xml'").withZone(ZONE_ID);
    public static final DateTimeFormatter CNE_FILENAME_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMdd'_'HH'30_'yyyyMMdd'-F299-v0-22XCORESO------S_to_17XTSO-CS------W.xml'").withZone(ZONE_ID);
    public static final DateTimeFormatter CNE_OUTPUT_FORMATTER = DateTimeFormatter.ofPattern("'CASTOR-RAO_22VCOR0CORE0PRDI_RTE-F299_'yyyyMMdd'-F299-0V.zip'").withZone(ZONE_ID);
    public static final DateTimeFormatter OPTIMIZED_CB_FILENAME_FORMATTER = DateTimeFormatter.ofPattern("'CASTOR-RAO_22VCOR0CORE0PRDI_RTE-F303_'yyyyMMdd'-F303-0V.xml'").withZone(ZONE_ID);
    public static final String CGM_XML_HEADER_FILENAME = "CGM_XML_Header.xml";
    public static final DateTimeFormatter METADATA_FILENAME_FORMATTER = DateTimeFormatter.ofPattern("'CASTOR-RAO_22VCOR0CORE0PRDI_RTE-F341_'yyyyMMdd'-F341-0V.csv'").withZone(ZONE_ID);
    public static final DateTimeFormatter RAO_LOGS_FILENAME_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMdd'_'HH'30_RAO-LOGS-0V.txt'").withZone(ZONE_ID);
    public static final DateTimeFormatter LOGS_OUTPUT_FORMATTER = DateTimeFormatter.ofPattern("'CASTOR-RAO_22VCOR0CORE0PRDI_RTE-F342_'yyyyMMdd'-F342-0V.zip'").withZone(ZONE_ID);
}