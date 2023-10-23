/*
 * Copyright (c) 2023, RTE (http://www.rte-france.com)
 *  This Source Code Form is subject to the terms of the Mozilla Public
 *  License, v. 2.0. If a copy of the MPL was not distributed with this
 *  file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package com.farao_community.farao.gridcapa_core_cc.app.services;

import com.powsybl.iidm.network.Network;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStream;

/**
 * @author Ameni Walha {@literal <ameni.walha at rte-france.com>}
 */
public final class NetworkHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(NetworkHandler.class);

    private NetworkHandler() {
        throw new IllegalStateException("Utility class");
    }

    public static Network loadNetwork(String filename, InputStream inputStream) {
        LOGGER.info("IIDM import of network : {}", filename);
        Network network = Network.read(filename, inputStream);
        processNetworkForCore(network);
        return network;
    }

    static void processNetworkForCore(Network network) {
        /*
         UCTE-DEF file does not provide configuration for default nominal voltage setup.

         This post processor modifies default nominal voltages in order to adapt it to FMAX
         calculation based on IMAX.

         By default, UCTE sets nominal voltage to 220 and 380kV for the voltage levels 6 and 7, whereas
         default values of Core countries are 225 and 400 kV instead.
         */
        updateVoltageLevelNominalV(network);

    }

    static void updateVoltageLevelNominalV(Network network) {
        network.getVoltageLevelStream().forEach(voltageLevel -> {
            if (safeDoubleEquals(voltageLevel.getNominalV(), 380)) {
                voltageLevel.setNominalV(400);
            } else if (safeDoubleEquals(voltageLevel.getNominalV(), 220)) {
                voltageLevel.setNominalV(225);
            }
            // Else it should not be changed cause is not equal to the default nominal voltage of voltage levels 6 or 7
        });
    }

    private static boolean safeDoubleEquals(double a, double b) {
        return Math.abs(a - b) < 1e-3;
    }

}
