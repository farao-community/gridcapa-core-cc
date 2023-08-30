/*
 * Copyright (c) 2023, RTE (http://www.rte-france.com)
 *  This Source Code Form is subject to the terms of the Mozilla Public
 *  License, v. 2.0. If a copy of the MPL was not distributed with this
 *  file, You can obtain one at http://mozilla.org/MPL/2.0/.
 *
 */

package com.farao_community.farao.gridcapa_core_cc.api.resource;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;

import static org.junit.jupiter.api.Assertions.*;

/**
 * @author Ameni Walha {@literal <ameni.walha at rte-france.com>}
 */
class CoreCCRequestTest {

    private CoreCCFileResource cgm;
    private CoreCCFileResource cbcora;
    private CoreCCFileResource glsk;
    private CoreCCFileResource refProg;
    private CoreCCFileResource raoRequest;
    private CoreCCFileResource virtualHub;

    private OffsetDateTime dateTime;

    @BeforeEach
    void setUp() {
        cgm = new CoreCCFileResource("network.txt", "http://path/to/cgm/file");
        cbcora = new CoreCCFileResource("cbcora.txt", "http://path/to/cbcora/file");
        glsk = new CoreCCFileResource("glsk.txt", "http://path/to/glsk/file");
        refProg = new CoreCCFileResource("refprog.txt", "http://path/to/refProg/file");
        raoRequest = new CoreCCFileResource("raoRequest.txt", "http://path/to/raoRequest/file");
        virtualHub = new CoreCCFileResource("virtualHub.txt", "http://path/to/virtualHub/file");
        dateTime = OffsetDateTime.parse("2021-10-03T00:30Z");
    }

    @Test
    void checkManualCoreCCRequest() {
        CoreCCRequest coreCCRequest = new CoreCCRequest("id", dateTime, cgm, cbcora, glsk, refProg, raoRequest, virtualHub);
        assertNotNull(coreCCRequest);
        assertEquals("id", coreCCRequest.getId());
        assertEquals("2021-10-03T00:30Z", coreCCRequest.getTimestamp().toString());
        assertEquals("network.txt", coreCCRequest.getCgm().getFilename());
        assertEquals("cbcora.txt", coreCCRequest.getCbcora().getFilename());
        assertEquals("refprog.txt", coreCCRequest.getRefProg().getFilename());
        assertEquals("glsk.txt", coreCCRequest.getGlsk().getFilename());
        assertEquals("raoRequest.txt", coreCCRequest.getRaoRequest().getFilename());
        assertEquals("virtualHub.txt", coreCCRequest.getVirtualHub().getFilename());
        assertFalse(coreCCRequest.getLaunchedAutomatically());
    }

    @Test
    void checkAutoCoreCCRequest() {
        CoreCCRequest coreCCRequest = new CoreCCRequest("id", dateTime, cgm, cbcora, glsk, refProg, raoRequest, virtualHub, true);
        assertTrue(coreCCRequest.getLaunchedAutomatically());
    }

}
