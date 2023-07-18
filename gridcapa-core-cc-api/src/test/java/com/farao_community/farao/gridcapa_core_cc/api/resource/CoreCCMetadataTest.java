/*
 * Copyright (c) 2023, RTE (http://www.rte-france.com)
 *  This Source Code Form is subject to the terms of the Mozilla Public
 *  License, v. 2.0. If a copy of the MPL was not distributed with this
 *  file, You can obtain one at http://mozilla.org/MPL/2.0/.
 *
 */

package com.farao_community.farao.gridcapa_core_cc.api.resource;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * @author Thomas Bouquet {@literal <thomas.bouquet at rte-france.com>}
 */
public class CoreCCMetadataTest {
    @Test
    void checkCoreCCMetadata() {
        CoreCCMetadata coreCCMetadata = new CoreCCMetadata("raoRequestFileName", "requestReceivedInstant", "raoRequestInstant", "computationStart", "computationEnd", "timeInterval", "status", "errorCode", "errorMessage", 0);
        assertEquals("raoRequestFileName", coreCCMetadata.getRaoRequestFileName());
        assertEquals("requestReceivedInstant", coreCCMetadata.getRequestReceivedInstant());
        assertEquals("computationStart", coreCCMetadata.getComputationStart());
        assertEquals("raoRequestInstant", coreCCMetadata.getRaoRequestInstant());
        assertEquals("computationEnd", coreCCMetadata.getComputationEnd());
        assertEquals("timeInterval", coreCCMetadata.getTimeInterval());
        assertEquals("status", coreCCMetadata.getStatus());
        assertEquals("errorCode", coreCCMetadata.getErrorCode());
        assertEquals("errorMessage", coreCCMetadata.getErrorMessage());
        assertEquals(0, coreCCMetadata.getVersion());
    }
}
