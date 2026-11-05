/*
 * Copyright (c) 2026, RTE (http://www.rte-france.com)
 *  This Source Code Form is subject to the terms of the Mozilla Public
 *  License, v. 2.0. If a copy of the MPL was not distributed with this
 *  file, You can obtain one at http://mozilla.org/MPL/2.0/.
 *
 */
package com.farao_community.farao.gridcapa_core_cc.api.resource;

/**
 * @author Vincent Bochet {@literal <vincent.bochet at rte-france.com>}
 */
public enum HourlyRaoResultErrorCode {
    RUNNING("0"),
    BD_PREPROCESSING_FAILURE("1"),
    TS_PREPROCESSING_FAILURE("2"),
    RAO_FAILURE("3"),
    UNKNOWN_FAILURE("99");

    private final String code;

    HourlyRaoResultErrorCode(String code) {
        this.code = code;
    }

    public String getCode() {
        return code;
    }
}
