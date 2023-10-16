/*
 * Copyright (c) 2023, RTE (http://www.rte-france.com)
 *  This Source Code Form is subject to the terms of the Mozilla Public
 *  License, v. 2.0. If a copy of the MPL was not distributed with this
 *  file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package com.farao_community.farao.gridcapa_core_cc.app;

import com.farao_community.farao.gridcapa_core_cc.api.resource.CoreCCFileResource;
import com.farao_community.farao.gridcapa_core_cc.api.resource.CoreCCRequest;
import com.farao_community.farao.gridcapa_core_cc.api.resource.CoreCCResponse;
import com.farao_community.farao.gridcapa_core_cc.api.resource.InternalCoreCCRequest;
import com.farao_community.farao.gridcapa_core_cc.app.postprocessing.FileExporterHelper;
import com.farao_community.farao.gridcapa_core_cc.app.services.RaoRunnerService;
import com.farao_community.farao.minio_adapter.starter.MinioAdapter;
import com.farao_community.farao.rao_runner.api.resource.RaoResponse;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URISyntaxException;
import java.net.URL;
import java.time.Instant;
import java.time.OffsetDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * @author Ameni Walha {@literal <ameni.walha at rte-france.com>}
 */
@SpringBootTest
class CoreCCHandlerTest {

    @Autowired
    private CoreCCHandler coreCCHandler;

    @MockBean
    private MinioAdapter minioAdapter;

    @MockBean
    private RaoRunnerService raoRunnerService;

    @MockBean
    private FileExporterHelper fileExporterHelper;

    @Test
    void handleCoreCCRequestTest() throws IOException {
        Mockito.when(minioAdapter.generatePreSignedUrl(Mockito.any())).thenReturn("http://url");
        RaoResponse raoResponse = new RaoResponse("id", "instant", "praUrl", "cracUrl", "raoUrl", Instant.now(), Instant.now());
        Mockito.when(raoRunnerService.run(Mockito.any())).thenReturn(raoResponse);
        Mockito.doNothing().when(Mockito.mock(FileExporterHelper.class)).exportCneToMinio(Mockito.any());
        Mockito.doNothing().when(Mockito.mock(FileExporterHelper.class)).exportNetworkToMinio(Mockito.any());
        Mockito.doNothing().when(Mockito.mock(FileExporterHelper.class)).exportRaoResultToMinio(Mockito.any());
        Mockito.doNothing().when(Mockito.mock(FileExporterHelper.class)).exportMetadataToMinio(Mockito.any());

        String requestId = "Test request";
        String networkFileName = "20210723-F119-v1-17XTSO-CS------W-to-22XCORESO------S.zip";
        String testDirectory = "/20210723";
        CoreCCFileResource networkFile = createFileResource(networkFileName, getClass().getResource(testDirectory + "/" + networkFileName));

        OffsetDateTime dateTime = OffsetDateTime.parse("2021-07-22T22:30Z");
        CoreCCFileResource refProgFile = createFileResource("", getClass().getResource(testDirectory + "/20210723-F110.xml"));
        CoreCCFileResource raoRequestFile = createFileResource("", getClass().getResource(testDirectory + "/20210723-F302-v3.xml"));
        CoreCCFileResource virtualHubFile = createFileResource("", getClass().getResource(testDirectory + "/20210723-F327-fake.xml"));
        CoreCCFileResource glskFile = createFileResource("", getClass().getResource(testDirectory + "/20210723-F226-v1.xml"));
        CoreCCFileResource cbcoraFile = createFileResource("cbcora",  getClass().getResource(testDirectory + "/20210723-F301_CBCORA_hvdcvh-outage.xml"));

        CoreCCRequest request = new CoreCCRequest(requestId, dateTime, networkFile, cbcoraFile, glskFile,  refProgFile, raoRequestFile, virtualHubFile, true);
        InternalCoreCCRequest internalCoreCCRequest = new InternalCoreCCRequest(request);
        CoreCCResponse response = coreCCHandler.handleCoreCCRequest(internalCoreCCRequest);
        assertEquals(requestId, response.getId());
        //should upload 7 artifacts: parameters + ACK + crac + network
        Mockito.verify(minioAdapter, Mockito.times(4)).uploadArtifact(Mockito.any(), Mockito.any());
        // TODO : delete generated tmp dir
    }

    private CoreCCFileResource createFileResource(String filename, URL resource) {
        try {
            return new CoreCCFileResource(filename, resource.toURI().toURL().toString());
        } catch (MalformedURLException | URISyntaxException e) {
            throw new RuntimeException(e);
        }
    }
}
