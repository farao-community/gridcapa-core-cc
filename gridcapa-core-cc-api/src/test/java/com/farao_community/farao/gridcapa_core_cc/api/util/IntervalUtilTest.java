/*
 * Copyright (c) 2026, RTE (http://www.rte-france.com)
 *  This Source Code Form is subject to the terms of the Mozilla Public
 *  License, v. 2.0. If a copy of the MPL was not distributed with this
 *  file, You can obtain one at http://mozilla.org/MPL/2.0/.
 *
 */
package com.farao_community.farao.gridcapa_core_cc.api.util;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;

/**
 * @author Vincent Bochet {@literal <vincent.bochet at rte-france.com>}
 */
class IntervalUtilTest {
    @ParameterizedTest
    @CsvSource({
        "2026-01-01T22:34:56Z,20260101",
        "2026-01-01T23:34:56Z,20260102",
        "2026-07-01T21:34:56Z,20260701",
        "2026-07-01T22:34:56Z,20260702",
    })
    void getBrusselsFormattedBusinessDayFromUtcTest(final String utcDateString, final String brusselsDateString) {
        final OffsetDateTime utcDateTime = OffsetDateTime.parse(utcDateString);
        final String convertedDate = IntervalUtil.getBrusselsFormattedBusinessDayFromUtc(utcDateTime);
        Assertions.assertThat(convertedDate).isEqualTo(brusselsDateString);
    }

    @ParameterizedTest
    @CsvSource({
        "2026-10-25T00:30:00Z,prefix_20261025_0230_suffix.txt,prefix_20261025_0230_suffix.txt",
        "2026-10-25T01:30:00Z,prefix_20261025_0230_suffix.txt,prefix_20261025_B230_suffix.txt",
        "2026-10-25T02:30:00Z,prefix_20261025_0330_suffix.txt,prefix_20261025_0330_suffix.txt",
    })
    void handle25TimestampCaseTest(final String utcDateString, final String expectedDefaultFilename, final String expectedFixedFilename) {
        final OffsetDateTime offsetDateTime = OffsetDateTime.parse(utcDateString);
        final String filename = "prefix_" + offsetDateTime.format(DateTimeFormatter.ofPattern("yyyyMMdd'_'HHmm").withZone(IntervalUtil.ZONE_ID)) + "_suffix.txt";
        final String fixedFilename = IntervalUtil.handle25TimestampCase(filename, offsetDateTime.toInstant().toString());

        Assertions.assertThat(filename).isEqualTo(expectedDefaultFilename);
        Assertions.assertThat(fixedFilename).isEqualTo(expectedFixedFilename);
    }
}
