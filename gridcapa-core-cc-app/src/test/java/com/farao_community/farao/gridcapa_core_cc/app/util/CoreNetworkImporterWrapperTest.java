/*
 * Copyright (c) 2023, RTE (http://www.rte-france.com)
 *  This Source Code Form is subject to the terms of the Mozilla Public
 *  License, v. 2.0. If a copy of the MPL was not distributed with this
 *  file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.farao_community.farao.gridcapa_core_cc.app.util;

import com.powsybl.iidm.network.Generator;
import com.powsybl.iidm.network.Load;
import com.powsybl.iidm.network.LoadType;
import com.powsybl.iidm.network.Network;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * @author Baptiste Seguinot {@literal <baptiste.seguinot at rte-france.com}
 * @author Thomas Bouquet {@literal <thomas.bouquet at rte-france.com>}
 */
class CoreNetworkImporterWrapperTest {

    @Test
    void testCoreNetworkImporterWrapper() {
        String networkFile = "/util/TestCase12NodesHvdc.uct";
        Path pathToNetworkFile = Paths.get(getClass().getResource(networkFile).getPath());
        Network network = CoreNetworkImporterWrapper.loadNetwork(pathToNetworkFile);

        // check nominal voltage
        // all network is on 400 kV, without the wrapper, the nominal voltage would have been 380 kV
        network.getVoltageLevelStream().forEach(vl -> assertEquals(400, vl.getNominalV(), 1e-3));

        // check missing generator and loads
        // no generation on node BBE1AA1 in the initial UCTE file
        Optional<Generator> generator = network.getBusBreakerView().getBus("BBE1AA1 ").getGeneratorStream().findAny();
        assertTrue(generator.isPresent());
        assertEquals("BBE1AA1 _generator", generator.get().getId());
        assertEquals(0, generator.get().getTargetP(), 1e-3);
        assertEquals(0, generator.get().getTargetQ(), 1e-3);

        // no load on node BBE2AA1 in the initial UCTE file
        Optional<Load> load = network.getBusBreakerView().getBus("BBE2AA1 ").getLoadStream().findAny();
        assertTrue(load.isPresent());
        assertEquals("BBE2AA1 _load", load.get().getId());
        assertEquals(0, load.get().getP0(), 1e-3);
        assertEquals(0, load.get().getQ0(), 1e-3);
        assertEquals(LoadType.FICTITIOUS, load.get().getLoadType());
    }
}
