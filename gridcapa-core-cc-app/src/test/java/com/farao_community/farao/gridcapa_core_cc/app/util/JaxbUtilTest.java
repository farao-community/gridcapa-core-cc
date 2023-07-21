/*
 * Copyright (c) 2023, RTE (http://www.rte-france.com)
 *  This Source Code Form is subject to the terms of the Mozilla Public
 *  License, v. 2.0. If a copy of the MPL was not distributed with this
 *  file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package com.farao_community.farao.gridcapa_core_cc.app.util;

import com.farao_community.farao.gridcapa_core_cc.api.exception.CoreCCInternalException;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.io.InputStream;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * @author Thomas Bouquet {@literal <thomas.bouquet at rte-france.com>}
 */
public class JaxbUtilTest {

    private InputStream inputStream = Mockito.mock(InputStream.class);;

    @Test
    void errorWhenUnmarshalFile() {
        CoreCCInternalException coreCCInternalException = assertThrows(CoreCCInternalException.class, () -> {
            JaxbUtil.unmarshalFile(int.class, Path.of("path"));
        });
        assertEquals("Error occurred when converting xml file path to object of type int", coreCCInternalException.getMessage());
    }

    @Test
    void errorWhenUnmarshalContent() {
        CoreCCInternalException coreCCInternalException = assertThrows(CoreCCInternalException.class, () -> {
            JaxbUtil.unmarshalContent(String.class, inputStream);
        });
        assertEquals("Error occurred when converting InputStream to object of type java.lang.String", coreCCInternalException.getMessage());
    }
}
