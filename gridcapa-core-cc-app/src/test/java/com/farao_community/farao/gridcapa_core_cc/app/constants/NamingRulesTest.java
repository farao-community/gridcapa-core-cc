/*
 * Copyright (c) 2023, RTE (http://www.rte-france.com)
 *  This Source Code Form is subject to the terms of the Mozilla Public
 *  License, v. 2.0. If a copy of the MPL was not distributed with this
 *  file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.farao_community.farao.gridcapa_core_cc.app.constants;

import com.farao_community.farao.gridcapa_core_cc.api.resource.InternalCoreCCRequest;
import com.farao_community.farao.gridcapa_core_cc.app.util.NamingRules;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;

import static com.farao_community.farao.gridcapa_core_cc.app.util.NamingRules.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * @author Mohamed BenRejeb {@literal <mohamed.ben-rejeb at rte-france.com>}
 * @author Thomas Bouquet {@literal <thomas.bouquet at rte-france.com>}
 * @author Godelaine de Montmorillon {@literal <godelaine.demontmorillon at rte-france.com>}
 */
class NamingRulesTest {

    private final LocalDateTime dateTime = LocalDateTime.of(2023, 7, 12, 15, 17, 41);
    private InternalCoreCCRequest internalCoreCCRequest;

    @BeforeEach
    void setUp() {
        internalCoreCCRequest = Mockito.mock(InternalCoreCCRequest.class);
        Mockito.when(internalCoreCCRequest.getVersion()).thenReturn(2);
    }

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
    void generateRaoRequestAckFileNameTest() {
        Mockito.when(internalCoreCCRequest.getTimestamp()).thenReturn(OffsetDateTime.parse("2021-01-01T00:30Z"));
        assertEquals("22XCORESO------S_10V1001C--00236Y_CORE-FB-302-ACK_20210101-F302-02.xml", generateRaoRequestAckFileName(internalCoreCCRequest));

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
        assertEquals("20230712_1530_METADATA-<version>.json", dateTime.format(INTERMEDIATE_METADATA_FILENAME_FORMATTER));
        assertNotEquals("2023-07-12_1530_METADATA-<version>.json", dateTime.format(INTERMEDIATE_METADATA_FILENAME_FORMATTER));
        assertNotEquals("20230101_0030_METADATA-<version>.json", dateTime.format(INTERMEDIATE_METADATA_FILENAME_FORMATTER));
    }

    @Test
    void generateCgmFileNameTest() {
        assertEquals("20200330_0130_2D1_UX1.uct", NamingRules.generateUctFileName("2020-03-29T23:00:00Z", 1));
    }

    @Test
    void generateCneFileName() {
        assertEquals("20230721_1530_20230721-F299-v2-22XCORESO------S_to_17XTSO-CS------W.xml", NamingRules.generateCneFileName("2023-07-21T13:51:33Z", internalCoreCCRequest));
    }

    @Test
    void generateMetadataFileName() {
        assertEquals("20230721_1630_METADATA-02.json", NamingRules.generateMetadataFileName("2023-07-21T14:03:23Z", internalCoreCCRequest));
        assertNotEquals("20230721_1630_METADATA-0V.json", NamingRules.generateMetadataFileName("2023-07-21T14:03:23Z", internalCoreCCRequest));
    }

    @Test
    void generateRaoResultFileName() {
        // Summertime
        assertEquals("CASTOR-INTERNAL-RESULTS_20230721_1551.json", NamingRules.generateRaoResultFileName("2023-07-21T13:51:33Z"));
        // Wintertime
        assertEquals("CASTOR-INTERNAL-RESULTS_20231231_1529.json", NamingRules.generateRaoResultFileName("2023-12-31T14:29:33Z"));
        //time change
        assertEquals("CASTOR-INTERNAL-RESULTS_20221030_0030.json", NamingRules.generateRaoResultFileName("2022-10-29T22:30:33Z"));
        assertEquals("CASTOR-INTERNAL-RESULTS_20221030_0230.json", NamingRules.generateRaoResultFileName("2022-10-30T00:30:33Z"));
        assertEquals("CASTOR-INTERNAL-RESULTS_20221030_B230.json", NamingRules.generateRaoResultFileName("2022-10-30T01:30:33Z"));
        assertEquals("CASTOR-INTERNAL-RESULTS_20221030_0330.json", NamingRules.generateRaoResultFileName("2022-10-30T02:30:33Z"));
    }
}
