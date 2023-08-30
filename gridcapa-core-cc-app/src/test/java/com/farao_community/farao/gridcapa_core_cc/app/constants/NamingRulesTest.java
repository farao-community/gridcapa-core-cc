/*
 * Copyright (c) 2021, RTE (http://www.rte-france.com)
 *  This Source Code Form is subject to the terms of the Mozilla Public
 *  License, v. 2.0. If a copy of the MPL was not distributed with this
 *  file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.farao_community.farao.gridcapa_core_cc.app.constants;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static com.farao_community.farao.gridcapa_core_cc.app.constants.NamingRules.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * @author Thomas Bouquet {@literal <thomas.bouquet at rte-france.com>}
 */
class NamingRulesTest {

    private final LocalDateTime dateTime = LocalDateTime.of(2023, 7, 12, 15, 17, 41);

    @Test
    void testCgmXmlHeaderName() {
        assertFalse("CGM_XML_HEADER".matches(CGM_XML_HEADER_NAME));
        assertTrue("anything.xml".matches(CGM_XML_HEADER_NAME));
    }

    @Test
    void testCgmFileName() {
        assertTrue("01234567_8930_2D1.uct".matches(CGM_FILE_NAME));
        assertTrue("52900617_B230_2D6.uct".matches(CGM_FILE_NAME));
        assertFalse("148083467_B230_2D2".matches(CGM_FILE_NAME));
        assertFalse("002619478_B230_2D4.zip".matches(CGM_FILE_NAME));
        assertFalse("52900617_B230_2D8.uct".matches(CGM_FILE_NAME));
        assertTrue("00000000_0030_2D5_anything.uct".matches(CGM_FILE_NAME));
    }

    @Test
    void testRaoRequestAckFileName() {
        assertEquals("CASTOR-RAO_22VCOR0CORE0PRDI_RTE-F302-ACK_20230712-F302-0V.xml", dateTime.format(RAO_REQUEST_ACK_FILENAME_FORMATTER));
        assertNotEquals("CASTOR-RAO_22VCOR0CORE0PRDI_RTE-F302-ACK_2023-07-12-F302-0V.xml", dateTime.format(RAO_REQUEST_ACK_FILENAME_FORMATTER));
        assertNotEquals("CASTOR-RAO_22VCOR0CORE0PRDI_RTE-F302-ACK_20230101-F302-0V.xml", dateTime.format(RAO_REQUEST_ACK_FILENAME_FORMATTER));
    }

    @Test
    void testUctFileName() {
        assertEquals("20230712_1530_2D0_UXV.uct", dateTime.format(UCT_FILENAME_FORMATTER));
        assertNotEquals("2023-07-12_1530_2D0_UXV.uct", dateTime.format(UCT_FILENAME_FORMATTER));
        assertNotEquals("20230101_0030_2D0_UXV.uct", dateTime.format(UCT_FILENAME_FORMATTER));
    }

    @Test
    void testCneFileName() {
        assertEquals("20230712_1530_20230712-F299-v0-22XCORESO------S_to_17XTSO-CS------W.xml", dateTime.format(CNE_FILENAME_FORMATTER));
        assertNotEquals("2023-07-12_1530_20230712-F299-v0-22XCORESO------S_to_17XTSO-CS------W.xml", dateTime.format(CNE_FILENAME_FORMATTER));
        assertNotEquals("20230101_0030_20230712-F299-v0-22XCORESO------S_to_17XTSO-CS------W.xml", dateTime.format(CNE_FILENAME_FORMATTER));
    }

    @Test
    void testIntermediateMetadataFileName() {
        assertEquals("20230712_1530_METADATA-0V.json", dateTime.format(INTERMEDIATE_METADATA_FILENAME_FORMATTER));
        assertNotEquals("2023-07-12_1530_METADATA-0V.json", dateTime.format(INTERMEDIATE_METADATA_FILENAME_FORMATTER));
        assertNotEquals("20230101_0030_METADATA-0V.json", dateTime.format(INTERMEDIATE_METADATA_FILENAME_FORMATTER));
    }

    @Test
    void testRaoResultFileName() {
        assertEquals("20230712_1530_RAO-RESULT-0V.json", dateTime.format(RAO_RESULT_FILENAME_FORMATTER));
        assertNotEquals("2023-07-12_1530_RAO-RESULT-0V.txt", dateTime.format(RAO_RESULT_FILENAME_FORMATTER));
        assertNotEquals("20230101_0030_RAO-RESULT-0V.txt", dateTime.format(RAO_RESULT_FILENAME_FORMATTER));
    }
}
