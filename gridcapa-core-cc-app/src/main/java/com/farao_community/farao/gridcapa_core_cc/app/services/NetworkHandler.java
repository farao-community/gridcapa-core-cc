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
 * UCTE-DEF file does not provide configuration for default nominal voltage setup.
 * This post processor modifies default nominal voltages in order to adapt it to FMAX calculation based on IMAX.
 * By default, UCTE sets nominal voltage to 220 and 380kV for the voltage levels 6 and 7,
 * whereas default values of Core countries are 225 and 400 kV instead.
 */
public final class NetworkHandler {

    private static final int VOLTAGE_LEVEL_6_UCTE = 220;
    private static final int VOLTAGE_LEVEL_7_UCTE = 380;
    private static final int VOLTAGE_LEVEL_6_CORE = 225;
    private static final int VOLTAGE_LEVEL_7_CORE = 400;

    private static final Logger LOGGER = LoggerFactory.getLogger(NetworkHandler.class);

    private NetworkHandler() {
        throw new IllegalStateException("Utility class");
    }

    public static Network loadNetwork(final String filename, final InputStream inputStream) {
        LOGGER.info("IIDM import of network : {}", filename);
        final Network network = Network.read(filename, inputStream);
        updateVoltageLevelNominalV(network);
        return network;
    }

    static void updateVoltageLevelNominalV(final Network network) {
        network.getVoltageLevelStream().forEach(voltageLevel -> {
            if (safeDoubleEquals(voltageLevel.getNominalV(), VOLTAGE_LEVEL_7_UCTE)) {
                voltageLevel.setNominalV(VOLTAGE_LEVEL_7_CORE);
            } else if (safeDoubleEquals(voltageLevel.getNominalV(), VOLTAGE_LEVEL_6_UCTE)) {
                voltageLevel.setNominalV(VOLTAGE_LEVEL_6_CORE);
            }
            // Else it should not be changed cause is not equal to the default nominal voltage of voltage levels 6 or 7
        });
    }

    private static boolean safeDoubleEquals(final double a, final double b) {
        return Math.abs(a - b) < 1e-3;
    }

}
