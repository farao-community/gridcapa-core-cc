/*
 * Copyright (c) 2026, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package com.farao_community.farao.gridcapa_core_cc.app.util;

import com.powsybl.iidm.network.Country;
import com.powsybl.iidm.network.Substation;
import com.powsybl.iidm.network.VoltageLevel;
import com.powsybl.iidm.reducer.NetworkPredicate;

public class NetworkUtil {
    private static final Country IRELAND_COUNTRY_CODE = Country.RU; // FIXME This is a hack until PowSyBl's Country enum properly reflects UCTE country conventions (should be available in next release at beginning of October)

    public static final NetworkPredicate CONTINENTAL_SUBNETWORK = new NetworkPredicate() {
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

    public static final NetworkPredicate SEM_SUBNETWORK = new NetworkPredicate() {
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
}
