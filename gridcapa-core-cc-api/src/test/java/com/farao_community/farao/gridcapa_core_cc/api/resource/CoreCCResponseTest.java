/*
 * Copyright (c) 2023, RTE (http://www.rte-france.com)
 *  This Source Code Form is subject to the terms of the Mozilla Public
 *  License, v. 2.0. If a copy of the MPL was not distributed with this
 *  file, You can obtain one at http://mozilla.org/MPL/2.0/.
 *
 */

package com.farao_community.farao.gridcapa_core_cc.api.resource;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

/**
 * @author Ameni Walha {@literal <ameni.walha at rte-france.com>}
 */
class CoreCCResponseTest {

    @Test
    void checkCoreCCResponse() {
        Instant computationStartInstant = Instant.parse("2021-01-01T00:30:00Z");
        Instant computationEndInstant = Instant.parse("2021-01-01T00:35:00Z");
        CoreCCResponse coreCCResponse = new CoreCCResponse("id");
        assertNotNull(coreCCResponse);
        assertEquals("id", coreCCResponse.getId());
    }
}
