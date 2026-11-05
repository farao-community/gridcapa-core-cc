/*
 * Copyright (c) 2023, RTE (http://www.rte-france.com)
 *  This Source Code Form is subject to the terms of the Mozilla Public
 *  License, v. 2.0. If a copy of the MPL was not distributed with this
 *  file, You can obtain one at http://mozilla.org/MPL/2.0/.
 *
 */

package com.farao_community.farao.gridcapa_core_cc.api.resource;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * @author Thomas Bouquet {@literal <thomas.bouquet at rte-france.com>}
 */
class CoreCCMetadataTest {
    @Test
    void checkCoreCCMetadata() {
        CoreCCMetadata coreCCMetadata = new CoreCCMetadata.Builder()
            .withRaoRequestFileName("raoRequest.json")
            .withRequestReceivedInstant("2023-07-27T14:00:00Z")
            .withRaoRequestInstant("2023-07-27T14:02:00Z")
            .withTimeInterval("interval")
            .withCorrelationId("correlationId")
            .withVersion(33)
            .withSemComputationStartInstant("2023-07-27T14:05:00Z")
            .withSemComputationEndInstant("2023-07-27T14:06:00Z")
            .withSemComputationStatus("FAILURE")
            .withSemComputationErrorCode("3")
            .withSemComputationErrorMessage("This is an error.")
            .withContinentalComputationStartInstant("2023-07-27T14:06:00Z")
            .withContinentalComputationEndInstant("2023-07-27T14:30:00Z")
            .withContinentalComputationStatus("SUCCESS")
            .withContinentalComputationErrorCode("0")
            .withContinentalComputationErrorMessage("No error")
            .build();

        Assertions.assertThat(coreCCMetadata.getRaoRequestFileName()).isEqualTo("raoRequest.json");
        Assertions.assertThat(coreCCMetadata.getRequestReceivedInstant()).isEqualTo("2023-07-27T14:00:00Z");
        Assertions.assertThat(coreCCMetadata.getRaoRequestInstant()).isEqualTo("2023-07-27T14:02:00Z");
        Assertions.assertThat(coreCCMetadata.getTimeInterval()).isEqualTo("interval");
        Assertions.assertThat(coreCCMetadata.getCorrelationId()).isEqualTo("correlationId");
        Assertions.assertThat(coreCCMetadata.getSemComputationStart()).isEqualTo("2023-07-27T14:05:00Z");
        Assertions.assertThat(coreCCMetadata.getSemComputationEnd()).isEqualTo("2023-07-27T14:06:00Z");
        Assertions.assertThat(coreCCMetadata.getSemComputationStatus()).isEqualTo("FAILURE");
        Assertions.assertThat(coreCCMetadata.getSemComputationErrorCode()).isEqualTo("3");
        Assertions.assertThat(coreCCMetadata.getSemComputationErrorMessage()).isEqualTo("This is an error.");
        Assertions.assertThat(coreCCMetadata.getContinentalComputationStart()).isEqualTo("2023-07-27T14:06:00Z");
        Assertions.assertThat(coreCCMetadata.getContinentalComputationEnd()).isEqualTo("2023-07-27T14:30:00Z");
        Assertions.assertThat(coreCCMetadata.getContinentalComputationStatus()).isEqualTo("SUCCESS");
        Assertions.assertThat(coreCCMetadata.getContinentalComputationErrorCode()).isEqualTo("0");
        Assertions.assertThat(coreCCMetadata.getContinentalComputationErrorMessage()).isEqualTo("No error");
        Assertions.assertThat(coreCCMetadata.getVersion()).isEqualTo(33);
    }

    @Test
    void checkDefaultCoreCCMetadata() {
        CoreCCMetadata coreCCMetadata = new CoreCCMetadata.Builder().build();

        Assertions.assertThat(coreCCMetadata.getRaoRequestFileName()).isNull();
        Assertions.assertThat(coreCCMetadata.getRequestReceivedInstant()).isNull();
        Assertions.assertThat(coreCCMetadata.getRaoRequestInstant()).isNull();
        Assertions.assertThat(coreCCMetadata.getTimeInterval()).isNull();
        Assertions.assertThat(coreCCMetadata.getCorrelationId()).isNull();
        Assertions.assertThat(coreCCMetadata.getSemComputationStart()).isNull();
        Assertions.assertThat(coreCCMetadata.getSemComputationEnd()).isNull();
        Assertions.assertThat(coreCCMetadata.getSemComputationStatus()).isNull();
        Assertions.assertThat(coreCCMetadata.getSemComputationErrorCode()).isNull();
        Assertions.assertThat(coreCCMetadata.getSemComputationErrorMessage()).isNull();
        Assertions.assertThat(coreCCMetadata.getContinentalComputationStart()).isNull();
        Assertions.assertThat(coreCCMetadata.getContinentalComputationEnd()).isNull();
        Assertions.assertThat(coreCCMetadata.getContinentalComputationStatus()).isNull();
        Assertions.assertThat(coreCCMetadata.getContinentalComputationErrorCode()).isNull();
        Assertions.assertThat(coreCCMetadata.getContinentalComputationErrorMessage()).isNull();
        Assertions.assertThat(coreCCMetadata.getVersion()).isZero();
    }
}
