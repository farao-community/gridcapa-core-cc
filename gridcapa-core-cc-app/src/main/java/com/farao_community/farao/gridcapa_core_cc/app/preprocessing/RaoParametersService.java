/*
 * Copyright (c) 2023, RTE (http://www.rte-france.com)
 *  This Source Code Form is subject to the terms of the Mozilla Public
 *  License, v. 2.0. If a copy of the MPL was not distributed with this
 *  file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.farao_community.farao.gridcapa_core_cc.app.preprocessing;

import com.farao_community.farao.gridcapa_core_cc.app.inputs.rao_request.Property;
import com.farao_community.farao.gridcapa_core_cc.app.inputs.rao_request.RequestMessage;
import com.farao_community.farao.gridcapa_core_cc.app.util.NamingRules;
import com.farao_community.farao.minio_adapter.starter.MinioAdapter;
import com.powsybl.iidm.network.Country;
import com.powsybl.openrao.raoapi.json.JsonRaoParameters;
import com.powsybl.openrao.raoapi.parameters.LoopFlowParameters;
import com.powsybl.openrao.raoapi.parameters.RaoParameters;
import com.powsybl.openrao.raoapi.parameters.RelativeMarginsParameters;
import com.powsybl.openrao.virtualhubs.BorderDirection;
import com.powsybl.openrao.virtualhubs.MarketArea;
import com.powsybl.openrao.virtualhubs.VirtualHub;
import com.powsybl.openrao.virtualhubs.VirtualHubsConfiguration;
import org.apache.commons.lang3.tuple.Pair;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * @author Mohamed BenRejeb {@literal <mohamed.ben-rejeb at rte-france.com>}
 * @author Vincent Bochet {@literal <vincent.bochet at rte-france.com>}
 */
@Service
public class RaoParametersService {
    private final MinioAdapter minioAdapter;
    private static final List<String> GERMAN_ZONES = Arrays.asList("D2", "D4", "D7", "D8");
    private static final String TOPO_RA_MIN_IMPACT = "Topo_RA_Min_impact";
    private static final String PST_RA_MIN_IMPACT = "PST_RA_Min_impact";
    private static final String LOOP_FLOW_COUNTRIES = "LF_Constraint_";

    private static final String SIMPLE_PTDF_BOUNDARIES_FORMAT = "{%s}-{%s}";
    private static final String COMPLEX_PTDF_BOUNDARIES_FORMAT = "{%s}-{%s}-{%s}+{%s}";

    public RaoParametersService(MinioAdapter minioAdapter) {
        this.minioAdapter = minioAdapter;
    }

    public String uploadJsonRaoParameters(RequestMessage requestMessage, VirtualHubsConfiguration virtualHubsConfiguration, String destinationKey) {
        RaoParameters raoParameters = createRaoParametersFromRequest(requestMessage, virtualHubsConfiguration);
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        JsonRaoParameters.write(raoParameters, outputStream);
        String jsonRaoParametersFilePath = String.format(NamingRules.S_INPUTS_S, destinationKey, NamingRules.JSON_RAO_PARAMETERS_FILE_NAME);
        minioAdapter.uploadArtifact(jsonRaoParametersFilePath, new ByteArrayInputStream(outputStream.toByteArray()));
        return jsonRaoParametersFilePath;
    }

    public RaoParameters createRaoParametersFromRequest(RequestMessage requestMessage, VirtualHubsConfiguration virtualHubsConfiguration) {
        RaoParameters raoParameters = RaoParameters.load();

        setLoopFlowCountries(requestMessage, raoParameters);
        setPstPenaltyCost(requestMessage, raoParameters);
        setAbsoluteMinimumImpactThreshold(requestMessage, raoParameters);
        setPtdfBoundaries(virtualHubsConfiguration, raoParameters);

        return raoParameters;
    }

    static void setPstPenaltyCost(RequestMessage requestMessage, RaoParameters raoParameters) {
        Optional<Property> pstRaMinImpactOptional = requestMessage.getHeader().getProperty().stream()
            .filter(property -> property.getName().equalsIgnoreCase(PST_RA_MIN_IMPACT)).findFirst();
        double pstRaMinImpact = 0.0D;
        if (pstRaMinImpactOptional.isPresent()) {
            pstRaMinImpact += Double.parseDouble(pstRaMinImpactOptional.get().getValue());
        }
        raoParameters.getRangeActionsOptimizationParameters().setPstRAMinImpactThreshold(pstRaMinImpact);
    }

    static void setAbsoluteMinimumImpactThreshold(RequestMessage requestMessage, RaoParameters raoParameters) {
        Optional<Property> topoRaMinImpactOptional = requestMessage.getHeader().getProperty().stream()
            .filter(property -> property.getName().equalsIgnoreCase(TOPO_RA_MIN_IMPACT)).findFirst();
        double topoRaMinImpact = 0.0D;
        if (topoRaMinImpactOptional.isPresent()) {
            topoRaMinImpact += Double.parseDouble(topoRaMinImpactOptional.get().getValue());
        }
        raoParameters.getTopoOptimizationParameters().setAbsoluteMinImpactThreshold(topoRaMinImpact);
    }

    static void setLoopFlowCountries(final RequestMessage requestMessage, final RaoParameters raoParameters) {
        final Set<Country> loopFlowCountries = requestMessage.getHeader().getProperty().stream()
            .filter(property -> property.getName().toUpperCase().startsWith(LOOP_FLOW_COUNTRIES.toUpperCase())
                                && property.getValue().equalsIgnoreCase(Boolean.TRUE.toString()))
            .map(property -> property.getName().substring(property.getName().lastIndexOf('_') + 1))
            .map(RaoParametersService::convertGermanyZones)
            .map(Country::valueOf)
            .collect(Collectors.toSet());

        final LoopFlowParameters loopFlowParameters =
                raoParameters.getLoopFlowParameters()
                        .orElseGet(() -> {
                            final LoopFlowParameters newParams = new LoopFlowParameters();
                            raoParameters.setLoopFlowParameters(newParams);
                            return newParams;
                        });
        loopFlowParameters.setCountries(loopFlowCountries);
    }

    static void setPtdfBoundaries(final VirtualHubsConfiguration virtualHubsConfiguration,
                                  final RaoParameters raoParameters) {
        final List<String> ptdfBoundariesStrings = getPtdfBoundariesStrings(virtualHubsConfiguration);

        final RelativeMarginsParameters relativeMarginsParameters =
                raoParameters.getRelativeMarginsParameters()
                        .orElseGet(() -> {
                            final RelativeMarginsParameters newParams = new RelativeMarginsParameters();
                            raoParameters.setRelativeMarginsParameters(newParams);
                            return newParams;
                        });
        relativeMarginsParameters.setPtdfBoundariesFromString(ptdfBoundariesStrings);
    }

    private static List<String> getPtdfBoundariesStrings(VirtualHubsConfiguration virtualHubsConfiguration) {
        return Stream.concat(
            getPtdfBoundariesFromBorderDirections(virtualHubsConfiguration),
            getPtdfBoundariesFromVirtualHubs(virtualHubsConfiguration)
        ).toList();
    }

    private static Stream<String> getPtdfBoundariesFromBorderDirections(VirtualHubsConfiguration virtualHubsConfiguration) {
        List<String> marketParticipantsCodes = virtualHubsConfiguration.getMarketAreas().stream()
            .filter(ma -> ma.isMcParticipant() || ma.isAhc())
            .map(MarketArea::code)
            .toList();

        return virtualHubsConfiguration.getBorderDirections().stream()
            .filter(borderDirection -> bothBordersAreMarketParticipant(marketParticipantsCodes, borderDirection))
            .map(borderDirection -> RaoParametersService.getOrderedPtdfBoundaryFromBorderDirection(borderDirection, virtualHubsConfiguration))
            .distinct();
    }

    private static Stream<String> getPtdfBoundariesFromVirtualHubs(VirtualHubsConfiguration virtualHubsConfiguration) {
        List<VirtualHub> marketParticipants = virtualHubsConfiguration.getVirtualHubs().stream()
            .filter(vh -> vh.isMcParticipant() || vh.isAhc())
            .toList();

        Map<String, Pair<VirtualHub, VirtualHub>> pairedVirtualHubs = new HashMap<>();
        for (VirtualHub vh : marketParticipants) {
            if (vh.oppositeHub() != null && pairedVirtualHubs.containsKey(vh.oppositeHub())) {
                VirtualHub oppositeHub = pairedVirtualHubs.get(vh.oppositeHub()).getKey();
                pairedVirtualHubs.put(vh.oppositeHub(), Pair.of(oppositeHub, vh)); // Complete existing pair with current virtual hub
            } else {
                pairedVirtualHubs.put(vh.code(), Pair.of(vh, null)); // Add a new partial pair
            }
        }

        return pairedVirtualHubs.values().stream()
            .map(RaoParametersService::getPtdfBoundaryFromVirtualHub);
    }

    private static boolean bothBordersAreMarketParticipant(List<String> marketParticipants, BorderDirection borderDirection) {
        return marketParticipants.contains(borderDirection.from())
            && marketParticipants.contains(borderDirection.to());
    }

    private static String getOrderedPtdfBoundaryFromBorderDirection(BorderDirection bd, VirtualHubsConfiguration virtualHubsConfiguration) {
        if (bd.from().compareTo(bd.to()) < 0) {
            return String.format(SIMPLE_PTDF_BOUNDARIES_FORMAT, getEicIfAvailable(bd.from(), virtualHubsConfiguration), getEicIfAvailable(bd.to(), virtualHubsConfiguration));
        } else {
            return String.format(SIMPLE_PTDF_BOUNDARIES_FORMAT, getEicIfAvailable(bd.to(), virtualHubsConfiguration), getEicIfAvailable(bd.from(), virtualHubsConfiguration));
        }
    }

    private static String getEicIfAvailable(String code, VirtualHubsConfiguration virtualHubsConfiguration) {
        Optional<MarketArea> optionalMA = virtualHubsConfiguration.getMarketAreas().stream()
            .filter(ma -> ma.code().equals(code))
            .findAny();
        return optionalMA.isPresent() ? optionalMA.get().eic() : code;
    }

    private static String getPtdfBoundaryFromVirtualHub(Pair<VirtualHub, VirtualHub> pair) {
        VirtualHub firstVirtualHub = pair.getKey();
        VirtualHub secondVirtualHub = pair.getValue();

        if (secondVirtualHub == null) {
            return String.format(SIMPLE_PTDF_BOUNDARIES_FORMAT,
                firstVirtualHub.relatedMa().eic(), firstVirtualHub.eic());
        } else {
            return String.format(COMPLEX_PTDF_BOUNDARIES_FORMAT,
                firstVirtualHub.relatedMa().eic(), firstVirtualHub.eic(),
                secondVirtualHub.relatedMa().eic(), secondVirtualHub.eic());
        }
    }

    private static String convertGermanyZones(String zone) {
        if (GERMAN_ZONES.contains(zone)) {
            return Country.DE.toString();
        } else {
            return zone;
        }
    }
}
