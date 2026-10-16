/*
 * Copyright (c) 2026, RTE (http://www.rte-france.com)
 *  This Source Code Form is subject to the terms of the Mozilla Public
 *  License, v. 2.0. If a copy of the MPL was not distributed with this
 *  file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package com.farao_community.farao.gridcapa_core_cc.app.util;

import com.powsybl.iidm.network.Identifiable;
import com.powsybl.iidm.network.LoadType;
import com.powsybl.iidm.network.Network;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

import java.nio.file.Paths;

/**
 * @author Vincent Bochet {@literal <vincent.bochet at rte-france.com>}
 */
class NetworkUtilTest {
    @Test
    void applyWorkaroundTest() {
        final Network network = Network.read(Paths.get(getClass().getResource("/util/network_with_virtualhubs.xiidm").getPath()));

        Assertions.assertThat(network.getLoadStream().anyMatch(load -> load.getId().contains("_virtualLoad"))).isTrue();
        Assertions.assertThat(network.getLoadStream().anyMatch(load -> load.getLoadType() == LoadType.FICTITIOUS)).isTrue();
//        // Those generators are already absent right after the newtork import, maybe the associated workaround is not needed anymore?
//        // TODO Check if virtualhubs still create virtual generators in imported network and remove workaround if not
//        Assertions.assertThat(network.getGenerator("XLI_OB1A")).isNotNull();
//        Assertions.assertThat(network.getGenerator("XLI_OB1B")).isNotNull();

        NetworkUtil.applyWorkaround(network);

        Assertions.assertThat(network.getLoadStream().noneMatch(load -> load.getId().contains("_virtualLoad"))).isTrue();
        Assertions.assertThat(network.getLoadStream().noneMatch(load -> load.getLoadType() == LoadType.FICTITIOUS)).isTrue();
        Assertions.assertThat(network.getGenerator("XLI_OB1A")).isNull();
        Assertions.assertThat(network.getGenerator("XLI_OB1B")).isNull();
    }
}
