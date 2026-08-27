/*
 * Copyright (c) 2026, RTE (http://www.rte-france.com)
 *  This Source Code Form is subject to the terms of the Mozilla Public
 *  License, v. 2.0. If a copy of the MPL was not distributed with this
 *  file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package com.farao_community.farao.gridcapa_core_cc.app.sem;

import com.powsybl.iidm.network.Country;
import com.powsybl.iidm.network.Network;
import com.powsybl.iidm.network.Substation;
import com.powsybl.iidm.network.VoltageLevel;
import com.powsybl.iidm.reducer.NetworkPredicate;
import com.powsybl.iidm.reducer.NetworkReducer;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * @author Vincent Bochet {@literal <vincent.bochet at rte-france.com>}
 */
class SemTest {

    private static final Country IRELAND_COUNTRY_CODE = Country.RU;

    private final NetworkPredicate corePredicate = new NetworkPredicate() {
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

    private final NetworkPredicate semPredicate = new NetworkPredicate() {
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

    @Test
    void test() {
        final String networkFile = "/sem/20260326_0030_2D7_UX0_FEXPORTGRIDMODEL_CGM_17XTSO-CS------W.uct";
        final Path networkPath = Paths.get(getClass().getResource(networkFile).getPath());
        final Network coreNetwork = Network.read(networkPath);
        final Network semNetwork = Network.read(networkPath);

        final NetworkReducer coreReducer = NetworkReducer.builder()
            .withNetworkPredicate(corePredicate)
            .withBoundaryLines(true)
            .build();
        coreReducer.reduce(coreNetwork);

        final NetworkReducer semReducer = NetworkReducer.builder()
            .withNetworkPredicate(semPredicate)
            .withBoundaryLines(true)
            .build();
        semReducer.reduce(semNetwork);

        coreNetwork.write("XIIDM", null, Path.of(networkPath.getParent().toString(), "coreNetwork.xiidm"));
        semNetwork.write("XIIDM", null, Path.of(networkPath.getParent().toString(), "semNetwork.xiidm"));
    }
}
