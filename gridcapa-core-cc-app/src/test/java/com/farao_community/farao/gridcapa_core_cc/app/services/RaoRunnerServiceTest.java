/*
 * Copyright (c) 2023, RTE (http://www.rte-france.com)
 *  This Source Code Form is subject to the terms of the Mozilla Public
 *  License, v. 2.0. If a copy of the MPL was not distributed with this
 *  file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package com.farao_community.farao.gridcapa_core_cc.app.services;

import com.farao_community.farao.gridcapa_core_cc.api.exception.CoreCCRaoException;
import com.farao_community.farao.rao_runner.api.resource.RaoRequest;
import com.farao_community.farao.rao_runner.api.resource.RaoResponse;
import com.farao_community.farao.rao_runner.starter.RaoRunnerClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * @author Thomas Bouquet {@literal <thomas.bouquet at rte-france.com>}
 */
public class RaoRunnerServiceTest {

    private RaoRequest raoRequest;
    private RaoResponse raoResponse;
    private RaoRunnerClient raoRunnerClient;
    private RaoRunnerClient badRaoRunnerClient;
    private RaoRunnerService raoRunnerService;

    @BeforeEach
    void setUp() {
        raoRequest = Mockito.mock(RaoRequest.class);
        Mockito.when(raoRequest.toString()).thenReturn("raoRequest");
        raoResponse = Mockito.mock(RaoResponse.class);
        Mockito.when(raoResponse.toString()).thenReturn("raoResponse");
        Mockito.when(raoResponse.getId()).thenReturn("id");
        raoRunnerClient = Mockito.mock(RaoRunnerClient.class);
        Mockito.when(raoRunnerClient.runRao(Mockito.any())).thenReturn(raoResponse);
        badRaoRunnerClient = Mockito.mock(RaoRunnerClient.class);
        Mockito.when(badRaoRunnerClient.runRao(Mockito.any())).thenThrow(new CoreCCRaoException("error"));
    }

    @Test
    void runRao() {
        raoRunnerService = new RaoRunnerService(raoRunnerClient);
        RaoResponse retrievedRaoResponse = raoRunnerService.run(raoRequest);
        assertEquals("id", retrievedRaoResponse.getId());
    }

    @Test
    void errorWhileRunningRao() {
        raoRunnerService = new RaoRunnerService(badRaoRunnerClient);
        CoreCCRaoException coreCCRaoException = assertThrows(CoreCCRaoException.class, () -> raoRunnerService.run(raoRequest));
        assertEquals("RAO run failed. Nested exception: error", coreCCRaoException.getMessage());
    }
}
