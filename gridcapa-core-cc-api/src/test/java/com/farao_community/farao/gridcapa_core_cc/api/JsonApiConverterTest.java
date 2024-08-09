/*
 * Copyright (c) 2023, RTE (http://www.rte-france.com)
 *  This Source Code Form is subject to the terms of the Mozilla Public
 *  License, v. 2.0. If a copy of the MPL was not distributed with this
 *  file, You can obtain one at http://mozilla.org/MPL/2.0/.
 *
 */

package com.farao_community.farao.gridcapa_core_cc.api;

import com.farao_community.farao.gridcapa_core_cc.api.exception.AbstractCoreCCException;
import com.farao_community.farao.gridcapa_core_cc.api.exception.CoreCCInternalException;
import com.farao_community.farao.gridcapa_core_cc.api.resource.CoreCCRequest;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.*;

/**
 * @author Ameni Walha {@literal <ameni.walha at rte-france.com>}
 */
class JsonApiConverterTest {
    @Test
    void checkCoreCCInputsJsonConversion() throws URISyntaxException, IOException {
        JsonApiConverter jsonApiConverter = new JsonApiConverter();
        String inputMessage = Files.readString(Paths.get(getClass().getResource("/validRequest.json").toURI()));
        CoreCCRequest coreCCRequest = jsonApiConverter.fromJsonMessage(inputMessage.getBytes(), CoreCCRequest.class);
        assertEquals("id", coreCCRequest.getId());
        assertEquals("cgm.txt", coreCCRequest.getCgm().getFilename());
        assertEquals("https://cgm/file/url", coreCCRequest.getCgm().getUrl());
        assertEquals("cbcora.txt", coreCCRequest.getCbcora().getFilename());
        assertEquals("https://cbcora/file/url", coreCCRequest.getCbcora().getUrl());
        assertEquals("glsk.txt", coreCCRequest.getGlsk().getFilename());
        assertEquals("https://glsk/file/url", coreCCRequest.getGlsk().getUrl());
    }

    @Test
    void checkInternalExceptionJsonConversion() throws URISyntaxException, IOException {
        JsonApiConverter jsonApiConverter = new JsonApiConverter();
        AbstractCoreCCException exception = new CoreCCInternalException("Something really bad happened");
        String expectedMessage = Files.readString(Paths.get(getClass().getResource("/coreCCInternalError.json").toURI()));
        assertEquals(expectedMessage, new String(jsonApiConverter.toJsonMessage(exception)));
    }

}
