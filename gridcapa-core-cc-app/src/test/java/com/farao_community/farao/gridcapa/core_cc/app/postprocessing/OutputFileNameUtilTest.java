/*
 * Copyright (c) 2022, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package com.farao_community.farao.gridcapa.core_cc.app.postprocessing;

import com.farao_community.farao.gridcapa.core_cc.app.TaskUtils;
import com.farao_community.farao.gridcapa.core_cc.app.entities.RaoIntegrationTask;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * @author Mohamed BenRejeb {@literal <mohamed.ben-rejeb at rte-france.com>}
 */

class OutputFileNameUtilTest {

    @Test
    void generateCgmFileNameTest() {
        assertEquals("20200330_0130_2D1_UX1.uct", OutputFileNameUtil.generateUctFileName("2020-03-29T23:00:00Z", 1));
    }

    @Test
    void generateOutputNamesFileNameTest() {
        RaoIntegrationTask task = new RaoIntegrationTask();
        TaskUtils.setTaskId(task, 1L);
        task.setVersion(3);
        task.setTimeInterval("2020-03-29T23:00:00Z/2020-03-30T00:00:00Z");
        assertEquals("CASTOR-RAO_22VCOR0CORE0TST4_RTE-F305_20200330-F305-03.xml", OutputFileNameUtil.generateRaoIResponseFileName(task));
        assertEquals("CASTOR-RAO_22VCOR0CORE0TST4_RTE-F304_20200330-F304-03.zip", OutputFileNameUtil.generateCgmZipName(task));
        assertEquals("CASTOR-RAO_22VCOR0CORE0TST4_RTE-F299_20200330-F299-03.zip", OutputFileNameUtil.generateCneZipName(task));
        assertEquals("CASTOR-RAO_22VCOR0CORE0TST4_RTE-F305_20200330-F305-03.xml", OutputFileNameUtil.generateRaoIResponseFileName(task));
        assertEquals("20200330_0130_20200330-F299-v3-22XCORESO------S_to_17XTSO-CS------W.xml", OutputFileNameUtil.generateCneFileName("2020-03-29T23:00:00Z", task));
        assertEquals("CASTOR-RAO_22VCOR0CORE0TST4_RTE-F302-ACK_20200330-F302-03.xml", OutputFileNameUtil.generateRaoRequestAckFileName(task));
    }

    @Test
    void test25TimestampDay() {
        RaoIntegrationTask task = new RaoIntegrationTask();
        TaskUtils.setTaskId(task, 1L);
        task.setVersion(4);
        task.setTimeInterval("2021-10-30T22:00:00Z/2021-10-31T23:00:00Z");

        assertEquals("20211031_0030_20211031-F299-v4-22XCORESO------S_to_17XTSO-CS------W.xml", OutputFileNameUtil.generateCneFileName("2021-10-30T22:00:00Z", task));
        assertEquals("20211031_0130_20211031-F299-v4-22XCORESO------S_to_17XTSO-CS------W.xml", OutputFileNameUtil.generateCneFileName("2021-10-30T23:00:00Z", task));
        assertEquals("20211031_0230_20211031-F299-v4-22XCORESO------S_to_17XTSO-CS------W.xml", OutputFileNameUtil.generateCneFileName("2021-10-31T00:00:00Z", task));
        assertEquals("20211031_B230_20211031-F299-v4-22XCORESO------S_to_17XTSO-CS------W.xml", OutputFileNameUtil.generateCneFileName("2021-10-31T01:00:00Z", task));
        assertEquals("20211031_0330_20211031-F299-v4-22XCORESO------S_to_17XTSO-CS------W.xml", OutputFileNameUtil.generateCneFileName("2021-10-31T02:00:00Z", task));
        assertEquals("20211031_2330_20211031-F299-v4-22XCORESO------S_to_17XTSO-CS------W.xml", OutputFileNameUtil.generateCneFileName("2021-10-31T22:00:00Z", task));

        assertEquals("20211031_0030_2D7_UX4.uct", OutputFileNameUtil.generateUctFileName("2021-10-30T22:00:00Z", 4));
        assertEquals("20211031_0130_2D7_UX4.uct", OutputFileNameUtil.generateUctFileName("2021-10-30T23:00:00Z", 4));
        assertEquals("20211031_0230_2D7_UX4.uct", OutputFileNameUtil.generateUctFileName("2021-10-31T00:00:00Z", 4));
        assertEquals("20211031_B230_2D7_UX4.uct", OutputFileNameUtil.generateUctFileName("2021-10-31T01:00:00Z", 4));
        assertEquals("20211031_0330_2D7_UX4.uct", OutputFileNameUtil.generateUctFileName("2021-10-31T02:00:00Z", 4));
        assertEquals("20211031_2330_2D7_UX4.uct", OutputFileNameUtil.generateUctFileName("2021-10-31T22:00:00Z", 4));

        assertEquals("20211031_0030_RAO-LOGS-04.txt", OutputFileNameUtil.generateLogsFileName("2021-10-30T22:00:00Z", task));
        assertEquals("20211031_0130_RAO-LOGS-04.txt", OutputFileNameUtil.generateLogsFileName("2021-10-30T23:00:00Z", task));
        assertEquals("20211031_0230_RAO-LOGS-04.txt", OutputFileNameUtil.generateLogsFileName("2021-10-31T00:00:00Z", task));
        assertEquals("20211031_B230_RAO-LOGS-04.txt", OutputFileNameUtil.generateLogsFileName("2021-10-31T01:00:00Z", task));
        assertEquals("20211031_0330_RAO-LOGS-04.txt", OutputFileNameUtil.generateLogsFileName("2021-10-31T02:00:00Z", task));
        assertEquals("20211031_2330_RAO-LOGS-04.txt", OutputFileNameUtil.generateLogsFileName("2021-10-31T22:00:00Z", task));

        assertEquals("20211031_0030_CRAC-CREATION-REPORT-04.txt", OutputFileNameUtil.generateCracCreationReportFileName("2021-10-30T22:00:00Z", task));
        assertEquals("20211031_0130_CRAC-CREATION-REPORT-04.txt", OutputFileNameUtil.generateCracCreationReportFileName("2021-10-30T23:00:00Z", task));
        assertEquals("20211031_0230_CRAC-CREATION-REPORT-04.txt", OutputFileNameUtil.generateCracCreationReportFileName("2021-10-31T00:00:00Z", task));
        assertEquals("20211031_B230_CRAC-CREATION-REPORT-04.txt", OutputFileNameUtil.generateCracCreationReportFileName("2021-10-31T01:00:00Z", task));
        assertEquals("20211031_0330_CRAC-CREATION-REPORT-04.txt", OutputFileNameUtil.generateCracCreationReportFileName("2021-10-31T02:00:00Z", task));
        assertEquals("20211031_2330_CRAC-CREATION-REPORT-04.txt", OutputFileNameUtil.generateCracCreationReportFileName("2021-10-31T22:00:00Z", task));
    }

    @Test
    void test23TimestampDay() {
        RaoIntegrationTask task = new RaoIntegrationTask();
        TaskUtils.setTaskId(task, 1L);
        task.setVersion(4);
        task.setTimeInterval("2022-03-26T23:00:00Z/2022-03-27T22:00:00Z");

        assertEquals("20220327_0030_20220327-F299-v4-22XCORESO------S_to_17XTSO-CS------W.xml", OutputFileNameUtil.generateCneFileName("2022-03-26T23:00:00Z", task));
        assertEquals("20220327_0130_20220327-F299-v4-22XCORESO------S_to_17XTSO-CS------W.xml", OutputFileNameUtil.generateCneFileName("2022-03-27T00:00:00Z", task));
        assertEquals("20220327_0330_20220327-F299-v4-22XCORESO------S_to_17XTSO-CS------W.xml", OutputFileNameUtil.generateCneFileName("2022-03-27T01:00:00Z", task));
        assertEquals("20220327_0430_20220327-F299-v4-22XCORESO------S_to_17XTSO-CS------W.xml", OutputFileNameUtil.generateCneFileName("2022-03-27T02:00:00Z", task));
        assertEquals("20220327_2330_20220327-F299-v4-22XCORESO------S_to_17XTSO-CS------W.xml", OutputFileNameUtil.generateCneFileName("2022-03-27T21:00:00Z", task));

        assertEquals("20220327_0030_2D7_UX4.uct", OutputFileNameUtil.generateUctFileName("2022-03-26T23:00:00Z", 4));
        assertEquals("20220327_0130_2D7_UX4.uct", OutputFileNameUtil.generateUctFileName("2022-03-27T00:00:00Z", 4));
        assertEquals("20220327_0330_2D7_UX4.uct", OutputFileNameUtil.generateUctFileName("2022-03-27T01:00:00Z", 4));
        assertEquals("20220327_0430_2D7_UX4.uct", OutputFileNameUtil.generateUctFileName("2022-03-27T02:00:00Z", 4));
        assertEquals("20220327_2330_2D7_UX4.uct", OutputFileNameUtil.generateUctFileName("2022-03-27T21:00:00Z", 4));

        assertEquals("20220327_0030_RAO-LOGS-04.txt", OutputFileNameUtil.generateLogsFileName("2022-03-26T23:00:00Z", task));
        assertEquals("20220327_0130_RAO-LOGS-04.txt", OutputFileNameUtil.generateLogsFileName("2022-03-27T00:00:00Z", task));
        assertEquals("20220327_0330_RAO-LOGS-04.txt", OutputFileNameUtil.generateLogsFileName("2022-03-27T01:00:00Z", task));
        assertEquals("20220327_0430_RAO-LOGS-04.txt", OutputFileNameUtil.generateLogsFileName("2022-03-27T02:00:00Z", task));
        assertEquals("20220327_2330_RAO-LOGS-04.txt", OutputFileNameUtil.generateLogsFileName("2022-03-27T21:00:00Z", task));

        assertEquals("20220327_0030_CRAC-CREATION-REPORT-04.txt", OutputFileNameUtil.generateCracCreationReportFileName("2022-03-26T23:00:00Z", task));
        assertEquals("20220327_0130_CRAC-CREATION-REPORT-04.txt", OutputFileNameUtil.generateCracCreationReportFileName("2022-03-27T00:00:00Z", task));
        assertEquals("20220327_0330_CRAC-CREATION-REPORT-04.txt", OutputFileNameUtil.generateCracCreationReportFileName("2022-03-27T01:00:00Z", task));
        assertEquals("20220327_0430_CRAC-CREATION-REPORT-04.txt", OutputFileNameUtil.generateCracCreationReportFileName("2022-03-27T02:00:00Z", task));
        assertEquals("20220327_2330_CRAC-CREATION-REPORT-04.txt", OutputFileNameUtil.generateCracCreationReportFileName("2022-03-27T21:00:00Z", task));
    }

}
