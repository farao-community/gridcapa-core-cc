/*
 * Copyright (c) 2023, RTE (http://www.rte-france.com)
 *  This Source Code Form is subject to the terms of the Mozilla Public
 *  License, v. 2.0. If a copy of the MPL was not distributed with this
 *  file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.farao_community.farao.gridcapa_core_cc.app.postprocessing;

import com.farao_community.farao.gridcapa_core_cc.api.resource.InternalCoreCCRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * @author Mohamed BenRejeb {@literal <mohamed.ben-rejeb at rte-france.com>}
 * @author Thomas Bouquet {@literal <thomas.bouquet at rte-france.com>}
 */
public class OutputFileNameUtilTest {

    private InternalCoreCCRequest internalCoreCCRequest;

    @BeforeEach
    void setUp() {
        internalCoreCCRequest = Mockito.mock(InternalCoreCCRequest.class);
        Mockito.when(internalCoreCCRequest.getVersion()).thenReturn(2);
    }

    @Test
    void generateCgmFileNameTest() {
        assertEquals("20200330_0130_2D1_UX1.uct", OutputFileNameUtil.generateUctFileName("2020-03-29T23:00:00Z", 1));
    }

    @Test
    void generateCneFileName() {
        assertEquals("20230721_1530_20230721-F299-v2-22XCORESO------S_to_17XTSO-CS------W.xml", OutputFileNameUtil.generateCneFileName("2023-07-21T13:51:33Z", internalCoreCCRequest));
    }

    @Test
    void generateRaoResultFileName() {
        assertEquals("20230721_1530_RAO-RESULT-02.json", OutputFileNameUtil.generateRaoResultFileName("2023-07-21T13:57:18Z", internalCoreCCRequest));
    }

    @Test
    void generateMetadataFileName() {
        assertEquals("20230721_1630_METADATA-02.json", OutputFileNameUtil.generateMetadataFileName("2023-07-21T14:03:23Z", internalCoreCCRequest));
    }

    @Test
    void generateCracCreationReportFileName() {
        assertEquals("20230721_1630_RAO-LOGS-02.txt", OutputFileNameUtil.generateCracCreationReportFileName("2023-07-21T14:03:42Z", internalCoreCCRequest));
    }

}
