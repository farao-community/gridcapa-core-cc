/*
 * Copyright (c) 2021, RTE (http://www.rte-france.com)
 *  This Source Code Form is subject to the terms of the Mozilla Public
 *  License, v. 2.0. If a copy of the MPL was not distributed with this
 *  file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.farao_community.farao.gridcapa_core_cc.app.configuration;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * @author Mohamed BenRejeb {@literal <mohamed.ben-rejeb at rte-france.com>}
 * @author Thomas Bouquet {@literal <thomas.bouquet at rte-france.com>}
 */
@SpringBootTest
class UrlWhitelistConfigurationTest {

    @Autowired
    public UrlWhitelistConfiguration urlWhitelistConfiguration;

    @Test
    void checkUrlWhiteListIsRetrievedCorrectly() {
        // TODO: understand why not 1 and "http://localhost:9000"
        assertEquals(2, urlWhitelistConfiguration.getWhitelist().size());
        assertEquals("file:/", urlWhitelistConfiguration.getWhitelist().get(0));
        assertEquals("http://minio:9000/", urlWhitelistConfiguration.getWhitelist().get(1));
    }
}
