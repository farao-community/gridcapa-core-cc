/*
 * Copyright (c) 2021, RTE (http://www.rte-france.com)
 */
package com.farao_community.farao.gridcapa_core_cc.app.constants;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

/**
 * @author Mohamed BenRejeb {@literal <mohamed.ben-rejeb at rte-france.com>}
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
    public static final ZoneId ZONE_ID = ZoneId.of("Europe/Brussels");
    public static final String OUTPUTS = "%s/outputs/%s";

    public static final DateTimeFormatter RAO_REQUEST_ACK_FILENAME_FORMATTER = DateTimeFormatter.ofPattern("'CASTOR-RAO_22VCOR0CORE0PRDI_RTE-F302-ACK_'yyyyMMdd'-F302-0V.xml'").withZone(ZONE_ID);
    public static final DateTimeFormatter UCT_FILENAME_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMdd'_'HH'30_2D0_UXV.uct'").withZone(ZONE_ID);
    public static final DateTimeFormatter CNE_FILENAME_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMdd'_'HH'30_'yyyyMMdd'-F299-v0-22XCORESO------S_to_17XTSO-CS------W.xml'").withZone(ZONE_ID);
    public static final DateTimeFormatter INTERMEDIATE_METADATA_FILENAME_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMdd'_'HH'30_METADATA-0V.json'").withZone(ZONE_ID);
    public static final DateTimeFormatter RAO_RESULT_FILENAME_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMdd'_'HH'30_RAO-RESULT-0V.json'").withZone(ZONE_ID);

    // -- Xml Response Generator constants
    public static final String XML_RESPONSE_GENERATOR_SENDER_ID = "22XCORESO------S";
    public static final String XML_RESPONSE_GENERATOR_RECEIVER_ID = "17XTSO-CS------W";

}
