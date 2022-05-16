/*
 * Copyright (c) 2022, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package com.farao_community.farao.gridcapa.core_cc.app.postprocessing;

import com.farao_community.farao.gridcapa.core_cc.app.TaskUtils;
import com.farao_community.farao.gridcapa.core_cc.app.entities.HourlyRaoRequest;
import com.farao_community.farao.gridcapa.core_cc.app.entities.HourlyRaoResult;
import com.farao_community.farao.gridcapa.core_cc.app.entities.RaoIntegrationTask;
import com.farao_community.farao.gridcapa.core_cc.app.entities.TaskStatus;
import com.farao_community.farao.gridcapa.core_cc.app.messaging.MinioAdapter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;

import java.io.InputStream;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * @author Mohamed BenRejeb {@literal <mohamed.ben-rejeb at rte-france.com>}
 */
@SpringBootTest
class RaoIPostProcessServiceTest {

    @Autowired
    RaoIPostProcessService raoIPostProcessService;

    @MockBean
    MinioAdapter minioAdapter;

    @BeforeEach
    public void setUp() {
        Mockito.when(minioAdapter.getFileNameFromUrl(Mockito.any())).thenCallRealMethod();
        Mockito.doNothing().when(minioAdapter).uploadFile(Mockito.any(), Mockito.any());
        Mockito.doNothing().when(minioAdapter).uploadFile(Mockito.any(), Mockito.any(), Mockito.any());
    }

    @Test
    void renameRaoHourlyResultsAndSendToDailyOutputsTest() {
        InputStream inputCracXmlInputStream = getClass().getResourceAsStream("/postprocessing/post_processing_cne/F301.xml");
        Mockito.when(minioAdapter.getInputStreamFromUrl("http://host:9000/inputCracXml.xml")).thenReturn(inputCracXmlInputStream);
        InputStream crac1InputStream = getClass().getResourceAsStream("/postprocessing/post_processing_cne/crac.json");
        Mockito.when(minioAdapter.getInputStreamFromUrl("http://host:9000/crac1.json")).thenReturn(crac1InputStream);
        InputStream networkInputStream = getClass().getResourceAsStream("/postprocessing/post_processing_cne/network.xiidm");
        Mockito.when(minioAdapter.getInputStreamFromUrl("http://host:9000/network1.xiidm")).thenReturn(networkInputStream);
        InputStream raoParamsInputStream = getClass().getResourceAsStream("/postprocessing/post_processing_cne/rao_params.json");
        Mockito.when(minioAdapter.getInputStreamFromUrl("http://host:9000/rao_params.json")).thenReturn(raoParamsInputStream);
        InputStream raoResult1InputStream = getClass().getResourceAsStream("/postprocessing/post_processing_cne/raoResult.json");
        Mockito.when(minioAdapter.getInputStreamFromUrl("http://host:9000/raoResult1.json")).thenReturn(raoResult1InputStream);
        InputStream networkWithPra1InputStream = getClass().getResourceAsStream("/postprocessing/post_processing_cne/network.xiidm");
        Mockito.when(minioAdapter.getInputStreamFromUrl("http://host:9000/networkWithPra.xiidm")).thenReturn(networkWithPra1InputStream);
        RaoIntegrationTask task = new RaoIntegrationTask();
        TaskUtils.setTaskId(task, 2L);
        task.setVersion(12);
        task.setTimeInterval("2019-01-07T23:00Z/2019-01-08T23:00Z");
        task.setInputCracXmlFileUrl("http://host:9000/inputCracXml.xml");
        Set<HourlyRaoRequest> hourlyInputs = new HashSet<>();

        HourlyRaoRequest hourlyInput1 = new HourlyRaoRequest();
        hourlyInput1.setInstant("2019-01-08T12:30:00Z");
        hourlyInput1.setCracFileUrl("http://host:9000/crac1.json");
        hourlyInput1.setNetworkFileUrl("http://host:9000/network1.xiidm");
        hourlyInput1.setRaoParametersFileUrl("http://host:9000/rao_params.json");
        hourlyInputs.add(hourlyInput1);

        task.setHourlyRaoRequests(hourlyInputs);

        Set<HourlyRaoResult> hourlyResults = new HashSet<>();
        HourlyRaoResult hourlyRaoResult1 = new HourlyRaoResult();
        hourlyRaoResult1.setInstant("2019-01-08T12:30:00Z");
        hourlyRaoResult1.setStatus(TaskStatus.SUCCESS);
        hourlyRaoResult1.setNetworkWithPraUrl("http://host:9000/networkWithPra.xiidm");
        hourlyRaoResult1.setRaoResultFileUrl("http://host:9000/raoResult1.json");
        hourlyResults.add(hourlyRaoResult1);
        task.setHourlyRaoResults(hourlyResults);

        raoIPostProcessService.renameRaoHourlyResultsAndSendToDailyOutputs(task, "targetMinioFolder", true);

        assertEquals("targetMinioFolder/outputs/CASTOR-RAO_22VCOR0CORE0PRDI_RTE-F299_20190108-F299-12.zip", task.getDailyOutputs().getCnesZipPath());
        assertEquals("targetMinioFolder/outputs/CASTOR-RAO_22VCOR0CORE0PRDI_RTE-F304_20190108-F304-12.zip", task.getDailyOutputs().getCgmsZipPath());
    }
}

