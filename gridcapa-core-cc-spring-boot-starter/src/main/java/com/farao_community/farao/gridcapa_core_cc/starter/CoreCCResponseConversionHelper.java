/*
 * Copyright (c) 2023, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.farao_community.farao.gridcapa_core_cc.starter;

import com.farao_community.farao.gridcapa_core_cc.api.JsonApiConverter;
import com.farao_community.farao.gridcapa_core_cc.api.exception.CoreCCInvalidDataException;
import com.farao_community.farao.gridcapa_core_cc.api.resource.CoreCCResponse;
import com.github.jasminb.jsonapi.exceptions.ResourceParseException;
import org.springframework.amqp.core.Message;

/**
 * @author Ameni Walha {@literal <ameni.walha at rte-france.com>}
 */
public final class CoreCCResponseConversionHelper {

    private CoreCCResponseConversionHelper() {
        throw new AssertionError("Utility class should not be constructed");
    }

    public static CoreCCResponse convertCoreCCResponse(Message message, JsonApiConverter jsonConverter) {
        try {
            return jsonConverter.fromJsonMessage(message.getBody(), CoreCCResponse.class);
        } catch (ResourceParseException resourceParseException) {
            // exception details from core-cc-runner app is wrapped into a ResourceParseException on json Api Error format.
            String originCause = resourceParseException.getErrors().getErrors().get(0).getDetail();
            throw new CoreCCInvalidDataException(originCause);
        } catch (Exception unknownException) {
            throw new CoreCCInvalidDataException("Unsupported exception thrown by core-cc-runner app", unknownException);
        }
    }
}
