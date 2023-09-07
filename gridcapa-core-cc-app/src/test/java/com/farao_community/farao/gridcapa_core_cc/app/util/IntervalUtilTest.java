/*
 * Copyright (c) 2023, RTE (http://www.rte-france.com)
 *  This Source Code Form is subject to the terms of the Mozilla Public
 *  License, v. 2.0. If a copy of the MPL was not distributed with this
 *  file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package com.farao_community.farao.gridcapa_core_cc.app.util;

import com.farao_community.farao.gridcapa_core_cc.api.util.IntervalUtil;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * @author Thomas Bouquet {@literal <thomas.bouquet at rte-france.com>}
 */
class IntervalUtilTest {

    @Test
    void getFormattedBusinessDay() {
        OffsetDateTime offsetDateTime = OffsetDateTime.of(2023, 7, 21, 16, 28, 20, 0, ZoneId.of("Europe/Brussels").getRules().getOffset(LocalDateTime.now()));
        assertEquals("20230721", IntervalUtil.getFormattedBusinessDay(offsetDateTime));
    }
}
