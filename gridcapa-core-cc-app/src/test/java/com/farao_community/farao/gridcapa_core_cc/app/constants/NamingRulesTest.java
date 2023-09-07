/*
 * Copyright (c) 2021, RTE (http://www.rte-france.com)
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
    }
}
