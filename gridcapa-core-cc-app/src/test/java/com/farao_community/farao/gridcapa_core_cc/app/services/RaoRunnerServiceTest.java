/*
 * Copyright (c) 2023, RTE (http://www.rte-france.com)
 *  This Source Code Form is subject to the terms of the Mozilla Public
 *  License, v. 2.0. If a copy of the MPL was not distributed with this
 *  file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package com.farao_community.farao.gridcapa_core_cc.app.services;

import com.farao_community.farao.gridcapa_core_cc.api.exception.CoreCCRaoException;
import com.farao_community.farao.rao_runner.api.resource.RaoFailureResponse;
import com.farao_community.farao.rao_runner.api.resource.RaoRequest;
import com.farao_community.farao.rao_runner.api.resource.RaoSuccessResponse;
import com.farao_community.farao.rao_runner.starter.RaoRunnerClient;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * @author Thomas Bouquet {@literal <thomas.bouquet at rte-france.com>}
 */
class RaoRunnerServiceTest {

    private RaoRequest raoRequest;
    private RaoRunnerService raoRunnerService;

    @Test
    void runRao() {
        raoRequest = Mockito.mock(RaoRequest.class);
        Mockito.when(raoRequest.toString()).thenReturn("raoRequest");
        RaoSuccessResponse raoResponse = Mockito.mock(RaoSuccessResponse.class);
        Mockito.when(raoResponse.toString()).thenReturn("raoResponse");
        Mockito.when(raoResponse.getId()).thenReturn("id");
        RaoRunnerClient raoRunnerClient = Mockito.mock(RaoRunnerClient.class);
        Mockito.when(raoRunnerClient.runRao(Mockito.any())).thenReturn(raoResponse);
        raoRunnerService = new RaoRunnerService(raoRunnerClient);

        RaoSuccessResponse retrievedRaoResponse = raoRunnerService.run(raoRequest);

        assertEquals("id", retrievedRaoResponse.getId());
    }

    @Test
    void errorWhileRunningRao() {
        RaoRunnerClient badRaoRunnerClient = Mockito.mock(RaoRunnerClient.class);
        final RaoFailureResponse failureResponse = new RaoFailureResponse.Builder().withErrorMessage("error").build();
        Mockito.when(badRaoRunnerClient.runRao(Mockito.any())).thenReturn(failureResponse);
        raoRunnerService = new RaoRunnerService(badRaoRunnerClient);
        CoreCCRaoException coreCCRaoException = assertThrows(CoreCCRaoException.class, () -> raoRunnerService.run(raoRequest));
        assertEquals("RAO run failed: error", coreCCRaoException.getMessage());
    }
}
