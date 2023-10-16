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
import com.farao_community.farao.rao_api.json.JsonRaoParameters;
import com.farao_community.farao.rao_api.parameters.RaoParameters;
import com.farao_community.farao.rao_api.parameters.extensions.LoopFlowParametersExtension;
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
    private static final String MAX_CRA = "Max_cRA_";
    private static final String MAX_TOPO_CRA = "Max_Topo_cRA_";
    private static final String MAX_PST_CRA = "Max_PST_cRA_";

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
        setMaxCurativeRaPerTso(requestMessage, raoParameters);
        setMaxCurativeTopoPerTso(requestMessage, raoParameters);
        setMaxCurativePstPerTso(requestMessage, raoParameters);
        return raoParameters;
    }

    private void setMaxCurativePstPerTso(RequestMessage requestMessage, RaoParameters raoParameters) {
        Map<String, Integer> maxNbrCurativePstByTso = requestMessage.getHeader().getProperty().stream()
                .filter(property -> property.getName().toUpperCase().startsWith(MAX_PST_CRA.toUpperCase()))
                .collect(Collectors.toMap(property -> property.getName().substring(property.getName().lastIndexOf('_') + 1), property -> Integer.parseInt(property.getValue())));
        raoParameters.getRaUsageLimitsPerContingencyParameters().setMaxCurativePstPerTso(maxNbrCurativePstByTso);
    }

    private void setMaxCurativeTopoPerTso(RequestMessage requestMessage, RaoParameters raoParameters) {
        Map<String, Integer> maxNbrTopoCurRaByTso = requestMessage.getHeader().getProperty().stream()
                .filter(property -> property.getName().toUpperCase().startsWith(MAX_TOPO_CRA.toUpperCase()))
                .collect(Collectors.toMap(property -> property.getName().substring(property.getName().lastIndexOf('_') + 1), property -> Integer.parseInt(property.getValue())));
        raoParameters.getRaUsageLimitsPerContingencyParameters().setMaxCurativeTopoPerTso(maxNbrTopoCurRaByTso);
    }

    private void setMaxCurativeRaPerTso(RequestMessage requestMessage, RaoParameters raoParameters) {
        Map<String, Integer> maxNbrCurRaByTso = requestMessage.getHeader().getProperty().stream()
                .filter(property -> property.getName().toUpperCase().startsWith(MAX_CRA.toUpperCase()))
                .collect(Collectors.toMap(property -> property.getName().substring(property.getName().lastIndexOf('_') + 1), property -> Integer.parseInt(property.getValue())));
        raoParameters.getRaUsageLimitsPerContingencyParameters().setMaxCurativeRaPerTso(maxNbrCurRaByTso);
    }

    private void setPstPenaltyCost(RequestMessage requestMessage, RaoParameters raoParameters) {
        Optional<Property> pstRaMinImpactOptional = requestMessage.getHeader().getProperty().stream()
                .filter(property -> property.getName().equalsIgnoreCase(PST_RA_MIN_IMPACT)).findFirst();
        double pstRaMinImpact = 0.0D;
        if (pstRaMinImpactOptional.isPresent()) {
            pstRaMinImpact += Double.parseDouble(pstRaMinImpactOptional.get().getValue());
        }
        raoParameters.getRangeActionsOptimizationParameters().setPstPenaltyCost(pstRaMinImpact);
    }

    private void setAbsoluteMinimumImpactThreshold(RequestMessage requestMessage, RaoParameters raoParameters) {
        Optional<Property> topoRaMinImpactOptional = requestMessage.getHeader().getProperty().stream()
                .filter(property -> property.getName().equalsIgnoreCase(TOPO_RA_MIN_IMPACT)).findFirst();
        double topoRaMinImpact = 0.0D;
        if (topoRaMinImpactOptional.isPresent()) {
            topoRaMinImpact += Double.parseDouble(topoRaMinImpactOptional.get().getValue());
        }
        raoParameters.getTopoOptimizationParameters().setAbsoluteMinImpactThreshold(topoRaMinImpact);
    }

    private void setLoopFlowCountries(RequestMessage requestMessage, RaoParameters raoParameters) {
        List<String> loopFlowZones = requestMessage.getHeader().getProperty().stream()
                .filter(property -> property.getName().toUpperCase().startsWith(LOOP_FLOW_COUNTRIES.toUpperCase()) && property.getValue().equalsIgnoreCase(Boolean.TRUE.toString()))
                .map(property -> property.getName().substring(property.getName().lastIndexOf('_') + 1)).collect(Collectors.toList());
        Set<Country> loopFlowCountries = loopFlowZones.stream().map(this::convertGermanyZones).map(Country::valueOf).collect(Collectors.toSet());
        LoopFlowParametersExtension loopFlowParameters;
        if (raoParameters.hasExtension(LoopFlowParametersExtension.class)) {
            loopFlowParameters = raoParameters.getExtension(LoopFlowParametersExtension.class);
        } else {
            loopFlowParameters = new LoopFlowParametersExtension();
            raoParameters.addExtension(LoopFlowParametersExtension.class, loopFlowParameters);
        }
        loopFlowParameters.setCountries(loopFlowCountries);
    }

    private String convertGermanyZones(String zone) {
        if (GERMAN_ZONES.contains(zone)) {
            return Country.DE.toString();
        } else {
            return zone;
        }
    }
}
