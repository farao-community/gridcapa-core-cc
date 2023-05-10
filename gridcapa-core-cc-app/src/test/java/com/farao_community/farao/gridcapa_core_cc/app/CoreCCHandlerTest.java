/*
 * Copyright (c) 2021, RTE (http://www.rte-france.com)
 *  This Source Code Form is subject to the terms of the Mozilla Public
 *  License, v. 2.0. If a copy of the MPL was not distributed with this
 *  file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package com.farao_community.farao.gridcapa_core_cc.app;

import com.farao_community.farao.gridcapa_core_cc.api.resource.CoreCCFileResource;
import com.farao_community.farao.gridcapa_core_cc.api.resource.CoreCCRequest;
import com.farao_community.farao.gridcapa_core_cc.api.resource.CoreCCResponse;
import com.farao_community.farao.minio_adapter.starter.MinioAdapter;
import com.farao_community.farao.rao_runner.api.resource.RaoResponse;
import com.farao_community.farao.rao_runner.starter.AsynchronousRaoRunnerClient;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;

import java.net.MalformedURLException;
import java.net.URISyntaxException;
import java.net.URL;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.concurrent.CompletableFuture;

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
    private AsynchronousRaoRunnerClient asynchronousRaoRunnerClient;

    @Test
    void handleCoreCCRequestTest() {
        Mockito.when(minioAdapter.generatePreSignedUrl(Mockito.any())).thenReturn("http://url");
        CompletableFuture<RaoResponse> future = new CompletableFuture<>();
        RaoResponse raoResponse = new RaoResponse("id", "instant", "praUrl", "cracUrl", "raoUrl", Instant.now(), Instant.now());
        future.complete(raoResponse);
        Mockito.when(asynchronousRaoRunnerClient.runRaoAsynchronously(Mockito.any())).thenReturn(future);

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
        CoreCCResponse response = coreCCHandler.handleCoreCCRequest(request, true);
        assertEquals(requestId, response.getId());
        //should upload 7 artifacts: parameters + 3 networks + 3 cracs
        Mockito.verify(minioAdapter, Mockito.times(7)).uploadArtifact(Mockito.any(), Mockito.any());
    }

    private CoreCCFileResource createFileResource(String filename, URL resource) {
        try {
            return new CoreCCFileResource(filename, resource.toURI().toURL().toString());
        } catch (MalformedURLException e) {
            throw new RuntimeException(e);
        } catch (URISyntaxException e) {
            throw new RuntimeException(e);
        }
    }
}
