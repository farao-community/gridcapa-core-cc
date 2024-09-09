/*
 * Copyright (c) 2023, RTE (http://www.rte-france.com)
 *  This Source Code Form is subject to the terms of the Mozilla Public
 *  License, v. 2.0. If a copy of the MPL was not distributed with this
 *  file, You can obtain one at http://mozilla.org/MPL/2.0/.
 *
 */

package com.farao_community.farao.gridcapa_core_cc.api.resource;

import com.farao_community.farao.gridcapa.task_manager.api.TaskParameterDto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * @author Ameni Walha {@literal <ameni.walha at rte-france.com>}
 */
class CoreCCRequestTest {

    private CoreCCFileResource cgm;
    private CoreCCFileResource dcCgm;
    private CoreCCFileResource cbcora;
    private CoreCCFileResource glsk;
    private CoreCCFileResource refProg;
    private CoreCCFileResource raoRequest;
    private CoreCCFileResource virtualHub;
    private List<TaskParameterDto> taskParameterList;
    private OffsetDateTime dateTime;
    private String currentRunId;

    @BeforeEach
    void setUp() {
        cgm = new CoreCCFileResource("network.txt", "http://path/to/cgm/file");
        dcCgm = new CoreCCFileResource("dcnetwork.txt", "http://path/to/dccgm/file");
        cbcora = new CoreCCFileResource("cbcora.txt", "http://path/to/cbcora/file");
        glsk = new CoreCCFileResource("glsk.txt", "http://path/to/glsk/file");
        refProg = new CoreCCFileResource("refprog.txt", "http://path/to/refProg/file");
        raoRequest = new CoreCCFileResource("raoRequest.txt", "http://path/to/raoRequest/file");
        virtualHub = new CoreCCFileResource("virtualHub.txt", "http://path/to/virtualHub/file");
        dateTime = OffsetDateTime.parse("2021-10-03T00:30Z");
        taskParameterList = List.of(new TaskParameterDto("USE_DC_CGM_INPUT", "BOOLEAN", "TRUE", "FALSE"));
        currentRunId = UUID.randomUUID().toString();
    }

    @Test
    void checkManualCoreCCRequest() {
        CoreCCRequest coreCCRequest = new CoreCCRequest("id", currentRunId, dateTime, cgm, dcCgm, cbcora, glsk, refProg, raoRequest, virtualHub, taskParameterList);
        assertNotNull(coreCCRequest);
        assertEquals("id", coreCCRequest.getId());
        assertEquals(currentRunId, coreCCRequest.getCurrentRunId());
        assertEquals("2021-10-03T00:30Z", coreCCRequest.getTimestamp().toString());
        assertEquals("network.txt", coreCCRequest.getCgm().getFilename());
        assertEquals("dcnetwork.txt", coreCCRequest.getDcCgm().getFilename());
        assertEquals("cbcora.txt", coreCCRequest.getCbcora().getFilename());
        assertEquals("refprog.txt", coreCCRequest.getRefProg().getFilename());
        assertEquals("glsk.txt", coreCCRequest.getGlsk().getFilename());
        assertEquals("raoRequest.txt", coreCCRequest.getRaoRequest().getFilename());
        assertEquals("virtualHub.txt", coreCCRequest.getVirtualHub().getFilename());
        assertFalse(coreCCRequest.getLaunchedAutomatically());
        assertEquals(1, coreCCRequest.getTaskParameterList().size());
        assertEquals("USE_DC_CGM_INPUT", coreCCRequest.getTaskParameterList().get(0).getId());
    }

    @Test
    void checkAutoCoreCCRequest() {
        CoreCCRequest coreCCRequest = new CoreCCRequest("id", currentRunId, dateTime, cgm, dcCgm, cbcora, glsk, refProg, raoRequest, virtualHub, true, taskParameterList);
        assertTrue(coreCCRequest.getLaunchedAutomatically());
    }

}
