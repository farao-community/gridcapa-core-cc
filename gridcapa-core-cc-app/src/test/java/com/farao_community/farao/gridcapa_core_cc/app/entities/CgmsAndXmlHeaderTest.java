/*
 * Copyright (c) 2026, RTE (http://www.rte-france.com)
 *  This Source Code Form is subject to the terms of the Mozilla Public
 *  License, v. 2.0. If a copy of the MPL was not distributed with this
 *  file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package com.farao_community.farao.gridcapa_core_cc.app.entities;

import com.farao_community.farao.gridcapa_core_cc.api.exception.CoreCCInvalidDataException;
import com.farao_community.farao.gridcapa_core_cc.app.inputs.rao_response.Payload;
import com.farao_community.farao.gridcapa_core_cc.app.inputs.rao_response.ResponseMessage;
import com.unicorn.response.response_payload.File;
import com.unicorn.response.response_payload.Files;
import com.unicorn.response.response_payload.ResponseItem;
import com.unicorn.response.response_payload.ResponseItems;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;

class CgmsAndXmlHeaderTest {
    private CgmsAndXmlHeader cgmsAndXmlHeader;

    @BeforeEach
    void setUp() {
        final ResponseMessage responseMessage = new ResponseMessage();
        final Payload payload = new Payload();
        final ResponseItems responseItems = new ResponseItems();
        final ResponseItem responseItem1 = new ResponseItem();
        final ResponseItem responseItem2 = new ResponseItem();
        final Files files1 = new Files();
        final Files files2 = new Files();
        final File file1 = new File();
        final File file2 = new File();
        responseMessage.setPayload(payload);
        payload.setResponseItems(responseItems);
        responseItems.getResponseItem().add(responseItem1);
        responseItems.getResponseItem().add(responseItem2);
        responseItem1.setTimeInterval("2026-03-26T03:00Z/2026-03-26T04:00Z");
        responseItem1.setFiles(files1);
        files1.getFile().add(file1);
        file1.setCode("fileCode1");
        file1.setUrl("fileName://fileUrl1");
        responseItem2.setTimeInterval("2026-03-26T17:00Z/2026-03-26T18:00Z");
        responseItem2.setFiles(files2);
        files2.getFile().add(file2);
        file2.setCode("fileCode1");
        file2.setUrl("fileName://fileUrl2");

        cgmsAndXmlHeader = new CgmsAndXmlHeader(responseMessage, List.of(Path.of("fileUrl2")));
    }

    @Test
    void instantNotInIntervalTest() {
        final Instant instant = OffsetDateTime.now().toInstant();

        Assertions.assertThatExceptionOfType(CoreCCInvalidDataException.class)
            .isThrownBy(() -> cgmsAndXmlHeader.getNetworkPath(instant))
            .withMessage("cannot find instant " + instant + " in cgm xml header time intervals");
    }

    @Test
    void cgmNotAvailableInZipTest() {
        final Instant instant = OffsetDateTime.parse("2026-03-26T03:30:00Z").toInstant();

        Assertions.assertThatExceptionOfType(CoreCCInvalidDataException.class)
            .isThrownBy(() -> cgmsAndXmlHeader.getNetworkPath(instant))
            .withMessage("cannot find cgm for instant " + instant + " in zip folder");
    }

    @Test
    void test() {
        final Instant instant = OffsetDateTime.parse("2026-03-26T17:30:00Z").toInstant();

        final Path networkPath = cgmsAndXmlHeader.getNetworkPath(instant);

        Assertions.assertThat(networkPath).isNotNull();
    }
}
