/*
 * Copyright (c) 2023, RTE (http://www.rte-france.com)
 *  This Source Code Form is subject to the terms of the Mozilla Public
 *  License, v. 2.0. If a copy of the MPL was not distributed with this
 *  file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.farao_community.farao.gridcapa_core_cc.app.preprocessing;

import com.farao_community.farao.gridcapa_core_cc.app.inputs.rao_request.Property;
import com.farao_community.farao.gridcapa_core_cc.app.inputs.rao_request.RequestMessage;
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

import static com.farao_community.farao.gridcapa_core_cc.app.util.NamingRules.JSON_RAO_PARAMETERS_FILE_NAME;
import static com.farao_community.farao.gridcapa_core_cc.app.util.NamingRules.S_INPUTS_S;

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
    private static final Double ZERO_AS_DOUBLE = 0.0D;

    private static final String SIMPLE_PTDF_BOUNDARIES_FORMAT = "{%s}-{%s}";
    private static final String COMPLEX_PTDF_BOUNDARIES_FORMAT = "{%s}-{%s}-{%s}+{%s}";

    public RaoParametersService(final MinioAdapter minioAdapter) {
        this.minioAdapter = minioAdapter;
    }

    public String uploadJsonRaoParameters(final RequestMessage requestMessage,
                                          final VirtualHubsConfiguration virtualHubsConfiguration,
                                          final String destinationKey) {
        final RaoParameters raoParameters = createRaoParametersFromRequest(requestMessage, virtualHubsConfiguration);
        final ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        JsonRaoParameters.write(raoParameters, outputStream);
        final String jsonRaoParametersFilePath = String.format(S_INPUTS_S, destinationKey, JSON_RAO_PARAMETERS_FILE_NAME);
        minioAdapter.uploadArtifact(jsonRaoParametersFilePath, new ByteArrayInputStream(outputStream.toByteArray()));
        return jsonRaoParametersFilePath;
    }

    public RaoParameters createRaoParametersFromRequest(final RequestMessage requestMessage,
                                                        final VirtualHubsConfiguration virtualHubsConfiguration) {
        final RaoParameters raoParameters = RaoParameters.load();

        setLoopFlowCountries(requestMessage, raoParameters);
        setPstPenaltyCost(requestMessage, raoParameters);
        setAbsoluteMinimumImpactThreshold(requestMessage, raoParameters);
        setPtdfBoundaries(virtualHubsConfiguration, raoParameters);

        return raoParameters;
    }

    static void setPstPenaltyCost(final RequestMessage requestMessage,
                                  final RaoParameters raoParameters) {
        raoParameters.getRangeActionsOptimizationParameters()
                .setPstRAMinImpactThreshold(getNumericHeaderValue(requestMessage, PST_RA_MIN_IMPACT));
    }

    static void setAbsoluteMinimumImpactThreshold(RequestMessage requestMessage,
                                                  RaoParameters raoParameters) {
        raoParameters.getTopoOptimizationParameters()
                .setAbsoluteMinImpactThreshold(getNumericHeaderValue(requestMessage, TOPO_RA_MIN_IMPACT));
    }

    private static Double getNumericHeaderValue(final RequestMessage requestMessage,
                                                final String key) {
        Optional<Property> propertyOptional = requestMessage
                .getHeader()
                .getProperty().stream()
                .filter(property -> property.getName()
                        .equalsIgnoreCase(key))
                .findFirst();

        return propertyOptional
                .map(property -> {
                    try {
                        return Double.parseDouble(property.getValue());
                    } catch (final Exception e) {
                        return ZERO_AS_DOUBLE;
                    }
                })
                .orElse(ZERO_AS_DOUBLE);
    }

    static void setLoopFlowCountries(final RequestMessage requestMessage,
                                     final RaoParameters raoParameters) {
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

    private static List<String> getPtdfBoundariesStrings(final VirtualHubsConfiguration virtualHubsConfiguration) {
        return Stream.concat(
                getPtdfBoundariesFromBorderDirections(virtualHubsConfiguration),
                getPtdfBoundariesFromVirtualHubs(virtualHubsConfiguration)
        ).toList();
    }

    private static Stream<String> getPtdfBoundariesFromBorderDirections(final VirtualHubsConfiguration virtualHubsConfiguration) {
        List<String> marketParticipantsCodes = virtualHubsConfiguration.getMarketAreas().stream()
                .filter(ma -> ma.isMcParticipant() || ma.isAhc())
                .map(MarketArea::code)
                .toList();

        return virtualHubsConfiguration.getBorderDirections().stream()
                .filter(borderDirection -> bothBordersAreMarketParticipant(marketParticipantsCodes, borderDirection))
                .map(borderDirection -> RaoParametersService.getOrderedPtdfBoundaryFromBorderDirection(borderDirection, virtualHubsConfiguration))
                .distinct();
    }

    private static Stream<String> getPtdfBoundariesFromVirtualHubs(final VirtualHubsConfiguration virtualHubsConfiguration) {
        final List<VirtualHub> marketParticipants = virtualHubsConfiguration.getVirtualHubs().stream()
                .filter(vh -> vh.isMcParticipant() || vh.isAhc())
                .toList();

        final Map<String, Pair<VirtualHub, VirtualHub>> pairedVirtualHubs = new HashMap<>();
        for (final VirtualHub hub : marketParticipants) {
            final String oppositeKey = hub.oppositeHub();
            if (hub.oppositeHub() != null && pairedVirtualHubs.containsKey(oppositeKey)) {
                final VirtualHub oppositeHub = pairedVirtualHubs.get(oppositeKey).getKey();
                pairedVirtualHubs.put(oppositeKey, Pair.of(oppositeHub, hub)); // Complete existing pair with current virtual hub
            } else {
                pairedVirtualHubs.put(hub.code(), Pair.of(hub, null)); // Add a new partial pair
            }
        }

        return pairedVirtualHubs.values().stream()
                .map(RaoParametersService::getPtdfBoundaryFromVirtualHub);
    }

    private static boolean bothBordersAreMarketParticipant(final List<String> marketParticipants,
                                                           final BorderDirection borderDirection) {
        return marketParticipants.contains(borderDirection.from())
               && marketParticipants.contains(borderDirection.to());
    }

    private static String getOrderedPtdfBoundaryFromBorderDirection(final BorderDirection direction,
                                                                    final VirtualHubsConfiguration virtualHubsConfiguration) {
        if (direction.from().compareTo(direction.to()) < 0) {
            return String.format(SIMPLE_PTDF_BOUNDARIES_FORMAT, getEicIfAvailable(direction.from(), virtualHubsConfiguration),
                                 getEicIfAvailable(direction.to(), virtualHubsConfiguration));
        } else {
            return String.format(SIMPLE_PTDF_BOUNDARIES_FORMAT, getEicIfAvailable(direction.to(), virtualHubsConfiguration),
                                 getEicIfAvailable(direction.from(), virtualHubsConfiguration));
        }
    }

    private static String getEicIfAvailable(final String code,
                                            final VirtualHubsConfiguration virtualHubsConfiguration) {
        final Optional<MarketArea> optionalMA = virtualHubsConfiguration
                .getMarketAreas().stream()
                .filter(ma -> ma.code().equals(code))
                .findAny();
        return optionalMA.isPresent() ? optionalMA.get().eic() : code;
    }

    private static String getPtdfBoundaryFromVirtualHub(final Pair<VirtualHub, VirtualHub> hubs) {
        final VirtualHub firstVirtualHub = hubs.getKey();
        final VirtualHub secondVirtualHub = hubs.getValue();

        if (secondVirtualHub == null) {
            return String.format(SIMPLE_PTDF_BOUNDARIES_FORMAT,
                                 firstVirtualHub.relatedMa().eic(), firstVirtualHub.eic());
        } else {
            return String.format(COMPLEX_PTDF_BOUNDARIES_FORMAT,
                                 firstVirtualHub.relatedMa().eic(), firstVirtualHub.eic(),
                                 secondVirtualHub.relatedMa().eic(), secondVirtualHub.eic());
        }
    }

    private static String convertGermanyZones(final String zone) {
        if (GERMAN_ZONES.contains(zone)) {
            return Country.DE.toString();
        } else {
            return zone;
        }
    }
}
