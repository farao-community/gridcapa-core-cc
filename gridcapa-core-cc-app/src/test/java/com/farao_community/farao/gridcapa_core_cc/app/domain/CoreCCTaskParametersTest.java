/*
 * Copyright (c) 2024, RTE (http://www.rte-france.com)
 *  This Source Code Form is subject to the terms of the Mozilla Public
 *  License, v. 2.0. If a copy of the MPL was not distributed with this
 *  file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package com.farao_community.farao.gridcapa_core_cc.app.domain;

import com.farao_community.farao.gridcapa.task_manager.api.TaskParameterDto;
import com.farao_community.farao.gridcapa_core_cc.api.exception.CoreCCInvalidDataException;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
/**
 * @author Daniel Thirion {@literal <daniel.thirion at rte-france.com>}
 */
class CoreCCTaskParametersTest {

    private static final String USE_DC_CGM_INPUT = "USE_DC_CGM_INPUT";

    @Test
    void testValidBooleanParameter() {
        final TaskParameterDto parameter = Mockito.mock(TaskParameterDto.class);
        Mockito.when(parameter.getId()).thenReturn(USE_DC_CGM_INPUT);
        Mockito.when(parameter.getParameterType()).thenReturn("BOOLEAN");
        Mockito.when(parameter.getValue()).thenReturn("true");

        final CoreCCTaskParameters taskParameters = new CoreCCTaskParameters(Collections.singletonList(parameter));
        assertTrue(taskParameters.isUseDcCgmInput());
    }

    @Test
    void testInvalidBooleanParameterType() {
        final TaskParameterDto parameter = Mockito.mock(TaskParameterDto.class);
        Mockito.when(parameter.getId()).thenReturn(USE_DC_CGM_INPUT);
        Mockito.when(parameter.getParameterType()).thenReturn("STRING");
        Mockito.when(parameter.getValue()).thenReturn("true");

        final List<TaskParameterDto> parameters = Collections.singletonList(parameter);
        final CoreCCInvalidDataException exception = assertThrows(CoreCCInvalidDataException.class, () -> new CoreCCTaskParameters(parameters));
        assertTrue(exception.getMessage().contains("Parameter USE_DC_CGM_INPUT was expected to be of type BOOLEAN, got STRING"));
    }

    @Test
    void testUnknownParameter() {
        final TaskParameterDto parameter = Mockito.mock(TaskParameterDto.class);
        Mockito.when(parameter.getId()).thenReturn("UNKNOWN_PARAMETER");
        Mockito.when(parameter.getParameterType()).thenReturn("BOOLEAN");
        Mockito.when(parameter.getValue()).thenReturn("true");

        final CoreCCTaskParameters taskParameters = new CoreCCTaskParameters(Collections.singletonList(parameter));
        assertFalse(taskParameters.isUseDcCgmInput());
    }

    @Test
    void testToJsonString() {
        final TaskParameterDto parameter = Mockito.mock(TaskParameterDto.class);
        Mockito.when(parameter.getId()).thenReturn(USE_DC_CGM_INPUT);
        Mockito.when(parameter.getParameterType()).thenReturn("BOOLEAN");
        Mockito.when(parameter.getValue()).thenReturn("true");

        final CoreCCTaskParameters taskParameters = new CoreCCTaskParameters(Collections.singletonList(parameter));
        final String jsonString = taskParameters.toJsonString();
        assertEquals("{\n\t\"USE_DC_CGM_INPUT\": true\n}", jsonString);
    }
}
