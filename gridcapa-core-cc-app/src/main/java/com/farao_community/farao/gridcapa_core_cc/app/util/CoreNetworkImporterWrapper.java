/*
 * Copyright (c) 2021, RTE (http://www.rte-france.com)
 */
package com.farao_community.farao.gridcapa_core_cc.app.util;

import com.powsybl.iidm.network.*;

import java.nio.file.Path;

/**
 * @author Baptiste Seguinot {@literal <baptiste.seguinot at rte-france.com}
 */
public final class CoreNetworkImporterWrapper {

    private CoreNetworkImporterWrapper() {
        // do nothing, sonar
    }

    public static Network loadNetwork(Path path) {
        Network network = Network.read(path);
        processNetworkForCoreCC(network);
        return network;
    }

    private static void processNetworkForCoreCC(Network network) {
        /*
         UCTE-DEF file does not provide configuration for default nominal voltage setup.

         This post processor modifies default nominal voltages in order to adapt it to FMAX
         calculation based on IMAX.

         By default, UCTE sets nominal voltage to 220 and 380kV for the voltage levels 6 and 7, whereas
         default values of Core countries are 225 and 400 kV instead.
         */
        updateVoltageLevelNominalV(network);

        /*
        When importing an UCTE network file, powsybl ignores generators and loads that do not have an initial power flow.

        It can cause an error if a GLSK file associated to this network includes some factors on
        these nodes. The GLSK importers looks for a Generator (GSK) or Load (LSK) associated to this
        node. If the Generator/Load does not exist, the GLSK cannot be created.

        This post process fix this problem, by creating for all missing generators a generator (P, Q = 0),
        and all missing loads a load (P, Q = 0).
        */
        createMissingGeneratorsAndLoads(network);

        /*
        Temporary patch to make OLF work
         */
        alignDisconnectionOfTieLines(network);
    }

    private static void updateVoltageLevelNominalV(Network network) {
        network.getVoltageLevelStream().forEach(voltageLevel -> {
            if (safeDoubleEquals(voltageLevel.getNominalV(), 380)) {
                voltageLevel.setNominalV(400);
            } else if (safeDoubleEquals(voltageLevel.getNominalV(), 220)) {
                voltageLevel.setNominalV(225);
            } else {
                // Should not be changed cause is not equal to the default nominal voltage of voltage levels 6 or 7
            }
        });
    }

    private static void createMissingGeneratorsAndLoads(Network network) {
        network.getVoltageLevelStream().forEach(voltageLevel -> {
            voltageLevel.getBusBreakerView().getBuses().forEach(bus -> createMissingGenerator(network, voltageLevel, bus.getId()));
            voltageLevel.getBusBreakerView().getBuses().forEach(bus -> createMissingLoad(network, voltageLevel, bus.getId()));
        });
    }

    private static void createMissingGenerator(Network network, VoltageLevel voltageLevel, String busId) {
        String generatorId = busId + "_generator";
        if (network.getGenerator(generatorId) == null) {
            try {
                voltageLevel.newGenerator()
                        .setBus(busId)
                        .setEnsureIdUnicity(true)
                        .setId(generatorId)
                        .setMaxP(9999)
                        .setMinP(0)
                        .setTargetP(0)
                        .setTargetQ(0)
                        .setTargetV(voltageLevel.getNominalV())
                        .setVoltageRegulatorOn(false)
                        .add()
                        .newMinMaxReactiveLimits().setMaxQ(99999).setMinQ(99999).add();
            } catch (Exception e) {
                // Can't create generator
            }
        }
    }

    private static void createMissingLoad(Network network, VoltageLevel voltageLevel, String busId) {
        String loadId = busId + "_load";
        if (network.getLoad(loadId) == null) {
            try {
                voltageLevel.newLoad()
                        .setBus(busId)
                        .setEnsureIdUnicity(true)
                        .setId(loadId)
                        .setP0(0)
                        .setQ0(0)
                        .setLoadType(LoadType.FICTITIOUS)
                        .add();
            } catch (Exception e) {
                // Can't create load
            }
        }
    }

    private static boolean safeDoubleEquals(double a, double b) {
        return Math.abs(a - b) < 1e-3;
    }

    private static void alignDisconnectionOfTieLines(Network network) {
        network.getBranchStream()
                .filter(TieLine.class::isInstance)
                .forEach(tl -> {
                    if (!tl.getTerminal1().isConnected() || !tl.getTerminal2().isConnected()) {
                        tl.getTerminal1().disconnect();
                        tl.getTerminal2().disconnect();
                    }
                });
    }
}
