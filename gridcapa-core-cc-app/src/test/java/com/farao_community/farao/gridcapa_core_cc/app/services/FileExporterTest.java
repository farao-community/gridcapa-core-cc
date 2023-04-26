/*
 * Copyright (c) 2021, RTE (http://www.rte-france.com)
 *  This Source Code Form is subject to the terms of the Mozilla Public
 *  License, v. 2.0. If a copy of the MPL was not distributed with this
 *  file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package com.farao_community.farao.gridcapa_core_cc.app.services;

import com.farao_community.farao.data.crac_api.Crac;
import com.farao_community.farao.data.crac_api.RemedialAction;
import com.farao_community.farao.data.crac_api.State;
import com.farao_community.farao.data.crac_creation.creator.fb_constraint.crac_creator.FbConstraintCreationContext;
import com.farao_community.farao.data.crac_impl.CracImpl;
import com.farao_community.farao.gridcapa_core_cc.app.limiting_branch.LimitingBranchResult;
import com.farao_community.farao.gridcapa_core_cc.app.services.results_export.ResultType;
import com.farao_community.farao.gridcapa_core_cc.app.study_point.StudyPointResult;
import com.farao_community.farao.gridcapa_core_cc.api.resource.CoreCCRequest;
import com.farao_community.farao.minio_adapter.starter.MinioAdapter;
import com.farao_community.farao.rao_api.parameters.RaoParameters;
import com.powsybl.iidm.network.Network;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;

import java.io.IOException;
import java.io.InputStream;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * @author Ameni Walha {@literal <ameni.walha at rte-france.com>}
 */
@SpringBootTest
class FileExporterTest {

    @Autowired
    private FileExporter fileExporter;
    @Autowired
    private FileImporter fileImporter;

    @MockBean
    private MinioAdapter minioAdapter;

    private final OffsetDateTime dateTime = OffsetDateTime.parse("2021-07-22T22:30Z");

    @Test
    void exportMainAndRexStudyPointResultTest() throws IOException {
        Mockito.when(minioAdapter.generatePreSignedUrl(Mockito.any())).thenReturn("resultUrl");
        List<StudyPointResult> studyPointsResult = new ArrayList<>();
        StudyPointResult studyPointResult = mockStudyPointResult();
        studyPointsResult.add(studyPointResult);
        CoreCCRequest coreCCRequest = Mockito.mock(CoreCCRequest.class);
        Mockito.when(coreCCRequest.getTimestamp()).thenReturn(dateTime);
        Mockito.when(coreCCRequest.getLaunchedAutomatically()).thenReturn(true);
        String resultUrl = fileExporter.exportStudyPointResult(studyPointsResult, coreCCRequest, null).get(ResultType.MAIN_RESULT);
        ArgumentCaptor<InputStream> argumentCaptor = ArgumentCaptor.forClass(InputStream.class);
        Mockito.verify(minioAdapter, Mockito.times(3)).uploadOutputForTimestamp(Mockito.any(), argumentCaptor.capture(), Mockito.any(), Mockito.any(), Mockito.any());
        List<InputStream> resultsBaos = argumentCaptor.getAllValues();
        assertEquals("Period;Vertice ID;Branch ID;Branch Status;RAM before;RAM after\r\n;;;;0;0\r\n", new String(resultsBaos.get(0).readAllBytes()));
        assertEquals("Period;Vertice ID;Branch ID;Branch Name;Outage Name;Branch Status;RAM before;RAM after;flow before;flow after\r\n;;;;;;0;0;0;0\r\n", new String(resultsBaos.get(1).readAllBytes()));
        assertEquals("resultUrl", resultUrl);
    }

    @Test
    void exportRemedialActionsStudyPointResultTest() throws IOException {
        final OffsetDateTime dateTime = OffsetDateTime.parse("2023-01-18T00:30Z");
        final String directory = "/rao-result-remedial-action";

        Mockito.when(minioAdapter.generatePreSignedUrl(Mockito.any())).thenReturn("resultUrl");
        List<StudyPointResult> studyPointsResult = List.of(mockStudyPointResultWithRemedialAction());
        CoreCCRequest coreCCRequest = Mockito.mock(CoreCCRequest.class);
        Mockito.when(coreCCRequest.getTimestamp()).thenReturn(dateTime);
        Mockito.when(coreCCRequest.getLaunchedAutomatically()).thenReturn(true);

        Network network = Network.read("network.uct", getClass().getResourceAsStream(directory + "/network.uct"));
        FbConstraintCreationContext fbConstraintCreationContext = fileImporter.importCrac(getClass().getResource(directory + "/crac.xml").toExternalForm(), dateTime, network);

        fileExporter.exportStudyPointResult(studyPointsResult, coreCCRequest, fbConstraintCreationContext);

        ArgumentCaptor<InputStream> argumentCaptor = ArgumentCaptor.forClass(InputStream.class);
        Mockito.verify(minioAdapter, Mockito.times(3)).uploadOutputForTimestamp(Mockito.any(), argumentCaptor.capture(), Mockito.any(), Mockito.any(), Mockito.any());
        List<InputStream> resultsBaos = argumentCaptor.getAllValues();
        assertEquals("Period;Vertice ID;State;RA ID;RA name\r\nperiod;vertice ID;N-1 DE - FR ELEMT 2;RemedialActionId;RemedialActionName\r\n", new String(resultsBaos.get(2).readAllBytes()));
    }

    @Test
    void exportRexOnlyStudyPointResultTest() throws IOException {
        Mockito.when(minioAdapter.generatePreSignedUrl(Mockito.any())).thenReturn("resultUrl");
        List<StudyPointResult> studyPointsResult = new ArrayList<>();
        StudyPointResult studyPointResult = mockStudyPointResult();
        studyPointsResult.add(studyPointResult);
        CoreCCRequest coreCCRequest = Mockito.mock(CoreCCRequest.class);
        Mockito.when(coreCCRequest.getTimestamp()).thenReturn(dateTime);
        Mockito.when(coreCCRequest.getLaunchedAutomatically()).thenReturn(false);
        Map<ResultType, String> resultUrls = fileExporter.exportStudyPointResult(studyPointsResult, coreCCRequest, null);
        assertNull(resultUrls.get(ResultType.MAIN_RESULT));
        String resultUrl = resultUrls.get(ResultType.REX_RESULT);
        ArgumentCaptor<InputStream> argumentCaptor = ArgumentCaptor.forClass(InputStream.class);
        Mockito.verify(minioAdapter, Mockito.times(2)).uploadOutputForTimestamp(Mockito.any(), argumentCaptor.capture(), Mockito.any(), Mockito.any(), Mockito.any());
        List<InputStream> resultsBaos = argumentCaptor.getAllValues();
        assertEquals("Period;Vertice ID;Branch ID;Branch Name;Outage Name;Branch Status;RAM before;RAM after;flow before;flow after\r\n;;;;;;0;0;0;0\r\n", new String(resultsBaos.get(0).readAllBytes()));
        assertEquals("resultUrl", resultUrl);
    }

    private StudyPointResult mockStudyPointResult() {
        State state = Mockito.mock(State.class);
        LimitingBranchResult limitingBranchResult = Mockito.mock(LimitingBranchResult.class);
        Mockito.when(limitingBranchResult.getBranchStatus()).thenReturn("");
        Mockito.when(limitingBranchResult.getCriticalBranchId()).thenReturn("");
        Mockito.when(limitingBranchResult.getCriticalBranchName()).thenReturn("");
        Mockito.when(limitingBranchResult.getFlowAfter()).thenReturn(0.0);
        Mockito.when(limitingBranchResult.getFlowBefore()).thenReturn(0.0);
        Mockito.when(limitingBranchResult.getRamBefore()).thenReturn(0.0);
        Mockito.when(limitingBranchResult.getRamAfter()).thenReturn(0.0);
        Mockito.when(limitingBranchResult.getRemedialActions()).thenReturn(new HashSet<>());
        Mockito.when(limitingBranchResult.getState()).thenReturn(state);
        Mockito.when(limitingBranchResult.getVerticeId()).thenReturn("");
        List<LimitingBranchResult> limitingBranchResults = Collections.singletonList(limitingBranchResult);
        StudyPointResult studyPointResult = Mockito.mock(StudyPointResult.class);
        Mockito.when(studyPointResult.getListLimitingBranchResult()).thenReturn(limitingBranchResults);
        return studyPointResult;
    }

    private StudyPointResult mockStudyPointResultWithRemedialAction() {
        State state = Mockito.mock(State.class);
        LimitingBranchResult limitingBranchResult = Mockito.mock(LimitingBranchResult.class);
        Mockito.when(limitingBranchResult.getBranchStatus()).thenReturn("branchStatus");
        Mockito.when(limitingBranchResult.getCriticalBranchId()).thenReturn("FR_CBCO_00001");
        Mockito.when(limitingBranchResult.getCriticalBranchName()).thenReturn("criticalBranchName");
        Mockito.when(limitingBranchResult.getFlowAfter()).thenReturn(0.0);
        Mockito.when(limitingBranchResult.getFlowBefore()).thenReturn(0.0);
        Mockito.when(limitingBranchResult.getRamBefore()).thenReturn(0.0);
        Mockito.when(limitingBranchResult.getRamAfter()).thenReturn(0.0);
        Mockito.when(limitingBranchResult.getState()).thenReturn(state);
        Mockito.when(limitingBranchResult.getVerticeId()).thenReturn("verticeId");
        RemedialAction<?> remedialActionMock = Mockito.mock(RemedialAction.class);
        Mockito.when(remedialActionMock.getId()).thenReturn("RemedialActionId");
        Mockito.when(remedialActionMock.getName()).thenReturn("RemedialActionName");
        Mockito.when(limitingBranchResult.getRemedialActions()).thenReturn(Set.of(remedialActionMock));
        StudyPointResult studyPointResult = Mockito.mock(StudyPointResult.class);
        Mockito.when(studyPointResult.getListLimitingBranchResult()).thenReturn(List.of(limitingBranchResult));
        Mockito.when(studyPointResult.getPeriod()).thenReturn("period");
        Mockito.when(studyPointResult.getId()).thenReturn("vertice ID");
        return studyPointResult;
    }

    @Test
    void saveRaoParametersTest() {
        RaoParameters raoParameters = RaoParameters.load();
        Mockito.when(minioAdapter.generatePreSignedUrl(Mockito.any())).thenReturn("raoParametersUrl");
        String raoParametersUrl = fileExporter.saveRaoParametersAndGetUrl(raoParameters);
        Mockito.verify(minioAdapter, Mockito.times(1)).uploadArtifact(Mockito.any(), Mockito.any(InputStream.class));
        assertEquals("raoParametersUrl", raoParametersUrl);
    }

    @Test
    void saveCracInJsonFormatTest() {
        Crac crac = new CracImpl("id");
        Mockito.when(minioAdapter.generatePreSignedUrl(Mockito.any())).thenReturn("cracUrl");
        String cracUrl = fileExporter.saveCracInJsonFormat(crac, dateTime);
        Mockito.verify(minioAdapter, Mockito.times(1)).uploadArtifact(Mockito.any(), Mockito.any(InputStream.class));
        assertEquals("cracUrl", cracUrl);
    }

    @Test
    void saveShiftedCgmWithPraTest() {
        String raoDirectory = "/rao-result";
        Network network = Network.read("network.uct", getClass().getResourceAsStream(raoDirectory + "/network.uct"));
        Mockito.when(minioAdapter.generatePreSignedUrl(Mockito.any())).thenReturn("cgmWithPraUrl");
        String cgmWithPraUrl = fileExporter.saveShiftedCgmWithPra(network, "test");
        Mockito.verify(minioAdapter, Mockito.times(1)).uploadArtifact(Mockito.any(), Mockito.any(InputStream.class));
        assertEquals("cgmWithPraUrl", cgmWithPraUrl);
    }
}