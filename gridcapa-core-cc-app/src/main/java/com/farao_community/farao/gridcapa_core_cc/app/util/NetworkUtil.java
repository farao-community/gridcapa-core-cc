/*
 * Copyright (c) 2026, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package com.farao_community.farao.gridcapa_core_cc.app.util;

import com.powsybl.iidm.network.Country;
import com.powsybl.iidm.network.Generator;
import com.powsybl.iidm.network.Load;
import com.powsybl.iidm.network.Network;
import com.powsybl.iidm.network.Substation;
import com.powsybl.iidm.network.VoltageLevel;
import com.powsybl.iidm.reducer.NetworkPredicate;
import com.powsybl.iidm.reducer.NetworkReducer;
import com.powsybl.openrao.data.crac.api.State;
import com.powsybl.openrao.data.raoresult.api.RaoResult;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

public final class NetworkUtil {
    private static final String ALEGRO_GEN_BE = "XLI_OB1B_generator";
    private static final String ALEGRO_GEN_DE = "XLI_OB1A_generator";
    private static final Country IRELAND_COUNTRY_CODE = Country.RU; // FIXME This is a hack until PowSyBl's Country enum properly reflects UCTE country conventions (should be available in next release at beginning of October)

    private NetworkUtil() {
    }

    private static final NetworkPredicate CONTINENTAL_SUBNETWORK_PREDICATE = new NetworkPredicate() {
        @Override
        public boolean test(final Substation substation) {
            return substation.getCountry().orElse(null) != IRELAND_COUNTRY_CODE;
        }

        @Override
        public boolean test(final VoltageLevel voltageLevel) {
            final Substation substation = voltageLevel.getSubstation().orElse(null);
            return substation == null || substation.getCountry().orElse(null) != IRELAND_COUNTRY_CODE;
        }
    };

    private static final NetworkPredicate SEM_SUBNETWORK_PREDICATE = new NetworkPredicate() {
        @Override
        public boolean test(final Substation substation) {
            return substation.getCountry().orElse(null) == IRELAND_COUNTRY_CODE;
        }

        @Override
        public boolean test(final VoltageLevel voltageLevel) {
            final Substation substation = voltageLevel.getSubstation().orElse(null);
            return substation != null && substation.getCountry().orElse(null) == IRELAND_COUNTRY_CODE;
        }
    };

    public static final NetworkReducer SEM_NETWORK_REDUCER = NetworkReducer.builder()
        .withNetworkPredicate(NetworkUtil.SEM_SUBNETWORK_PREDICATE)
        .withBoundaryLines(true)
        .build();

    public static final NetworkReducer CONTINENTAL_NETWORK_REDUCER = NetworkReducer.builder()
        .withNetworkPredicate(NetworkUtil.CONTINENTAL_SUBNETWORK_PREDICATE)
        .withBoundaryLines(true)
        .build();

    public static void applyActivatedRemedialActionsForState(final Network network, final RaoResult raoResult, final State state) {
        // network actions need to be applied BEFORE range actions because to apply HVDC range actions we need to apply AC emulation deactivation network actions beforehand
        raoResult.getActivatedNetworkActionsDuringState(state).forEach(networkAction -> networkAction.apply(network));
        raoResult.getActivatedRangeActionsDuringState(state).forEach(rangeAction -> rangeAction.apply(
            network, raoResult.getOptimizedSetPointOnState(state, rangeAction)
        ));
    }

    public static void applyWorkaround(final Network network) {
        // work around until the problem of "Too many loads connected to this bus" is corrected
        NetworkUtil.removeVirtualLoadsFromNetwork(network);
        // work around until the problem of "Too many generators connected to this bus" is corrected
        NetworkUtil.removeAlegroVirtualGeneratorsFromNetwork(network);
        // work around until fictitious loads and generators are not created in groovy script anymore
        NetworkUtil.removeFictitiousGeneratorsFromNetwork(network);
        NetworkUtil.removeFictitiousLoadsFromNetwork(network);
    }

    private static void removeVirtualLoadsFromNetwork(final Network network) {
        final List<String> virtualLoadsList = new ArrayList<>();
        network.getSubstationStream().forEach(substation -> substation.getVoltageLevels()
            .forEach(voltageLevel -> voltageLevel.getBusBreakerView().getBuses()
                .forEach(bus -> bus.getLoadStream().filter(busLoad -> busLoad.getNameOrId().contains("_virtualLoad")).forEach(virtualLoad -> virtualLoadsList.add(virtualLoad.getNameOrId()))
                )));
        virtualLoadsList.forEach(virtualLoad -> network.getLoad(virtualLoad).remove());
    }

    private static void removeAlegroVirtualGeneratorsFromNetwork(final Network network) {
        Optional.ofNullable(network.getGenerator(ALEGRO_GEN_BE)).ifPresent(Generator::remove);
        Optional.ofNullable(network.getGenerator(ALEGRO_GEN_DE)).ifPresent(Generator::remove);
    }

    private static void removeFictitiousGeneratorsFromNetwork(final Network network) {
        final Set<String> generatorsToRemove = network.getGeneratorStream().filter(Generator::isFictitious).map(Generator::getId).collect(Collectors.toSet());
        generatorsToRemove.forEach(id -> network.getGenerator(id).remove());
    }

    private static void removeFictitiousLoadsFromNetwork(final Network network) {
        final Set<String> loadsToRemove = network.getLoadStream().filter(Load::isFictitious).map(Load::getId).collect(Collectors.toSet());
        loadsToRemove.forEach(id -> network.getLoad(id).remove());
    }
}
