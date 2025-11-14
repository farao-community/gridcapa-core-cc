/*
 * Copyright (c) 2023, RTE (http://www.rte-france.com)
 *  This Source Code Form is subject to the terms of the Mozilla Public
 *  License, v. 2.0. If a copy of the MPL was not distributed with this
 *  file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.farao_community.farao.gridcapa_core_cc.api.util;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

import static java.time.temporal.ChronoUnit.HOURS;

/**
 * @author Pengbo Wang {@literal <pengbo.wang at rte-international.com>}
 * @author Mohamed BenRejeb {@literal <mohamed.ben-rejeb at rte-france.com>}
 * @author Godelaine de Montmorillon {@literal <godelaine.demontmorillon at rte-france.com>}
 */
public final class IntervalUtil {
    public static final ZoneId ZONE_ID = ZoneId.of("Europe/Brussels");
    private static final ZoneOffset OFFSET_BEFORE_WINTER_DST = ZoneOffset.ofHours(2);
    private static final ZoneOffset OFFSET_AFTER_WINTER_DST = ZoneOffset.ofHours(1);

    private IntervalUtil() {
        throw new AssertionError("Utility class should not be constructed");
    }

    public static String getBrusselsFormattedBusinessDayFromUtc(final OffsetDateTime offsetDateTime) {
        return DateTimeFormatter.ofPattern("yyyyMMdd").format(utcToBrussels(offsetDateTime));
    }

    private static OffsetDateTime utcToBrussels(final OffsetDateTime offsetDateTime) {
        return offsetDateTime.withOffsetSameInstant(ZONE_ID.getRules().getOffset(offsetDateTime.toInstant()));
    }

    public static String handleWinterDst(final String filename, final String instantInDstHour) {
        final Instant instant = Instant.parse(instantInDstHour);
        final ZoneOffset previousOffset = OffsetDateTime.from(instant.minus(1, HOURS).atZone(ZONE_ID)).getOffset();
        final ZoneOffset currentOffset = OffsetDateTime.from(instant.atZone(ZONE_ID)).getOffset();
        // change filename if interval is in winter DST
        if (previousOffset.equals(OFFSET_BEFORE_WINTER_DST) && currentOffset.equals(OFFSET_AFTER_WINTER_DST)) {
            return filename.replace("_0", "_B");
        } else {
            return filename;
        }
    }
}
