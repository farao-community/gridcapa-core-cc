/*
 * Copyright (c) 2022, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package com.farao_community.farao.gridcapa.core_cc.app.preprocessing;

import com.farao_community.farao.gridcapa.core_cc.app.inputs.rao_request.RequestMessage;
import com.farao_community.farao.gridcapa.core_cc.app.util.JaxbUtil;
import com.farao_community.farao.rao_api.parameters.RaoParameters;
import com.farao_community.farao.search_tree_rao.castor.parameters.SearchTreeRaoParameters;
import com.powsybl.iidm.network.Country;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.io.File;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

/**
 * @author Mohamed BenRejeb {@literal <mohamed.ben-rejeb at rte-france.com>}
 */
@SpringBootTest
class RaoParametersTest {

    @Autowired
    private RaoParametersService raoParametersService;

    @Test
    void checkRaoParametersFilledCorrectly() {
        String absolutePath = new File(getClass().getResource("/RaoIntegrationRequest.xml").getFile()).getAbsolutePath();
        RequestMessage raoRequestMessage = JaxbUtil.unmarshalFile(RequestMessage.class, Paths.get(absolutePath));
        assertEquals("2020-03-29T23:00:00Z/2020-03-30T00:00:00Z", raoRequestMessage.getPayload().getRequestItems().getTimeInterval());
        RaoParameters raoParameters = raoParametersService.createRaoParametersFromRequest(raoRequestMessage);

        assertEquals(10.0, raoParameters.getPstPenaltyCost());

        List<String> expectedLoopFlowConstraintCountries = Arrays.asList("CZ", "SK", "FR", "PL", "RO", "DE", "SI", "NL", "HR", "HU", "AT");
        List<String> actualLoopFlowConstraintCountries = raoParameters.getLoopflowCountries().stream().map(Country::toString).collect(Collectors.toList());
        assertTrue(expectedLoopFlowConstraintCountries.size() == actualLoopFlowConstraintCountries.size()
                && expectedLoopFlowConstraintCountries.containsAll(actualLoopFlowConstraintCountries)
                && actualLoopFlowConstraintCountries.containsAll(expectedLoopFlowConstraintCountries));

        SearchTreeRaoParameters searchTreeRaoParameters = raoParameters.getExtension(SearchTreeRaoParameters.class);
        assertEquals(10.0, searchTreeRaoParameters.getAbsoluteNetworkActionMinimumImpactThreshold());

        Map<String, Integer> maxCurativeRaPerTsoExpected = new TreeMap<>();
        maxCurativeRaPerTsoExpected.put("AT", 10);
        maxCurativeRaPerTsoExpected.put("BE", 10);
        assertEquals(maxCurativeRaPerTsoExpected.get("AT"), searchTreeRaoParameters.getMaxCurativeRaPerTso().get("AT"));
        assertEquals(maxCurativeRaPerTsoExpected.get("BE"), searchTreeRaoParameters.getMaxCurativeRaPerTso().get("BE"));
        assertEquals(maxCurativeRaPerTsoExpected.size(), searchTreeRaoParameters.getMaxCurativeRaPerTso().size());

        Map<String, Integer> maxCurativeTopoPerTsoExpected = new TreeMap<>();
        maxCurativeTopoPerTsoExpected.put("AT", 10);
        maxCurativeTopoPerTsoExpected.put("BE", 10);
        maxCurativeTopoPerTsoExpected.put("CZ", 10);
        assertEquals(maxCurativeTopoPerTsoExpected.get("AT"), searchTreeRaoParameters.getMaxCurativeTopoPerTso().get("AT"));
        assertEquals(maxCurativeTopoPerTsoExpected.get("BE"), searchTreeRaoParameters.getMaxCurativeTopoPerTso().get("BE"));
        assertEquals(maxCurativeTopoPerTsoExpected.get("CZ"), searchTreeRaoParameters.getMaxCurativeTopoPerTso().get("CZ"));
        assertEquals(maxCurativeTopoPerTsoExpected.size(), searchTreeRaoParameters.getMaxCurativeTopoPerTso().size());

        Map<String, Integer> maxCurativePstPerTsoExpected = new TreeMap<>();
        maxCurativePstPerTsoExpected.put("AT", 10);
        assertEquals(maxCurativePstPerTsoExpected.get("AT"), searchTreeRaoParameters.getMaxCurativePstPerTso().get("AT"));
        assertEquals(maxCurativePstPerTsoExpected.size(), searchTreeRaoParameters.getMaxCurativePstPerTso().size());
    }
}
