/*
 * Copyright (c) 2022, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package com.farao_community.farao.gridcapa.core_cc.app.preprocessing;

import com.farao_community.farao.gridcapa.core_cc.app.MinioMemoryMock;
import com.farao_community.farao.gridcapa.core_cc.app.entities.HourlyRaoRequest;
import com.farao_community.farao.gridcapa.core_cc.app.entities.HourlyRaoResult;
import com.farao_community.farao.gridcapa.core_cc.app.entities.RaoIntegrationTask;
import com.farao_community.farao.gridcapa.core_cc.app.entities.TaskStatus;
import org.apache.commons.io.IOUtils;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * @author Mohamed BenRejeb {@literal <mohamed.ben-rejeb at rte-france.com>}
 */
class RaoIPreProcessServiceTest {

    private RaoIPreProcessService raoIPreprocessService;

    private MinioMemoryMock minioAdapter;

    @BeforeEach
    public void setUp() {
        minioAdapter = new MinioMemoryMock();
        raoIPreprocessService = new RaoIPreProcessService(minioAdapter, Mockito.mock(RaoParametersService.class));
    }

    @Test
    void initTaskSuccessfullyTest() throws IOException {
        RaoIntegrationTask task = new RaoIntegrationTask();
        InputStream inputsArchiveInputStream = getClass().getResourceAsStream("/preprocessing/test3TS.zip");
        MultipartFile multipartFileArchive = new MockMultipartFile("test3TS",
                "test3TS.zip", "application/octet-stream", IOUtils.toByteArray(inputsArchiveInputStream));
        raoIPreprocessService.initializeTaskFromZipArchive(task, multipartFileArchive);
        Assertions.assertEquals("e880ff7e-8d81-4f89-86b0-e3276e9d9476", task.getCorrelationId());
        Assertions.assertEquals(3, task.getHourlyRaoRequests().size());
        Assertions.assertEquals(0, task.getHourlyRaoResults().size());
        HourlyRaoRequest hourlyRaoRequest = task.getHourlyRaoRequests().iterator().next();
        Assertions.assertEquals("2020-03-29T22:00:00Z", hourlyRaoRequest.getInstant());
        Assertions.assertEquals("presigned:0/inputs/cracs/20200329_2200.json", hourlyRaoRequest.getCracFileUrl());
        Assertions.assertEquals("presigned:0/inputs/networks/20200329_2200.xiidm", hourlyRaoRequest.getNetworkFileUrl());
        Assertions.assertEquals("presigned:0/inputs/20200330-F319-v1-17XTSO-CS------W-to-22XCORESO------S.xml", hourlyRaoRequest.getRealGlskFileUrl());
        Assertions.assertEquals("presigned:0/inputs/20200330-F120-v1-17XTSO-CS------W-to-22XCORESO------S.xml", hourlyRaoRequest.getRefprogFileUrl());
        Assertions.assertEquals("0/hourly_rao_results/20200329_2200", hourlyRaoRequest.getResultsDestination());
    }

    @Test
    void initTaskWithFailureTest() throws IOException {
        RaoIntegrationTask task = new RaoIntegrationTask();
        InputStream inputsArchiveInputStream = getClass().getResourceAsStream("/preprocessing/test3TS_Error.zip");
        MultipartFile multipartFileArchive = new MockMultipartFile("test3TS_Error",
                "test3TS_Error.zip", "application/octet-stream", IOUtils.toByteArray(inputsArchiveInputStream));
        raoIPreprocessService.initializeTaskFromZipArchive(task, multipartFileArchive);
        Assertions.assertEquals(3, task.getHourlyRaoRequests().size());
        HourlyRaoRequest hourlyRaoRequest = task.getHourlyRaoRequests().iterator().next();
        Assertions.assertEquals("2020-03-29T22:00:00Z", hourlyRaoRequest.getInstant());

        Assertions.assertEquals(3, task.getHourlyRaoResults().size());
        HourlyRaoResult hourlyRaoResult = task.getHourlyRaoResults().iterator().next();
        Assertions.assertEquals("2020-03-29T22:00:00Z", hourlyRaoResult.getInstant());
        Assertions.assertEquals(TaskStatus.ERROR, hourlyRaoResult.getStatus());
        Assertions.assertEquals("Please check the naming format of Refprog. No match with: ^[0-9]{8}-F120-v[0-9]-17XTSO-CS------W-to-22XCORESO------S.xml", hourlyRaoResult.getErrorMessage());

    }

    @Test
    void initTaskWithPartialErrorTest() throws IOException {
        RaoIntegrationTask task = new RaoIntegrationTask();
        InputStream inputsArchiveInputStream = getClass().getResourceAsStream("/preprocessing/test3TS_Partial_Error.zip");
        MultipartFile multipartFileArchive = new MockMultipartFile("test3TS_Partial_Error",
                "test3TS_Partial_Error.zip", "application/octet-stream", IOUtils.toByteArray(inputsArchiveInputStream));
        raoIPreprocessService.initializeTaskFromZipArchive(task, multipartFileArchive);
        Assertions.assertEquals(3, task.getHourlyRaoRequests().size());
        Assertions.assertEquals(1, task.getHourlyRaoResults().size());
        HourlyRaoResult hourlyRaoResult = task.getHourlyRaoResults().iterator().next();
        Assertions.assertEquals("2020-03-29T23:00:00Z", hourlyRaoResult.getInstant());
        Assertions.assertEquals(TaskStatus.ERROR, hourlyRaoResult.getStatus());
        Assertions.assertEquals("Error occurred while trying to import inputs at timestamp: 2020-03-29T23:00:00Z. Origin cause : Please check the naming format of the CGMs. No match with: 20200330_0030_2D1_UC5_F100_CORESO.uct", hourlyRaoResult.getErrorMessage());
    }

    @Test
    void testRaoRequestAcknowledgment() throws IOException {
        RaoIntegrationTask task = new RaoIntegrationTask();
        InputStream inputsArchiveInputStream = getClass().getResourceAsStream("/preprocessing/test3TS.zip");
        MultipartFile multipartFileArchive = new MockMultipartFile("test3TS",
                "test3TS.zip", "application/octet-stream", IOUtils.toByteArray(inputsArchiveInputStream));
        new RaoIPreProcessService(minioAdapter, Mockito.mock(RaoParametersService.class)).initializeTaskFromZipArchive(task, multipartFileArchive);

        Assertions.assertEquals("0/outputs/CASTOR-RAO_22VCOR0CORE0TST4_RTE-F302-ACK_20200330-F302-01.xml", task.getDailyOutputs().getRaoRequestAckPath());
        String expectedFileContents = new String(getClass().getResourceAsStream("/preprocessing/RaoRequestACK.xml").readAllBytes()).replace("\r", "");
        Assertions.assertEquals(expectedFileContents, minioAdapter.getFileContents(task.getDailyOutputs().getRaoRequestAckPath()));
    }

    @Test
    void testCracCreationReport() throws IOException {
        RaoIntegrationTask task = new RaoIntegrationTask();
        InputStream inputsArchiveInputStream = getClass().getResourceAsStream("/preprocessing/test3TS.zip");
        MultipartFile multipartFileArchive = new MockMultipartFile("test3TS",
            "test3TS.zip", "application/octet-stream", IOUtils.toByteArray(inputsArchiveInputStream));
        raoIPreprocessService.initializeTaskFromZipArchive(task, multipartFileArchive);

        String expectedCracCreationReport = "[REMOVED] Critical branch \"fr4_de1_N\" was not imported: NOT_FOR_REQUESTED_TIMESTAMP. CriticalBranch is not valid for the requested timestamp.\n" +
            "[REMOVED] Critical branch \"nl2_be3_N\" was not imported: NOT_FOR_RAO. critical branch nl2_be3_N was removed as it is neither a CNEC, nor a MNEC.";

        Path path = new File(task.getDailyOutputs().getLogsTmpOutputPath(), "20200330_0030_CRAC-CREATION-REPORT-01.txt").toPath();
        Assertions.assertEquals(expectedCracCreationReport, Files.readString(path));

        path = new File(task.getDailyOutputs().getLogsTmpOutputPath(), "20200330_0130_CRAC-CREATION-REPORT-01.txt").toPath();
        Assertions.assertEquals(expectedCracCreationReport, Files.readString(path));
    }
}
