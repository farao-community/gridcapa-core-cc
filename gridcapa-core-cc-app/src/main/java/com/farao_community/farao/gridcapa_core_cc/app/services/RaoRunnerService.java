/*
 * Copyright (c) 2023, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.farao_community.farao.gridcapa_core_cc.app.services;

import com.farao_community.farao.gridcapa_core_cc.api.exception.CoreCCInternalException;
import com.farao_community.farao.gridcapa_core_cc.api.exception.CoreCCRaoException;
import com.farao_community.farao.rao_runner.api.resource.AbstractRaoResponse;
import com.farao_community.farao.rao_runner.api.resource.RaoFailureResponse;
import com.farao_community.farao.rao_runner.api.resource.RaoRequest;
import com.farao_community.farao.rao_runner.api.resource.RaoSuccessResponse;
import com.farao_community.farao.rao_runner.starter.RaoRunnerClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * @author Amira Kahya {@literal <amira.kahya at rte-france.com>}
 */
@Service
public class RaoRunnerService {
    private static final Logger LOGGER = LoggerFactory.getLogger(RaoRunnerService.class);
    private final RaoRunnerClient raoRunnerClient;

    public RaoRunnerService(RaoRunnerClient raoRunnerClient) {
        this.raoRunnerClient = raoRunnerClient;
    }

    public RaoSuccessResponse run(RaoRequest raoRequest) throws CoreCCInternalException {
        try {
            LOGGER.info("RAO request sent: {}", raoRequest);
            AbstractRaoResponse raoResponse = raoRunnerClient.runRao(raoRequest);
            LOGGER.info("RAO response received: {}", raoResponse);
            if (raoResponse.isRaoFailed()) {
                final RaoFailureResponse failureResponse = (RaoFailureResponse) raoResponse;
                throw new CoreCCRaoException("RAO run failed: " + failureResponse.getErrorMessage());
            }
            return (RaoSuccessResponse) raoResponse;
        } catch (CoreCCRaoException e) {
            throw e;
        } catch (Exception e) {
            throw new CoreCCRaoException("RAO run failed", e);
        }
    }

}
