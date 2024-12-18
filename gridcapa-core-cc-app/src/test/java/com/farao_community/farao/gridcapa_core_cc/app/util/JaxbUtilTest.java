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

import jakarta.xml.bind.annotation.*;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * @author Thomas Bouquet {@literal <thomas.bouquet at rte-france.com>}
 */
class JaxbUtilTest {

    private final InputStream inputStream = Mockito.mock(InputStream.class);

    @XmlAccessorType(XmlAccessType.FIELD)
    @XmlType(name = "", propOrder = {"zipCode", "name"})
    @XmlRootElement(name = "BasicCity")
    private static class BasicCity {
        @XmlElement(name = "zipCode", required = true)
        private int zipCode = 0;
        @XmlElement(name = "name", required = true)
        private String name = "";

        public BasicCity() { }

        public BasicCity(int zipCode, String name) {
            this.zipCode = zipCode;
            this.name = name;
        }

        public int getZipCode() {
            return zipCode;
        }

        public String getName() {
            return name;
        }
    }

    @Test
    void unmarshalFile() throws IOException {
        String xmlFile = "/util/fileToUnmarshal.xml";
        Path pathToXmlFile = Paths.get(getClass().getResource(xmlFile).getPath());
        BasicCity unmarshaledFile = JaxbUtil.unmarshalFile(BasicCity.class, pathToXmlFile);
        assertEquals(75000, unmarshaledFile.getZipCode());
        assertEquals("Paris", unmarshaledFile.getName());
    }

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
