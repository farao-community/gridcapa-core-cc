/*
 * Copyright (c) 2021, RTE (http://www.rte-france.com)
 */
package com.farao_community.farao.gridcapa_core_cc.app.constants;

/**
 * @author Mohamed BenRejeb {@literal <mohamed.ben-rejeb at rte-france.com>}
 */
public final class InputsNamingRules {

    private InputsNamingRules() {
        throw new AssertionError("Utility class should not be constructed");
    }

    public static final String S_INPUTS_S = "%s/inputs/%s";
    public static final String S_INPUTS_NETWORKS_S = "%s/inputs/networks/%s";
    public static final String S_INPUTS_CRACS_S = "%s/inputs/cracs/%s";

    public static final String JSON_EXTENSION = ".json";
    public static final String IIDM_EXTENSION = ".xiidm";
    public static final String JSON_RAO_PARAMETERS_FILE_NAME = "raoParameters.json";

    public static final String RAO_REQUEST_FILE_NAME = "^[0-9]{8}-F302-v[0-9]-17XTSO-CS------W-to-22XCORESO------S.xml";
    public static final String CGM_ZIP_FILE_NAME = "^[0-9]{8}-F119-v[0-9]-17XTSO-CS------W-to-22XCORESO------S.zip";
    public static final String CGM_XML_HEADER_NAME = "^.*\\.(xml)$";
    public static final String CGM_FILE_NAME = "^[0-9]{8}_([0-9]{2}|B2)30_2D[1-7]_UX[0-9].uct";
    public static final String REAL_GLSK_FILE_NAME = "^[0-9]{8}-F319-v[0-9]-17XTSO-CS------W-to-22XCORESO------S.xml";
    public static final String CRAC_FILE_NAME = "^[0-9]{8}-F301-v[0-9]-17XTSO-CS------W-to-22XCORESO------S.xml";
    public static final String REF_PROG_FILE_NAME = "^[0-9]{8}-F120-v[0-9]-17XTSO-CS------W-to-22XCORESO------S.xml";
    public static final String VIRTUAL_HUBS_FILE_NAME = "^[0-9]{8}-F327-v[0-9]-17XTSO-CS------W-to-22XCORESO------S.xml";

}
