/*
 * Copyright (c) 2023, RTE (http://www.rte-france.com)
 *  This Source Code Form is subject to the terms of the Mozilla Public
 *  License, v. 2.0. If a copy of the MPL was not distributed with this
 *  file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.farao_community.farao.gridcapa_core_cc.app.preprocessing;

import com.farao_community.farao.gridcapa_core_cc.app.inputs.rao_request.RequestMessage;
import com.farao_community.farao.gridcapa_core_cc.app.util.JaxbUtil;
import com.powsybl.iidm.network.Country;
import com.powsybl.openrao.raoapi.parameters.RaoParameters;
import com.powsybl.openrao.raoapi.parameters.extensions.LoopFlowParametersExtension;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * @author Mohamed BenRejeb {@literal <mohamed.ben-rejeb at rte-france.com>}
 * @author Thomas Bouquet {@literal <thomas.bouquet at rte-france.com>}
 */
@SpringBootTest
class RaoParametersServiceTest {

    @Autowired
    private RaoParametersService raoParametersService;

    @Test
    void checkRaoParametersFilledCorrectly() {
        RequestMessage raoRequestMessage = JaxbUtil.unmarshalFile(RequestMessage.class, Paths.get(getClass().getResource("/RaoIntegrationRequest.xml").getPath()));
        assertEquals("2020-03-29T23:00:00Z/2020-03-30T00:00:00Z", raoRequestMessage.getPayload().getRequestItems().getTimeInterval());
        RaoParameters raoParameters = raoParametersService.createRaoParametersFromRequest(raoRequestMessage);

        assertEquals(10.0, raoParameters.getRangeActionsOptimizationParameters().getPstPenaltyCost());

        List<String> expectedLoopFlowConstraintCountries = Arrays.asList("CZ", "SK", "FR", "PL", "RO", "DE", "SI", "NL", "HR", "HU", "AT");
        List<String> actualLoopFlowConstraintCountries = raoParameters.getExtension(LoopFlowParametersExtension.class).getCountries().stream().map(Country::toString).collect(Collectors.toList());
        assertTrue(expectedLoopFlowConstraintCountries.size() == actualLoopFlowConstraintCountries.size()
                && expectedLoopFlowConstraintCountries.containsAll(actualLoopFlowConstraintCountries)
                && actualLoopFlowConstraintCountries.containsAll(expectedLoopFlowConstraintCountries));

        assertEquals(10.0, raoParameters.getTopoOptimizationParameters().getAbsoluteMinImpactThreshold());
    }
}
