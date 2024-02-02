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
import java.time.temporal.ChronoUnit;

/**
 * @author Pengbo Wang {@literal <pengbo.wang at rte-international.com>}
 * @author Mohamed BenRejeb {@literal <mohamed.ben-rejeb at rte-france.com>}
 * @author Godelaine de Montmorillon {@literal <godelaine.demontmorillon at rte-france.com>}
 */
public final class IntervalUtil {
    public static final ZoneId ZONE_ID = ZoneId.of("Europe/Brussels");

    private IntervalUtil() {
        throw new AssertionError("Utility class should not be constructed");
    }

    public static String getBrusselsFormattedBusinessDayFromUtc(OffsetDateTime offsetDateTime) {
        return DateTimeFormatter.ofPattern("yyyyMMdd").format(convertUtcToBrusselOffsetDateTime(offsetDateTime));
    }

    private static OffsetDateTime convertUtcToBrusselOffsetDateTime(OffsetDateTime offsetDateTime) {
        return offsetDateTime.withOffsetSameInstant(ZONE_ID.getRules().getOffset(offsetDateTime.toInstant()));
    }

    public static String handle25TimestampCase(String filename, String instant) {
        ZoneOffset previousOffset = OffsetDateTime.from(Instant.parse(instant).minus(1, ChronoUnit.HOURS).atZone(ZONE_ID)).getOffset();
        ZoneOffset currentOffset = OffsetDateTime.from(Instant.parse(instant).atZone(ZONE_ID)).getOffset();
        if (previousOffset == ZoneOffset.ofHours(2) && currentOffset == ZoneOffset.ofHours(1)) {
            return filename.replace("_0", "_B");
        } else {
            return filename;
        }
    }
}
