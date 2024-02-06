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
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * @author Thomas Bouquet {@literal <thomas.bouquet at rte-france.com>}
 */
class IntervalUtilTest {

    @Test
    void getFormattedBusinessDay() {
        final ZoneOffset utcOffset = ZoneId.of("UTC").getRules().getOffset(LocalDateTime.now());
        OffsetDateTime summer0 = OffsetDateTime.of(2024, 7, 21, 21, 30, 0, 0, utcOffset);
        OffsetDateTime summer1 = OffsetDateTime.of(2024, 7, 21, 22, 30, 0, 0, utcOffset);
        OffsetDateTime summer2 = OffsetDateTime.of(2024, 7, 21, 23, 30, 0, 0, utcOffset);
        OffsetDateTime winter1 = OffsetDateTime.of(2024, 1, 21, 22, 30, 0, 0, utcOffset);
        OffsetDateTime winter2 = OffsetDateTime.of(2024, 1, 21, 23, 30, 0, 0, utcOffset);
        assertEquals("20240721", IntervalUtil.getBrusselsFormattedBusinessDayFromUtc(summer0));
        assertEquals("20240722", IntervalUtil.getBrusselsFormattedBusinessDayFromUtc(summer1));
        assertEquals("20240722", IntervalUtil.getBrusselsFormattedBusinessDayFromUtc(summer2));
        assertEquals("20240121", IntervalUtil.getBrusselsFormattedBusinessDayFromUtc(winter1));
        assertEquals("20240122", IntervalUtil.getBrusselsFormattedBusinessDayFromUtc(winter2));
    }
}
