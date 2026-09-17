/*
 * Copyright (c) 2023, RTE (http://www.rte-france.com)
 *  This Source Code Form is subject to the terms of the Mozilla Public
 *  License, v. 2.0. If a copy of the MPL was not distributed with this
 *  file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package com.farao_community.farao.gridcapa_core_cc.app.services;

import com.farao_community.farao.gridcapa_core_cc.api.resource.CoreCCFileResource;
import com.farao_community.farao.minio_adapter.starter.MinioAdapter;
import com.powsybl.iidm.network.Network;
import com.powsybl.openrao.data.crac.api.Crac;
import com.powsybl.openrao.data.crac.api.parameters.CracCreationParameters;
import com.powsybl.openrao.data.crac.io.fbconstraint.FbConstraintCreationContext;
import com.powsybl.openrao.data.raoresult.api.ComputationStatus;
import com.powsybl.openrao.data.raoresult.api.RaoResult;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.io.InputStream;
import java.net.URL;
import java.time.OffsetDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * @author Ameni Walha {@literal <ameni.walha at rte-france.com>}
 */
@SpringBootTest
class FileImporterTest {

    @Autowired
    private FileImporter fileImporter;

    @MockitoBean
    private MinioAdapter minioAdapter;

    private final String testDirectory = "/20210723";
    private final OffsetDateTime dateTime = OffsetDateTime.parse("2021-07-22T22:30Z");

    @Test
    void importCbcora() {
        final InputStream networkStream = getClass().getResourceAsStream(testDirectory + "/20210723_0030_2D5_CGM.uct");
        final Network network = Network.read("20210723_0030_2D5_CGM.uct", networkStream);
        final CoreCCFileResource cbcoraFile = createFileResource("cbcora", getClass().getResource(testDirectory + "/20210723-F301_CBCORA_hvdcvh-outage.xml"));
        final FbConstraintCreationContext fbConstraintCreationContext = fileImporter.importCbcora(cbcoraFile.getUrl(), dateTime, network);
        final Crac crac = fbConstraintCreationContext.getCrac();
        Assertions.assertNotNull(crac);
        assertEquals("17XTSO-CS------W-20190108-F301v1", crac.getId());
    }

    @Test
    void cracCreationParameters() {
        final CracCreationParameters parameters = fileImporter.getCimCracCreationParameters();
        Assertions.assertEquals(2147483647, parameters.getRaUsageLimitsPerInstant().get("curative").getMaxRa());
    }

    @Test
    void importNetworkTest() {
        final String networkPath = testDirectory + "/20210723_0030_2D5_CGM.uct";
        final String cgmUrl = getClass().getResource(networkPath).toExternalForm();
        Mockito.when(minioAdapter.getFile(cgmUrl)).thenReturn(getClass().getResourceAsStream(networkPath));

        final Network network = fileImporter.importNetwork(cgmUrl);
        assertNotNull(network);
        assertEquals("20210723_0030_2D5_CGM", network.getNameOrId());
    }

    @Test
    void importRaoResult() {
        final String raoResultFileName = "raoResult.json";
        final String raoResultUrl = getClass().getResource(testDirectory + "/" + raoResultFileName).toExternalForm();

        final InputStream networkStream = getClass().getResourceAsStream(testDirectory + "/20210723_0030_2D5_CGM.uct");
        final Network network = Network.read("20210723_0030_2D5_CGM.uct", networkStream);
        final CoreCCFileResource cbcoraFile = createFileResource("cbcora", getClass().getResource(testDirectory + "/20210723-F301_CBCORA_hvdcvh-outage.xml"));
        final FbConstraintCreationContext fbConstraintCreationContext = fileImporter.importCbcora(cbcoraFile.getUrl(), dateTime, network);
        final Crac crac = fbConstraintCreationContext.getCrac();

        final RaoResult raoResult = fileImporter.importRaoResult(raoResultUrl, crac);
        assertEquals(ComputationStatus.DEFAULT, raoResult.getComputationStatus());
        assertNotNull(raoResult);
    }

    private CoreCCFileResource createFileResource(final String filename, final URL resource) {
        return new CoreCCFileResource(filename, resource.toExternalForm());
    }
}
