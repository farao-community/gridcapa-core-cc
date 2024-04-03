/*
 * Copyright (c) 2023, RTE (http://www.rte-france.com)
 *  This Source Code Form is subject to the terms of the Mozilla Public
 *  License, v. 2.0. If a copy of the MPL was not distributed with this
 *  file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.farao_community.farao.gridcapa_core_cc.app.preprocessing;

import com.farao_community.farao.gridcapa_core_cc.app.util.NamingRules;
import com.farao_community.farao.gridcapa_core_cc.app.inputs.rao_request.Property;
import com.farao_community.farao.gridcapa_core_cc.app.inputs.rao_request.RequestMessage;
import com.farao_community.farao.minio_adapter.starter.MinioAdapter;
import com.powsybl.openrao.raoapi.json.JsonRaoParameters;
import com.powsybl.openrao.raoapi.parameters.RaoParameters;
import com.powsybl.openrao.raoapi.parameters.extensions.LoopFlowParametersExtension;
import com.powsybl.iidm.network.Country;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.*;
import java.util.stream.Collectors;

/**
 * @author Mohamed BenRejeb {@literal <mohamed.ben-rejeb at rte-france.com>}
 */
@Service
public class RaoParametersService {
    private final MinioAdapter minioAdapter;
    private static final List<String> GERMAN_ZONES = Arrays.asList("D2", "D4", "D7", "D8");
    private static final String TOPO_RA_MIN_IMPACT = "Topo_RA_Min_impact";
    private static final String PST_RA_MIN_IMPACT = "PST_RA_Min_impact";
    private static final String LOOP_FLOW_COUNTRIES = "LF_Constraint_";

    public RaoParametersService(MinioAdapter minioAdapter) {
        this.minioAdapter = minioAdapter;
    }

    public String uploadJsonRaoParameters(RequestMessage requestMessage, String destinationKey) {
        RaoParameters raoParameters = createRaoParametersFromRequest(requestMessage);
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        JsonRaoParameters.write(raoParameters, outputStream);
        String jsonRaoParametersFilePath = String.format(NamingRules.S_INPUTS_S, destinationKey, NamingRules.JSON_RAO_PARAMETERS_FILE_NAME);
        minioAdapter.uploadArtifact(jsonRaoParametersFilePath, new ByteArrayInputStream(outputStream.toByteArray()));
        return jsonRaoParametersFilePath;
    }

    public RaoParameters createRaoParametersFromRequest(RequestMessage requestMessage) {
        RaoParameters raoParameters = RaoParameters.load();

        setLoopFlowCountries(requestMessage, raoParameters);
        setPstPenaltyCost(requestMessage, raoParameters);
        setAbsoluteMinimumImpactThreshold(requestMessage, raoParameters);

        return raoParameters;
    }

    private static void setPstPenaltyCost(RequestMessage requestMessage, RaoParameters raoParameters) {
        Optional<Property> pstRaMinImpactOptional = requestMessage.getHeader().getProperty().stream()
            .filter(property -> property.getName().equalsIgnoreCase(PST_RA_MIN_IMPACT)).findFirst();
        double pstRaMinImpact = 0.0D;
        if (pstRaMinImpactOptional.isPresent()) {
            pstRaMinImpact += Double.parseDouble(pstRaMinImpactOptional.get().getValue());
        }
        raoParameters.getRangeActionsOptimizationParameters().setPstPenaltyCost(pstRaMinImpact);
    }

    private static void setAbsoluteMinimumImpactThreshold(RequestMessage requestMessage, RaoParameters raoParameters) {
        Optional<Property> topoRaMinImpactOptional = requestMessage.getHeader().getProperty().stream()
            .filter(property -> property.getName().equalsIgnoreCase(TOPO_RA_MIN_IMPACT)).findFirst();
        double topoRaMinImpact = 0.0D;
        if (topoRaMinImpactOptional.isPresent()) {
            topoRaMinImpact += Double.parseDouble(topoRaMinImpactOptional.get().getValue());
        }
        raoParameters.getTopoOptimizationParameters().setAbsoluteMinImpactThreshold(topoRaMinImpact);
    }

    private static void setLoopFlowCountries(RequestMessage requestMessage, RaoParameters raoParameters) {
        Set<Country> loopFlowCountries = requestMessage.getHeader().getProperty().stream()
            .filter(property -> property.getName().toUpperCase().startsWith(LOOP_FLOW_COUNTRIES.toUpperCase()) && property.getValue().equalsIgnoreCase(Boolean.TRUE.toString()))
            .map(property -> property.getName().substring(property.getName().lastIndexOf('_') + 1))
            .map(RaoParametersService::convertGermanyZones)
            .map(Country::valueOf)
            .collect(Collectors.toSet());

        LoopFlowParametersExtension loopFlowParameters;
        if (raoParameters.hasExtension(LoopFlowParametersExtension.class)) {
            loopFlowParameters = raoParameters.getExtension(LoopFlowParametersExtension.class);
        } else {
            loopFlowParameters = new LoopFlowParametersExtension();
            raoParameters.addExtension(LoopFlowParametersExtension.class, loopFlowParameters);
        }
        loopFlowParameters.setCountries(loopFlowCountries);
    }

    private static String convertGermanyZones(String zone) {
        if (GERMAN_ZONES.contains(zone)) {
            return Country.DE.toString();
        } else {
            return zone;
        }
    }
}
