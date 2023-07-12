/*
 * Copyright (c) 2021, RTE (http://www.rte-france.com)
 *  This Source Code Form is subject to the terms of the Mozilla Public
 *  License, v. 2.0. If a copy of the MPL was not distributed with this
 *  file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.farao_community.farao.gridcapa_core_cc.app.postprocessing;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * @author Mohamed BenRejeb {@literal <mohamed.ben-rejeb at rte-france.com>}
 * @author Thomas Bouquet {@literal <thomas.bouquet at rte-france.com>}
 */
public class OutputFileNameUtilTest {

    @Test
    void generateCgmFileNameTest() {
        assertEquals("20200330_0130_2D1_UX1.uct", OutputFileNameUtil.generateUctFileName("2020-03-29T23:00:00Z", 1));
    }

    // TODO: generateOutputNamesFileNameTest
    // TODO: test25TimestampDay
    // TODO: test23TimestampDay
}
