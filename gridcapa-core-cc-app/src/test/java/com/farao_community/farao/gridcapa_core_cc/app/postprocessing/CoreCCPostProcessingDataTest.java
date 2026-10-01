/*
 * Copyright (c) 2026, RTE (http://www.rte-france.com)
 *  This Source Code Form is subject to the terms of the Mozilla Public
 *  License, v. 2.0. If a copy of the MPL was not distributed with this
 *  file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.farao_community.farao.gridcapa_core_cc.app.postprocessing;

import com.farao_community.farao.gridcapa_core_cc.api.resource.CoreCCRequest;
import com.farao_community.farao.gridcapa_core_cc.api.resource.HourlyRaoRequest;
import com.farao_community.farao.gridcapa_core_cc.api.resource.InternalCoreCCRequest;
import com.powsybl.iidm.network.Network;
import com.powsybl.openrao.data.crac.api.Crac;
import com.powsybl.openrao.data.crac.io.fbconstraint.FbConstraintCreationContext;
import com.powsybl.openrao.data.raoresult.api.RaoResult;
import com.powsybl.openrao.raoapi.parameters.RaoParameters;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.time.OffsetDateTime;
import java.util.List;

class CoreCCPostProcessingDataTest {
    @Test
    void raoRequestTest() {
        final CoreCCPostProcessingData data = new CoreCCPostProcessingData();
        Assertions.assertThat(data.getRequest()).isNull();
        Assertions.assertThat(data.getRaoRequestInstant()).isNull();
        Assertions.assertThat(data.getResultsDestination()).isNull();

        final CoreCCRequest coreCCRequest = new CoreCCRequest("id", "runId", OffsetDateTime.now(), null, null, null, null, null, null, null, List.of());
        final InternalCoreCCRequest internalCoreCCRequest = new InternalCoreCCRequest(coreCCRequest);
        data.setRequest(internalCoreCCRequest);

        Assertions.assertThat(data.getRequest()).isEqualTo(internalCoreCCRequest);
        Assertions.assertThat(data.getRaoRequestInstant()).isNull();
        Assertions.assertThat(data.getResultsDestination()).isNull();

        internalCoreCCRequest.setDestinationPath("destinationPath");
        Assertions.assertThat(data.getResultsDestination()).isEqualTo("destinationPath");

        final HourlyRaoRequest semHourlyRaoRequest = Mockito.mock(HourlyRaoRequest.class);
        Mockito.when(semHourlyRaoRequest.getRaoRequestInstant()).thenReturn("semInstant");
        internalCoreCCRequest.setSemHourlyRaoRequest(semHourlyRaoRequest);
        Assertions.assertThat(data.getRaoRequestInstant()).isEqualTo("semInstant");

        final HourlyRaoRequest continentalHourlyRaoRequest = Mockito.mock(HourlyRaoRequest.class);
        Mockito.when(continentalHourlyRaoRequest.getRaoRequestInstant()).thenReturn("continentalInstant");
        internalCoreCCRequest.setSemHourlyRaoRequest(continentalHourlyRaoRequest);
        Assertions.assertThat(data.getRaoRequestInstant()).isEqualTo("continentalInstant");
    }

    @Test
    void cracTest() {
        final CoreCCPostProcessingData data = new CoreCCPostProcessingData();
        Assertions.assertThat(data.getCracCreationContext()).isNull();
        Assertions.assertThat(data.getCrac()).isNull();
        Assertions.assertThat(data.getContinentalCracCreationContext()).isNull();
        Assertions.assertThat(data.getContinentalCrac()).isNull();
        Assertions.assertThat(data.getSemCracCreationContext()).isNull();
        Assertions.assertThat(data.getSemCrac()).isNull();

        final FbConstraintCreationContext fullCbcora = Mockito.mock(FbConstraintCreationContext.class);
        final Crac fullCrac = Mockito.mock(Crac.class);
        Mockito.when(fullCbcora.getCrac()).thenReturn(fullCrac);
        final FbConstraintCreationContext continentalCbcora = Mockito.mock(FbConstraintCreationContext.class);
        final Crac continentalCrac = Mockito.mock(Crac.class);
        Mockito.when(continentalCbcora.getCrac()).thenReturn(continentalCrac);
        final FbConstraintCreationContext semCbcora = Mockito.mock(FbConstraintCreationContext.class);
        final Crac semCrac = Mockito.mock(Crac.class);
        Mockito.when(semCbcora.getCrac()).thenReturn(semCrac);

        data.setCracCreationContext(fullCbcora);
        data.setContinentalCracCreationContext(continentalCbcora);
        data.setSemCracCreationContext(semCbcora);
        Assertions.assertThat(data.getCracCreationContext()).isEqualTo(fullCbcora);
        Assertions.assertThat(data.getCrac()).isEqualTo(fullCrac);
        Assertions.assertThat(data.getContinentalCracCreationContext()).isEqualTo(continentalCbcora);
        Assertions.assertThat(data.getContinentalCrac()).isEqualTo(continentalCrac);
        Assertions.assertThat(data.getSemCracCreationContext()).isEqualTo(semCbcora);
        Assertions.assertThat(data.getSemCrac()).isEqualTo(semCrac);
    }

    @Test
    void otherDataTest() {
        final CoreCCPostProcessingData data = new CoreCCPostProcessingData();
        Assertions.assertThat(data.getAcNetwork()).isNull();
        Assertions.assertThat(data.getDcNetwork()).isNull();
        Assertions.assertThat(data.getInitialAcNetworkVariantId()).isNull();
        Assertions.assertThat(data.getRaoParameters()).isNull();
        Assertions.assertThat(data.getRaoResult()).isNull();

        final Network acNetwork = Mockito.mock(Network.class);
        final Network dcNetwork = Mockito.mock(Network.class);
        data.setAcNetwork(acNetwork);
        data.setDcNetwork(dcNetwork);
        Assertions.assertThat(data.getAcNetwork()).isEqualTo(acNetwork);
        Assertions.assertThat(data.getAcNetwork()).isNotEqualTo(dcNetwork);
        Assertions.assertThat(data.getDcNetwork()).isEqualTo(dcNetwork);
        Assertions.assertThat(data.getDcNetwork()).isNotEqualTo(acNetwork);

        final String acNetworkVariantId = "acNetworkVariantId";
        data.setInitialAcNetworkVariantId(acNetworkVariantId);
        Assertions.assertThat(data.getInitialAcNetworkVariantId()).isEqualTo(acNetworkVariantId);

        final RaoParameters raoParameters = Mockito.mock(RaoParameters.class);
        data.setRaoParameters(raoParameters);
        Assertions.assertThat(data.getRaoParameters()).isEqualTo(raoParameters);

        final RaoResult raoResult = Mockito.mock(RaoResult.class);
        data.setRaoResult(raoResult);
        Assertions.assertThat(data.getRaoResult()).isEqualTo(raoResult);
    }
}
