/*
 * Copyright (c) 2021, RTE (http://www.rte-france.com)
 *  This Source Code Form is subject to the terms of the Mozilla Public
 *  License, v. 2.0. If a copy of the MPL was not distributed with this
 *  file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package com.farao_community.farao.gridcapa_core_cc.app;

import com.farao_community.farao.gridcapa_core_cc.app.services.FileExporter;
import com.farao_community.farao.gridcapa_core_cc.app.services.FileImporter;
import com.farao_community.farao.gridcapa_core_cc.app.study_point.StudyPointService;
import com.farao_community.farao.gridcapa_core_cc.api.resource.CoreCCFileResource;
import com.farao_community.farao.gridcapa_core_cc.api.resource.CoreCCRequest;
import com.farao_community.farao.gridcapa_core_cc.api.resource.CoreCCResponse;
import com.farao_community.farao.minio_adapter.starter.MinioAdapter;
import com.farao_community.farao.rao_runner.api.resource.RaoRequest;
import com.farao_community.farao.rao_runner.api.resource.RaoResponse;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;

import java.net.URL;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.HashMap;
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
    private StudyPointService studyPointService;

    @MockBean
    private FileExporter fileExporter;

    @MockBean
    private FileImporter fileImporter;

    @Test
    void handleCoreCCRequestTest() {
        Mockito.when(minioAdapter.generatePreSignedUrl(Mockito.any())).thenReturn("http://url");
        RaoRequest raorequest = Mockito.mock(RaoRequest.class);
        Mockito.when(studyPointService.computeStudyPointShift(Mockito.any(), Mockito.any(), Mockito.any(), Mockito.any())).thenReturn(raorequest);
        CompletableFuture<RaoResponse> future = new CompletableFuture<>();
        RaoResponse raoResponse = new RaoResponse("id", "instant", "praUrl", "cracUrl", "raoUrl", Instant.now(), Instant.now());
        Mockito.when(studyPointService.computeStudyPointRao(Mockito.any(), Mockito.any())).thenReturn(future);
        future.complete(raoResponse);
        Mockito.when(fileExporter.exportStudyPointResult(Mockito.any(), Mockito.any(), Mockito.any())).thenReturn(new HashMap<>());
        Mockito.when(fileImporter.importNetworkFromUrl(Mockito.any())).thenReturn(null);
        Mockito.when(fileExporter.saveShiftedCgmWithPra(Mockito.any(), Mockito.any())).thenReturn("");

        String requestId = "Test request";
        String networkFileName = "20210723_0030_2D5_CGM_limits.uct";
        String testDirectory = "/20210723";
        CoreCCFileResource networkFile = createFileResource(networkFileName, getClass().getResource(testDirectory + "/" + networkFileName));

        OffsetDateTime dateTime = OffsetDateTime.parse("2021-07-22T22:30Z");
        CoreCCFileResource refProgFile = createFileResource("", getClass().getResource(testDirectory + "/20210723-F110.xml"));
        CoreCCFileResource raorequestFile = createFileResource("", getClass().getResource(testDirectory + "/20210723-RaoRequest.csv"));
        CoreCCFileResource virtualhubFile = createFileResource("", getClass().getResource(testDirectory + "/20210723-virtualhub.csv"));
        CoreCCFileResource glskFile = createFileResource("", getClass().getResource(testDirectory + "/20210723-F226-v1.xml"));
        CoreCCFileResource cbcoraFile = createFileResource("cbcora",  getClass().getResource(testDirectory + "/20210723-F301_CBCORA_hvdcvh-outage.xml"));

        CoreCCRequest request = new CoreCCRequest(requestId, dateTime, networkFile, cbcoraFile, glskFile,  refProgFile, raorequestFile, virtualhubFile, true);
        CoreCCResponse response = coreCCHandler.handleCoreCCRequest(request);
        assertEquals(requestId, response.getId());
        Mockito.verify(minioAdapter, Mockito.times(1)).deleteFiles(Mockito.any());
    }

    private CoreCCFileResource createFileResource(String filename, URL resource) {
        return new CoreCCFileResource(filename, resource.toExternalForm());
    }
}
