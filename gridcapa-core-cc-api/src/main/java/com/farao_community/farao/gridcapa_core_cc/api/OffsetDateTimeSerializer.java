/*
 * Copyright (c) 2023, RTE (http://www.rte-france.com)
 *  This Source Code Form is subject to the terms of the Mozilla Public
 *  License, v. 2.0. If a copy of the MPL was not distributed with this
 *  file, You can obtain one at http://mozilla.org/MPL/2.0/.
 *
 */

package com.farao_community.farao.gridcapa_core_cc.api;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;

import java.io.IOException;
import java.time.OffsetDateTime;

/**
 * @author Ameni Walha {@literal <ameni.walha at rte-france.com>}
 */
public class OffsetDateTimeSerializer extends JsonSerializer<OffsetDateTime> {
    @Override
    public void serialize(final OffsetDateTime toSerialize,
                          final JsonGenerator generator,
                          final SerializerProvider provider) throws IOException {
        generator.writeString(toSerialize.toString());
    }
}
