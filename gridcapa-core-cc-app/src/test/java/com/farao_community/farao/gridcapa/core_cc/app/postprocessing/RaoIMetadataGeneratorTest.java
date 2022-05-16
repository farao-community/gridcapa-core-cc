/*
 * Copyright (c) 2022, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package com.farao_community.farao.gridcapa.core_cc.app.postprocessing;

import com.farao_community.farao.gridcapa.core_cc.app.MinioMemoryMock;
import com.farao_community.farao.gridcapa.core_cc.app.entities.HourlyRaoRequest;
import com.farao_community.farao.gridcapa.core_cc.app.entities.HourlyRaoResult;
import com.farao_community.farao.gridcapa.core_cc.app.entities.RaoIntegrationTask;
import com.farao_community.farao.gridcapa.core_cc.app.entities.TaskStatus;
import com.farao_community.farao.gridcapa.core_cc.app.messaging.MinioAdapter;
import com.farao_community.farao.gridcapa.core_cc.app.messaging.RaoIntegrationInputsProperties;
import com.farao_community.farao.gridcapa.core_cc.app.messaging.configuration.MinioConfiguration;
import com.farao_community.farao.gridcapa.core_cc.app.messaging.configuration.UrlWhitelistConfiguration;
import io.minio.MinioClient;
import org.apache.commons.io.FileUtils;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.boot.test.context.SpringBootTest;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * @author Peter Mitri {@literal <peter.mitri at rte-france.com>}
 */
@SpringBootTest
class RaoIMetadataGeneratorTest {

    @Test
    void testMetadataCsv() throws IOException {
        RaoIntegrationTask task = new RaoIntegrationTask();

        task.setTimeInterval("2021-10-23T22:00Z/2021-10-24T22:00Z");
        String timeStampBegin = "2021-10-23T22:00Z";

        task.setTaskId(1);
        task.setVersion(2);
        task.setTaskStatus(TaskStatus.SUCCESS);
        task.setCorrelationId("6fe0a389-9315-417e-956d-b3fbaa479caz");
        task.setRaoRequestFileName("20200330-F302-v3-17XTSO-CS------W-to-22XCORESO------S.xml");
        task.setInputsReceptionInstant(Instant.ofEpochSecond(3600 * 8));
        task.setComputationStartInstant(Instant.ofEpochSecond(3600 * 9));
        task.setComputationEndInstant(Instant.ofEpochSecond(24 * 3600 * 11));
        task.setOutputsSendingInstant(Instant.ofEpochSecond(24 * 3600 * 12));
        OffsetDateTime initialDT = OffsetDateTime.parse(timeStampBegin);
        Set<HourlyRaoRequest> requests = new HashSet<>();
        Set<HourlyRaoResult> results = new HashSet<>();
        List<TaskStatus> taskStatusRotation = List.of(TaskStatus.SUCCESS, TaskStatus.RUNNING, TaskStatus.ERROR, TaskStatus.CREATED);
        for (int h = 0; h < 24; h++) {
            OffsetDateTime dt = initialDT.plusHours(h);
            String instant = dt.toString().substring(0, 16) + ":00Z";
            HourlyRaoResult result = new HourlyRaoResult();
            result.setStatus(taskStatusRotation.get(h % 4));
            result.setInstant(instant);
            result.setComputationStartInstant(Instant.ofEpochSecond(h * 3600 * 10));
            result.setComputationEndInstant(Instant.ofEpochSecond(h * 3600 * 11));
            results.add(result);
        }
        task.setHourlyRaoRequests(requests);
        task.setHourlyRaoResults(results);

        MinioConfiguration minioConfiguration = Mockito.mock(MinioConfiguration.class);
        Mockito.when(minioConfiguration.getDefaultBucket()).thenReturn(null);
        MinioMock minioMock = new MinioMock(minioConfiguration, null, null, null);
        new RaoIMetadataGenerator(minioMock).exportMetadataFile(task, "targetFolder", true);
        assertEquals("targetFolder/outputs/CASTOR-RAO_22VCOR0CORE0PRDI_RTE-F341_20211024-F341-02.csv", minioMock.pathDestination);
        // Windows : replace CRLF with LF (\r\n with \n)
        String expectedCsv = new String(getClass().getResourceAsStream("/postprocessing/metadata.csv").readAllBytes()).replace("\r", "");
        assertEquals(expectedCsv, minioMock.readFile);
    }

    @Test
    void testMetadataCsvFailedTask() throws IOException {
        RaoIntegrationTask task = new RaoIntegrationTask();

        task.setTimeInterval("2021-10-23T22:00Z/2021-10-24T22:00Z");
        String timeStampBegin = "2021-10-23T22:00Z";

        task.setTaskId(1);
        task.setVersion(2);
        task.setTaskStatus(TaskStatus.ERROR);
        task.setCorrelationId("6fe0a389-9315-417e-956d-b3fbaa479caz");
        task.setRaoRequestFileName("20200330-F302-v3-17XTSO-CS------W-to-22XCORESO------S.xml");
        task.setInputsReceptionInstant(Instant.ofEpochSecond(3600));

        MinioMemoryMock minioAdapterMock = new MinioMemoryMock();
        new RaoIMetadataGenerator(minioAdapterMock).exportMetadataFile(task, "targetFolder", true);
        assertEquals("targetFolder/outputs/CASTOR-RAO_22VCOR0CORE0PRDI_RTE-F341_20211024-F341-02.csv", task.getDailyOutputs().getMetadataOutputsPath());
        String expectedFileContents = new String(getClass().getResourceAsStream("/postprocessing/metadata_error.csv").readAllBytes()).replace("\r", "");
        Assertions.assertEquals(expectedFileContents, minioAdapterMock.getFileContents(task.getDailyOutputs().getMetadataOutputsPath()));
    }

    /**
     * A MinioAdapter mock that stores the file and its path in memory when uploaded
     */
    private static class MinioMock extends MinioAdapter {
        private String pathDestination;
        private String readFile;

        public MinioMock(MinioConfiguration minioConfiguration, MinioClient minioClient, UrlWhitelistConfiguration urlWhitelistConfiguration, RaoIntegrationInputsProperties serverProperties) {
            super(minioConfiguration, minioClient, urlWhitelistConfiguration);
        }

        @Override
        public InputStream getInputStreamFromUrl(String url) {
            try {
                return FileUtils.openInputStream(new File(url));
            } catch (IOException e) {
                e.printStackTrace();
                return null;
            }
        }

        @Override
        public String getFileNameFromUrl(String stringUrl) {
            return stringUrl;
        }

        @Override
        public void uploadFile(String pathDestination, InputStream sourceInputStream) {
            try {
                this.pathDestination = pathDestination;
                readFile = new String(sourceInputStream.readAllBytes(), StandardCharsets.UTF_8);
            } catch (IOException e) {
                fail("Failed to read input stream");
            }
        }
    }
}
