/*
 * Copyright (c) 2022, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package com.farao_community.farao.gridcapa.core_cc.app.postprocessing;

import com.farao_community.farao.data.crac_creation.creator.fb_constraint.xsd.ActionType;
import com.farao_community.farao.data.crac_creation.creator.fb_constraint.xsd.CriticalBranchType;
import com.farao_community.farao.data.crac_creation.creator.fb_constraint.xsd.FlowBasedConstraintDocument;
import com.farao_community.farao.data.crac_creation.creator.fb_constraint.xsd.IndependantComplexVariant;
import com.farao_community.farao.gridcapa.core_cc.app.TaskUtils;
import com.farao_community.farao.gridcapa.core_cc.app.entities.HourlyRaoRequest;
import com.farao_community.farao.gridcapa.core_cc.app.entities.HourlyRaoResult;
import com.farao_community.farao.gridcapa.core_cc.app.entities.RaoIntegrationTask;
import com.farao_community.farao.gridcapa.core_cc.app.entities.TaskStatus;
import com.farao_community.farao.gridcapa.core_cc.app.messaging.MinioAdapter;
import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.Multimap;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;

import javax.xml.bind.JAXBElement;
import java.io.InputStream;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * @author Pengbo Wang {@literal <pengbo.wang at rte-international.com>}
 * @author Mohamed BenRejeb {@literal <mohamed.ben-rejeb at rte-france.com>}
 * @author Baptiste Seguinot {@literal <baptiste.seguinot at rte-france.com>}
 */
@SpringBootTest
class DailyF303Generator2Test {

    /*
     * second test-case to test the F303 export, with four hours of data, several contingencies,
     * critical branches with varying parameters other time in the F301, and some invalid
     * elements in the F301 which are not imported
     */

    @Autowired
    private DailyF303Generator dailyFbConstraintDocumentGenerator;
    @MockBean
    private MinioAdapter minioAdapter;
    private RaoIntegrationTask task;

    private String ts;

    @BeforeEach
    public void setUp() {

        String timeStampBegin = "2019-01-07T23:00Z";

        // some constants
        OffsetDateTime initialDT = OffsetDateTime.parse(timeStampBegin);
        OffsetDateTime endDt = initialDT.plusHours(24);

        ts = endDt.format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        String bDir = "/postprocessing/f303-2/";
        String iDir = "inputs/";
        String oDir = "outputs/";

        DateTimeFormatter isoFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm'Z'");
        DateTimeFormatter fileNameFormatter = DateTimeFormatter.ofPattern("yyyyMMdd'_'HH'30UTC'");

        // create task
        task = new RaoIntegrationTask();
        TaskUtils.setTaskId(task, 1L);
        task.setTimeInterval(initialDT.format(isoFormatter) + "/" + endDt.format(isoFormatter));
        Set<HourlyRaoRequest> hourlyRaoRequests = new HashSet<>();
        Set<HourlyRaoResult> hourlyRaoResults = new HashSet<>();
        task.setHourlyRaoResults(hourlyRaoResults);
        task.setHourlyRaoRequests(hourlyRaoRequests);
        task.setInputCracXmlFileUrl("http://host:9000/inputCracXml.xml");

        // "store" crac xml in dataBase
        InputStream inputCracXmlInputStream = getClass().getResourceAsStream(bDir + iDir + ts + "_F301-crac.xml");
        Mockito.when(minioAdapter.getInputStreamFromUrl("http://host:9000/inputCracXml.xml")).thenReturn(inputCracXmlInputStream);

        // set results
        /*
        23h-11h & 16h-23h -> not requested
        15-16h -> requested but error in RAO
        11-15h -> requested and OK, with results below

        CRA used:
        11h30 C01 -> CRA_OPEN_TR-FR1, CRA_PST_BE@16
        11h30 CO2 -> CRA_OPEN_FR1-FR3-2, CRA_PST_BE@16
        12h30 CO1 -> CRA_OPEN_NL1-NL3, CRA_OPEN_TR-FR1, CRA_PST_BE@16
        12h30 C02 -> CRA_PST_BE@-16
        13h30 CO1 -> no CRA (initially CRA_OPEN_FR1-FR3-2, CRA_PST_BE@2, but manually deleted from RaoResult)
        13h30 C02 -> CRA_OPEN_FR1-FR3-2, CRA_OPEN_NL1-NL3, CRA_PST_BE@16
        14h30 CO1 -> CRA_OPEN_NL1-NL3, CRA_PST_BE@16
        14h30 CO2 -> no CRA (contingency invalid)

        some outages/RA and CNECs invalid for some timestamps
         */

        for (int h = 12; h <= 15; h++) {

            String hFile = initialDT.plusHours(h).format(fileNameFormatter);

            // "store" network in dataBase
            InputStream networkInputStream = getClass().getResourceAsStream(bDir + iDir + hFile + "_network.uct");
            Mockito.when(minioAdapter.getInputStreamFromUrl("http://host:9000/network" + hFile + ".uct")).thenReturn(networkInputStream);

            // "store" raoResult in dataBase
            InputStream raoResultInputStream = getClass().getResourceAsStream(bDir + oDir + hFile + "_raoResult.json");
            Mockito.when(minioAdapter.getInputStreamFromUrl("http://host:9000/raoResult" + hFile + ".json")).thenReturn(raoResultInputStream);

            // prepare task
            HourlyRaoRequest hourlyRaoRequest = new HourlyRaoRequest();
            hourlyRaoRequest.setInstant(initialDT.plusHours(h).format(DateTimeFormatter.ISO_OFFSET_DATE_TIME));
            hourlyRaoRequest.setNetworkFileUrl("http://host:9000/network" + hFile + ".uct");
            hourlyRaoRequests.add(hourlyRaoRequest);

            HourlyRaoResult hourlyRaoResult = new HourlyRaoResult();
            hourlyRaoResult.setInstant(initialDT.plusHours(h).format(DateTimeFormatter.ISO_OFFSET_DATE_TIME));
            hourlyRaoResult.setStatus(TaskStatus.SUCCESS);
            hourlyRaoResult.setRaoResultFileUrl("http://host:9000/raoResult" + hFile + ".json");
            hourlyRaoResults.add(hourlyRaoResult);
        }

        HourlyRaoRequest failedHourlyRaoRequest = new HourlyRaoRequest();
        failedHourlyRaoRequest.setInstant("2019-01-08T15:30:00Z");

        HourlyRaoResult failedHourlyRaoResult = new HourlyRaoResult();
        failedHourlyRaoResult.setInstant("2019-01-08T15:30:00Z");
        failedHourlyRaoResult.setStatus(TaskStatus.ERROR);
        hourlyRaoRequests.add(failedHourlyRaoRequest);
        hourlyRaoResults.add(failedHourlyRaoResult);

        Mockito.when(minioAdapter.getFileNameFromUrl(Mockito.any())).thenCallRealMethod();
    }

    @Test
    void testF303Generation() {

        FlowBasedConstraintDocument dailyFbConstDocument = dailyFbConstraintDocumentGenerator.generate(task);

        /*
        byte[] dailyFbConstraint = JaxbUtil.writeInBytes(FlowBasedConstraintDocument.class, dailyFbConstDocument);
        Files.write(Paths.get("/tmp/f303-out-" + ts + ".xml"), dailyFbConstraint);
        */

        // -----
        // check headers
        // -----

        assertNotNull(dailyFbConstDocument);
        assertEquals("22XCORESO------S-20190108-F303v1", dailyFbConstDocument.getDocumentIdentification().getV());
        assertEquals(1, dailyFbConstDocument.getDocumentVersion().getV());
        assertEquals("B07", dailyFbConstDocument.getDocumentType().getV().value());
        assertEquals("22XCORESO------S", dailyFbConstDocument.getSenderIdentification().getV());
        assertEquals("A44", dailyFbConstDocument.getSenderRole().getV().value());
        assertEquals("17XTSO-CS------W", dailyFbConstDocument.getReceiverIdentification().getV());
        assertEquals("A36", dailyFbConstDocument.getReceiverRole().getV().value());
        assertEquals("2019-01-07T23:00Z/2019-01-08T23:00Z", dailyFbConstDocument.getConstraintTimeInterval().getV());
        assertEquals("10YDOM-REGION-1V", dailyFbConstDocument.getDomain().getV());

        Multimap<String, CriticalBranchType> criticalBranches = buildCriticalBranchesMapPerOriginalId(dailyFbConstDocument);

        // -----
        // the initial f301 file contains 22 critical branches, so is the f303 file
        // -----

        assertEquals(23, criticalBranches.keySet().size());

        // -----
        // check that critical branches on N state, with a fixed definition, are the same for the 24hours
        // -----

        assertEquals(1, criticalBranches.get("001_FR-DE [N][DIR]").size());
        assertEquals(1, criticalBranches.get("009_NL-BE [N][DIR]").size());
        assertEquals(1, criticalBranches.get("010_NL-BE [N][OPP]").size());

        // imax factor is equal to permanent factor for critical branches on N state
        assertEquals(1, criticalBranches.get("010_NL-BE [N][OPP]").stream().findAny().orElseThrow().getImaxFactor().doubleValue(), 1e-6);
        assertNull(criticalBranches.get("010_NL-BE [N][OPP]").stream().findAny().orElseThrow().getImaxA());

        // -----
        // check that critical branches with a variable definition depending on the hour, keep their variation
        // -----
        Collection<CriticalBranchType> cb002 = criticalBranches.get("002_FR-DE [N][OPP]");
        assertEquals(2, cb002.size());

        // same definition as in the f301
        CriticalBranchType pureMnec = getCriticalBranch(cb002, "002_FR-DE [N][OPP]", "2019-01-07T23:00Z/2019-01-08T13:00Z");
        CriticalBranchType cnecAndMnec = getCriticalBranch(cb002, "002_FR-DE [N][OPP]", "2019-01-08T13:00Z/2019-01-08T23:00Z");
        assertNotNull(pureMnec);
        assertNotNull(cnecAndMnec);
        assertFalse(pureMnec.isCNEC());
        assertTrue(cnecAndMnec.isCNEC());

        // -----
        // check that the invalid critical branches has been removed of the optimized hours
        // -----
        // 02/02/2022 -> change in F303 export, invalid elements are now exported for all time-stamps

        assertEquals(1, criticalBranches.get("022_NEVER_VALID").size());
        assertTrue(criticalBranches.get("022_NEVER_VALID").stream().anyMatch(cb -> cb.getTimeInterval().getV().equals("2019-01-07T23:00Z/2019-01-08T23:00Z")));

        // -----
        // check an always valid branch of CO1
        // -----

        Collection<CriticalBranchType> cb007 = criticalBranches.get("007_DE-NL [CO1][DIR]");
        assertEquals(8, cb007.size());
        // 1 per invalid / not requested / without CRA interval
        assertNotNull(getCriticalBranch(cb007, "007_DE-NL [CO1][DIR]", "2019-01-07T23:00Z/2019-01-08T11:00Z"));
        assertNotNull(getCriticalBranch(cb007, "007_DE-NL [CO1][DIR]", "2019-01-07T23:00Z/2019-01-08T11:00Z"));
        assertNotNull(getCriticalBranch(cb007, "007_DE-NL [CO1][DIR]", "2019-01-08T13:00Z/2019-01-08T14:00Z"));

        // 1 TATL per interval with CRAs
        assertNotNull(getCriticalBranch(cb007, "007_DE-NL [CO1][DIR]_TATL", "2019-01-08T11:00Z/2019-01-08T13:00Z"));
        assertNotNull(getCriticalBranch(cb007, "007_DE-NL [CO1][DIR]_TATL", "2019-01-08T14:00Z/2019-01-08T15:00Z"));

        // 1 PATL per hour with CRAs
        assertNotNull(getCriticalBranch(cb007, "007_DE-NL [CO1][DIR]_PATL", "2019-01-08T11:00Z/2019-01-08T12:00Z"));
        assertNotNull(getCriticalBranch(cb007, "007_DE-NL [CO1][DIR]_PATL", "2019-01-08T12:00Z/2019-01-08T13:00Z"));
        assertNotNull(getCriticalBranch(cb007, "007_DE-NL [CO1][DIR]_PATL", "2019-01-08T14:00Z/2019-01-08T15:00Z"));

        // -----
        // check Imax factor of the valid branch after CO1
        // -----

        // on hours with CRA, Imax are different for TATL and PATL
        assertEquals(1.15, getCriticalBranch(cb007, "007_DE-NL [CO1][DIR]_TATL", "2019-01-08T14:00Z/2019-01-08T15:00Z").getImaxFactor().doubleValue(), 1e-6);
        assertEquals(1., getCriticalBranch(cb007, "007_DE-NL [CO1][DIR]_PATL", "2019-01-08T14:00Z/2019-01-08T15:00Z").getImaxFactor().doubleValue(), 1e-6);

        // on hours without CRA and invalid hours, only PATL is taken into account
        assertEquals(1., getCriticalBranch(cb007, "007_DE-NL [CO1][DIR]", "2019-01-08T13:00Z/2019-01-08T14:00Z").getImaxFactor().doubleValue(), 1e-6);
        assertEquals(1., getCriticalBranch(cb007, "007_DE-NL [CO1][DIR]", "2019-01-07T23:00Z/2019-01-08T11:00Z").getImaxFactor().doubleValue(), 1e-6);

        // -----
        // check Imax for a branch which has Imax instead of ImaxFactor
        // -----
        Collection<CriticalBranchType> cb003 = criticalBranches.get("003_FR-DE [CO1][DIR]");
        assertEquals(6000., getCriticalBranch(cb003, "003_FR-DE [CO1][DIR]_TATL", "2019-01-08T14:00Z/2019-01-08T15:00Z").getImaxA().doubleValue(), 1e-6);
        assertEquals(5000., getCriticalBranch(cb003, "003_FR-DE [CO1][DIR]_PATL", "2019-01-08T14:00Z/2019-01-08T15:00Z").getImaxA().doubleValue(), 1e-6);
        assertEquals(5000., getCriticalBranch(cb003, "003_FR-DE [CO1][DIR]", "2019-01-07T23:00Z/2019-01-08T11:00Z").getImaxA().doubleValue(), 1e-6);
        assertNull(getCriticalBranch(cb003, "003_FR-DE [CO1][DIR]_TATL", "2019-01-08T14:00Z/2019-01-08T15:00Z").getImaxFactor());

        // -----
        // check the associated RA for CO1 on a given time interval
        // -----

        String varId1213Co1 = getCriticalBranch(cb007, "007_DE-NL [CO1][DIR]_PATL", "2019-01-08T12:00Z/2019-01-08T13:00Z").getComplexVariantId();

        // variant exists, with correct time interval
        IndependantComplexVariant variant1213Co1 = getComplexVariant(dailyFbConstDocument, varId1213Co1);
        assertEquals("2019-01-08T12:00Z/2019-01-08T13:00Z", variant1213Co1.getTimeInterval().getV());

        // variant contains 3 actions: CRA_OPEN_NL1-NL3, CRA_OPEN_TR-FR1, CRA_PST_BE@16
        assertEquals(3, variant1213Co1.getActionsSet().size());
        assertTrue(variant1213Co1.getActionsSet().stream().anyMatch(as -> as.getName().equals("OPEN_NL1-NL3")));
        assertTrue(variant1213Co1.getActionsSet().stream().anyMatch(as -> as.getName().equals("OPEN_TR-FR1")));
        assertTrue(variant1213Co1.getActionsSet().stream().anyMatch(as -> as.getName().equals("CRA_PST_BE")));

        // several actions of differents TSO -> default TSO is XX
        assertEquals("XX", variant1213Co1.getTsoOrigin());

        // check tap of activated PST
        ActionType pst1213Co1 = variant1213Co1.getActionsSet().stream().filter(as -> as.getName().equals("CRA_PST_BE")).findAny().orElseThrow().getAction().get(0);
        assertNotNull(pst1213Co1);
        assertEquals("PSTTAP", pst1213Co1.getType());
        assertEquals("16", ((JAXBElement) pst1213Co1.getContent().get(3)).getValue());

        // -----
        // check the associated RA for CO2 on the same timestamp, they are different than the ones of CO1
        // -----
        String varId1213Co2 = getCriticalBranch(criticalBranches.values(), "017_FR12-FR32 [CO2][DIR]_PATL", "2019-01-08T12:00Z/2019-01-08T13:00Z").getComplexVariantId();

        // variant exists, with correct time interval
        IndependantComplexVariant variant1213Co2 = getComplexVariant(dailyFbConstDocument, varId1213Co2);
        assertEquals("2019-01-08T12:00Z/2019-01-08T13:00Z", variant1213Co2.getTimeInterval().getV());

        // variant contains 1 actions: CRA_PST_BE@-16
        assertEquals(1, variant1213Co2.getActionsSet().size());
        assertEquals("BE", variant1213Co2.getTsoOrigin());
        assertTrue(variant1213Co2.getActionsSet().stream().anyMatch(as -> as.getName().equals("CRA_PST_BE")));

        // check tap of activated PST
        ActionType pst1213Co2 = variant1213Co2.getActionsSet().stream().filter(as -> as.getName().equals("CRA_PST_BE")).findAny().orElseThrow().getAction().get(0);
        assertNotNull(pst1213Co2);
        assertEquals("PSTTAP", pst1213Co2.getType());
        assertEquals("-16", ((JAXBElement) pst1213Co2.getContent().get(3)).getValue());

        // -----
        // check a critical branch which:
        // - is duplicated in the nativeCrac (two versions depending on the timeStamp)
        // - is invalid for some timestamps (13-14 and 14-15h)
        // -----
        Collection<CriticalBranchType> cb020 = criticalBranches.get("020_FR12-FR32 [CO2][OPP]");
        assertEquals(7, cb020.size());

        // 1 per not requested interval, merged with intervals without CRA
        assertNotNull(getCriticalBranch(cb020, "020_FR12-FR32 [CO2][OPP]", "2019-01-07T23:00Z/2019-01-08T11:00Z"));
        assertNotNull(getCriticalBranch(cb020, "020_FR12-FR32 [CO2][OPP]", "2019-01-08T14:00Z/2019-01-08T23:00Z"));

        // 1 TATL per interval with CRAs, with same characteristics (two CB cannot be merged as iMaxFactor is changing, see below)
        assertNotNull(getCriticalBranch(cb020, "020_FR12-FR32 [CO2][OPP]_TATL", "2019-01-08T11:00Z/2019-01-08T12:00Z"));
        assertNotNull(getCriticalBranch(cb020, "020_FR12-FR32 [CO2][OPP]_TATL", "2019-01-08T12:00Z/2019-01-08T14:00Z"));

        // 1 PATL per hour with CRAs
        assertNotNull(getCriticalBranch(cb020, "020_FR12-FR32 [CO2][OPP]_PATL", "2019-01-08T11:00Z/2019-01-08T12:00Z"));
        assertNotNull(getCriticalBranch(cb020, "020_FR12-FR32 [CO2][OPP]_PATL", "2019-01-08T12:00Z/2019-01-08T13:00Z"));
        assertNotNull(getCriticalBranch(cb020, "020_FR12-FR32 [CO2][OPP]_PATL", "2019-01-08T13:00Z/2019-01-08T14:00Z"));

        // imaxFactor of the branch change between some timestamps
        // TATL: 1.15 before 12:00, 1.30 after  (the TATL cb cannot be clustered because of the change in Imax)
        assertEquals(1.15, getCriticalBranch(cb020, "020_FR12-FR32 [CO2][OPP]_TATL", "2019-01-08T11:00Z/2019-01-08T12:00Z").getImaxFactor().doubleValue(), 1e-6);
        assertEquals(1.3, getCriticalBranch(cb020, "020_FR12-FR32 [CO2][OPP]_TATL", "2019-01-08T12:00Z/2019-01-08T14:00Z").getImaxFactor().doubleValue(), 1e-6);

        // PATL: 1 before 12:00, 1.2 after
        assertEquals(1, getCriticalBranch(cb020, "020_FR12-FR32 [CO2][OPP]_PATL", "2019-01-08T11:00Z/2019-01-08T12:00Z").getImaxFactor().doubleValue(), 1e-6);
        assertEquals(1.2, getCriticalBranch(cb020, "020_FR12-FR32 [CO2][OPP]_PATL", "2019-01-08T12:00Z/2019-01-08T13:00Z").getImaxFactor().doubleValue(), 1e-6);

        // -----
        // check a VNEC (not imported by RAO because not a MNEC and not a CNEC, but should be exported)
        // -----
        Collection<CriticalBranchType> cb021 = criticalBranches.get("021_NO_MNEC_NO_CNEC [CO2]");
        assertEquals(6, cb021.size());

        // 1 per not requested interval / interval without CRA
        assertNotNull(getCriticalBranch(cb021, "021_NO_MNEC_NO_CNEC [CO2]", "2019-01-07T23:00Z/2019-01-08T11:00Z"));
        assertNotNull(getCriticalBranch(cb021, "021_NO_MNEC_NO_CNEC [CO2]", "2019-01-08T14:00Z/2019-01-08T23:00Z"));

        // 1 TATL per interval with CRAs
        assertNotNull(getCriticalBranch(cb021, "021_NO_MNEC_NO_CNEC [CO2]_TATL", "2019-01-08T11:00Z/2019-01-08T14:00Z"));

        // 1 PATL per hour with CRAs
        assertNotNull(getCriticalBranch(cb021, "021_NO_MNEC_NO_CNEC [CO2]_PATL", "2019-01-08T11:00Z/2019-01-08T12:00Z"));
        assertNotNull(getCriticalBranch(cb021, "021_NO_MNEC_NO_CNEC [CO2]_PATL", "2019-01-08T12:00Z/2019-01-08T13:00Z"));
        assertNotNull(getCriticalBranch(cb021, "021_NO_MNEC_NO_CNEC [CO2]_PATL", "2019-01-08T13:00Z/2019-01-08T14:00Z"));

        // VNECs have associated CRAs, even if they are not in the RAO -> the CRAs of their corresponding state
        assertEquals(varId1213Co2, getCriticalBranch(cb021, "021_NO_MNEC_NO_CNEC [CO2]_PATL", "2019-01-08T12:00Z/2019-01-08T13:00Z").getComplexVariantId());

        // -----
        // check the total number of complex variants and CriticalBranches
        // -----
        // 6 combinations of hours/CO with CRAs
        assertEquals(6, dailyFbConstDocument.getComplexVariants().getComplexVariant().size());

        // Check that criticalBranch 023 is exported only for the valid timestamps
        Collection<CriticalBranchType> cb023 = criticalBranches.get("023_NOT_FOR_ALL_TIMESTAMPS");
        assertEquals(1, cb023.size());
        assertNull(getCriticalBranch(cb023, "023_NOT_FOR_ALL_TIMESTAMPS", "2019-01-08T11:00Z/2019-01-08T15:00Z"));
        assertNotNull(getCriticalBranch(cb023, "023_NOT_FOR_ALL_TIMESTAMPS", "2019-01-08T12:00Z/2019-01-08T14:00Z"));
    }

    private Multimap<String, CriticalBranchType> buildCriticalBranchesMapPerOriginalId(FlowBasedConstraintDocument flowBasedConstraintDocument) {
        Multimap<String, CriticalBranchType> cbMultimap = ArrayListMultimap.create();
        flowBasedConstraintDocument.getCriticalBranches().getCriticalBranch().forEach(cb -> cbMultimap.put(cb.getOriginalId() != null ? cb.getOriginalId() : cb.getId(), cb));
        return cbMultimap;
    }

    private CriticalBranchType getCriticalBranch(Collection<CriticalBranchType> cbCollection, String id, String timeInterval) {
        return cbCollection.stream()
                .filter(cb -> cb.getId().equals(id))
                .filter(cb -> cb.getTimeInterval().getV().equals(timeInterval))
                .findAny().orElse(null);
    }

    private IndependantComplexVariant getComplexVariant(FlowBasedConstraintDocument flowBasedConstraintDocument, String id) {
        return flowBasedConstraintDocument.getComplexVariants().getComplexVariant().stream()
                .filter(cv -> cv.getId().equals(id))
                .findAny().orElse(null);
    }
}
