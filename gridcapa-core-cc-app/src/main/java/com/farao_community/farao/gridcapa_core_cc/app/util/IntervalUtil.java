/*
 * Copyright (c) 2021, RTE (http://www.rte-france.com)
 */
package com.farao_community.farao.gridcapa_core_cc.app.util;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;

/**
 * @author Pengbo Wang {@literal <pengbo.wang at rte-international.com>}
 * @author Mohamed BenRejeb {@literal <mohamed.ben-rejeb at rte-france.com>}
 */
public final class IntervalUtil {

    private IntervalUtil() {
        throw new AssertionError("Utility class should not be constructed");
    }

    public static String getFormattedBusinessDay(OffsetDateTime offsetDateTime) {
        return DateTimeFormatter.ofPattern("yyyyMMdd").format(offsetDateTime);
    }
}
