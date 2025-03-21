/*
 * Copyright (c) 2023, RTE (http://www.rte-france.com)
 *  This Source Code Form is subject to the terms of the Mozilla Public
 *  License, v. 2.0. If a copy of the MPL was not distributed with this
 *  file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.farao_community.farao.gridcapa_core_cc.app.util;

import com.farao_community.farao.gridcapa_core_cc.api.exception.CoreCCInternalException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jakarta.xml.bind.*;
import javax.xml.transform.stream.StreamSource;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * @author Mohamed BenRejeb {@literal <mohamed.ben-rejeb at rte-france.com>}
 */
public final class JaxbUtil {

    private JaxbUtil() {
        throw new AssertionError("Utility class should not be constructed");
    }

    private static final Logger LOGGER = LoggerFactory.getLogger(JaxbUtil.class);

    public static <T> T unmarshalFile(Class<T> clazz, Path path) {
        try (InputStream fileContent = Files.newInputStream(path)) {
            JAXBContext jaxbContext = JAXBContext.newInstance(clazz);
            Unmarshaller jaxbUnmarshaller = jaxbContext.createUnmarshaller();
            JAXBElement<T> requestMessageTypeElement = jaxbUnmarshaller.unmarshal(new StreamSource(fileContent), clazz);
            return requestMessageTypeElement.getValue();
        } catch (JAXBException | IOException e) {

            String errorMessage = String.format("Error occurred when converting xml file %s to object of type %s", path, clazz.getName());
            LOGGER.error(errorMessage);
            throw new CoreCCInternalException(errorMessage, e);
        }
    }

    public static <T> T unmarshalContent(Class<T> clazz, InputStream inputStream) {
        try {
            JAXBContext jaxbContext = JAXBContext.newInstance(clazz);
            Unmarshaller jaxbUnmarshaller = jaxbContext.createUnmarshaller();
            JAXBElement<T> requestMessageTypeElement = jaxbUnmarshaller.unmarshal(new StreamSource(inputStream), clazz);
            return requestMessageTypeElement.getValue();
        } catch (JAXBException e) {
            String errorMessage = String.format("Error occurred when converting InputStream to object of type %s", clazz.getName());
            LOGGER.error(errorMessage);
            throw new CoreCCInternalException(errorMessage, e);
        }
    }
}
