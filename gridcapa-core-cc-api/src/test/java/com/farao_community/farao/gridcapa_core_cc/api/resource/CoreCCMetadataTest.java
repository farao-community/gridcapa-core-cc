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
        CoreCCMetadata coreCCMetadata = new CoreCCMetadata("raoRequest.json", "2023-07-27T14:00:00Z", "2023-07-27T14:02:00Z", "2023-07-27T14:05:00Z", "2023-07-27T14:06:00Z", "interval", "correlationId", "status", "0", "This is an error.", 0);
        assertEquals("raoRequest.json", coreCCMetadata.getRaoRequestFileName());
        assertEquals("2023-07-27T14:00:00Z", coreCCMetadata.getRequestReceivedInstant());
        assertEquals("2023-07-27T14:05:00Z", coreCCMetadata.getComputationStart());
        assertEquals("2023-07-27T14:02:00Z", coreCCMetadata.getRaoRequestInstant());
        assertEquals("2023-07-27T14:06:00Z", coreCCMetadata.getComputationEnd());
        assertEquals("interval", coreCCMetadata.getTimeInterval());
        assertEquals("correlationId", coreCCMetadata.getCorrelationId());
        assertEquals("status", coreCCMetadata.getStatus());
        assertEquals("0", coreCCMetadata.getErrorCode());
        assertEquals("This is an error.", coreCCMetadata.getErrorMessage());
        assertEquals(0, coreCCMetadata.getVersion());
    }
}
