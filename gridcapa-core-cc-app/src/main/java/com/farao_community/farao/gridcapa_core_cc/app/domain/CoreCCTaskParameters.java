/*
 * Copyright (c) 2024, RTE (http://www.rte-france.com)
 *  This Source Code Form is subject to the terms of the Mozilla Public
 *  License, v. 2.0. If a copy of the MPL was not distributed with this
 *  file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package com.farao_community.farao.gridcapa_core_cc.app.domain;

import com.farao_community.farao.gridcapa.task_manager.api.TaskParameterDto;
import com.farao_community.farao.gridcapa_core_cc.api.exception.CoreCCInvalidDataException;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * @author Daniel Thirion {@literal <daniel.thirion at rte-france.com>}
 */
public class CoreCCTaskParameters {
    private static final Logger LOGGER = LoggerFactory.getLogger(CoreCCTaskParameters.class);
    private static final String USE_DC_CGM_INPUT = "USE_DC_CGM_INPUT";
    private static final String KEY_VALUE_FORMAT = "%n\t\"%s\": %s";
    private boolean useDcCgmInput;

    public CoreCCTaskParameters(final List<TaskParameterDto> parameters) {
        final List<String> errors = new ArrayList<>();
        for (final TaskParameterDto parameter : parameters) {
            if (USE_DC_CGM_INPUT.equals(parameter.getId())) {
                useDcCgmInput = validateIsBooleanAndGet(parameter, errors);
            } else {
                LOGGER.warn("Unknown parameter {} (value: {}) will be ignored", parameter.getId(), parameter.getValue());
            }
        }
        if (!errors.isEmpty()) {
            final String message = String.format("Validation of parameters failed. Failure reasons are: [\"%s\"].", String.join("\" ; \"", errors));
            throw new CoreCCInvalidDataException(message);
        }
    }

    private boolean validateIsBooleanAndGet(final TaskParameterDto parameter,
                                            final List<String> errors) {
        if (StringUtils.equals("BOOLEAN", parameter.getParameterType())) {
            final String value = Optional.ofNullable(parameter.getValue()).orElse(parameter.getDefaultValue());
            return Boolean.parseBoolean(value);
        } else {
            errors.add(String.format("Parameter %s was expected to be of type BOOLEAN, got %s", parameter.getId(), parameter.getParameterType()));
            return false;
        }
    }

    public boolean isUseDcCgmInput() {
        return useDcCgmInput;
    }

    public String toJsonString() {
        final List<String> appender = new ArrayList<>();
        appender.add(String.format(KEY_VALUE_FORMAT, USE_DC_CGM_INPUT, useDcCgmInput));
        return String.format("{%s%n}", String.join(", ", appender));
    }
}
