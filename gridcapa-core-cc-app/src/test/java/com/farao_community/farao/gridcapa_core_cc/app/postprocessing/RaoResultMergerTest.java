/*
 * Copyright (c) 2026, RTE (http://www.rte-france.com)
 *  This Source Code Form is subject to the terms of the Mozilla Public
 *  License, v. 2.0. If a copy of the MPL was not distributed with this
 *  file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package com.farao_community.farao.gridcapa_core_cc.app.postprocessing;

import com.farao_community.farao.gridcapa.task_manager.api.TaskParameterDto;
import com.farao_community.farao.gridcapa_core_cc.api.resource.CoreCCFileResource;
import com.farao_community.farao.gridcapa_core_cc.api.resource.CoreCCRequest;
import com.farao_community.farao.gridcapa_core_cc.api.resource.InternalCoreCCRequest;
import com.farao_community.farao.gridcapa_core_cc.app.entities.CgmsAndXmlHeader;
import com.farao_community.farao.gridcapa_core_cc.app.services.FileImporter;
import com.farao_community.farao.gridcapa_core_cc.app.services.UrlValidationService;
import com.farao_community.farao.gridcapa_core_cc.app.util.CoreNetworkImporterWrapper;
import com.farao_community.farao.minio_adapter.starter.MinioAdapter;
import com.powsybl.commons.report.ReportNode;
import com.powsybl.glsk.commons.ZonalData;
import com.powsybl.iidm.network.Network;
import com.powsybl.openrao.data.crac.api.Crac;
import com.powsybl.openrao.data.crac.io.fbconstraint.FbConstraintCreationContext;
import com.powsybl.openrao.data.raoresult.api.RaoResult;
import com.powsybl.openrao.data.refprog.referenceprogram.ReferenceProgram;
import com.powsybl.openrao.raoapi.parameters.RaoParameters;
import com.powsybl.openrao.virtualhubs.VirtualHubsConfiguration;
import com.powsybl.sensitivity.SensitivityVariableSet;
import org.apache.commons.lang3.reflect.FieldUtils;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;

/**
 * @author Vincent Bochet {@literal <vincent.bochet at rte-france.com>}
 */
class RaoResultMergerTest {

    private final RaoResultMerger raoResultMerger = new RaoResultMerger(LoggerFactory.getLogger(RaoResultMergerTest.class));

    @InjectMocks
    private FileImporter fileImporter;
    @Mock
    private MinioAdapter minioAdapter;
    @Mock
    private UrlValidationService urlValidationService;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
    }

    private RaoResult getRaoResult(final String networkFileUrl,
                                   final String cracFileUrl,
                                   final String raoResultFileUrl) {
        final Network network = fileImporter.importNetwork(networkFileUrl);
        final Crac crac = fileImporter.importCrac(cracFileUrl, network);

        return fileImporter.importRaoResult(raoResultFileUrl, crac);
    }

    private Network importNetworkFromZip(final CoreCCFileResource fileResource, final String raoRequestInstant) {
        final CgmsAndXmlHeader cgmsAndXmlHeader = fileImporter.importCgmsZip(fileResource);
        final Path cgmPath = cgmsAndXmlHeader.getNetworkPath(Instant.parse(raoRequestInstant));
        return CoreNetworkImporterWrapper.loadNetwork(cgmPath);
    }

    @Test
    void mergeRaoResultsTest() throws IllegalAccessException {
        final CoreCCFileResource cgmFileResource = new CoreCCFileResource(
            "20260326-F119-v1-17XTSO-CS------W-to-22XCORESO------S.zip",
            getClass().getResource("/sem-integration/rao-results-merging/20260326-F119-v1-17XTSO-CS------W-to-22XCORESO------S.zip").getPath());
        final CoreCCFileResource dcCgmFileResource = new CoreCCFileResource(
            "20260326-F139-v1-17XTSO-CS------W-to-22XCORESO------S.zip",
            getClass().getResource("/sem-integration/rao-results-merging/20260326-F139-v1-17XTSO-CS------W-to-22XCORESO------S.zip").getPath());
        final CoreCCFileResource cbcoraFileResource = new CoreCCFileResource(
            "20260326-F301-v1-17XTSO-CS------W-to-22XCORESO------S.xml",
            getClass().getResource("/sem-integration/rao-results-merging/20260326-F301-v1-17XTSO-CS------W-to-22XCORESO------S.xml").getPath());
        final CoreCCFileResource glskFileResource = new CoreCCFileResource(
            "20260326-F319-v1-17XTSO-CS------W-to-22XCORESO------S.xml",
            getClass().getResource("/sem-integration/rao-results-merging/20260326-F319-v1-17XTSO-CS------W-to-22XCORESO------S.xml").getPath());
        final CoreCCFileResource refProgFileResource = new CoreCCFileResource(
            "20260326-F120-v1-17XTSO-CS------W-to-22XCORESO------S.xml",
            getClass().getResource("/sem-integration/rao-results-merging/20260326-F120-v1-17XTSO-CS------W-to-22XCORESO------S.xml").getPath());
        final CoreCCFileResource virtualHubsFileResource = new CoreCCFileResource(
            "20260326-F327-v1-17XTSO-CS------W-to-22XCORESO------S.xml",
            getClass().getResource("/sem-integration/rao-results-merging/20260326-F327-v1-17XTSO-CS------W-to-22XCORESO------S.xml").getPath());
        final TaskParameterDto taskParameterDto = new TaskParameterDto("USE_DC_CGM_INPUT", "BOOLEAN", "TRUE", "TRUE");
        final CoreCCRequest coreCCRequestEntity = new CoreCCRequest(
            "id", "current RunID",
            OffsetDateTime.now(),
            cgmFileResource,
            dcCgmFileResource,
            cbcoraFileResource,
            glskFileResource,
            refProgFileResource,
            null,
            null,
            List.of(taskParameterDto));
        final InternalCoreCCRequest coreCCRequest = new InternalCoreCCRequest(coreCCRequestEntity);
        coreCCRequest.setSemEnabled(true);

        final String continentalNetworkPath = "/sem-integration/rao-results-merging/networks/continental_20260325_2300.xiidm";
        final String continentalNetworkFileUrl = getClass().getResource(continentalNetworkPath).getPath().toString();
        Mockito.when(minioAdapter.getFile(continentalNetworkFileUrl)).thenAnswer(x -> getClass().getResourceAsStream(continentalNetworkPath));
        final String semNetworkPath = "/sem-integration/rao-results-merging/networks/sem_20260325_2300.xiidm";
        final String semNetworkFileUrl = getClass().getResource(semNetworkPath).getPath().toString();
        Mockito.when(minioAdapter.getFile(semNetworkFileUrl)).thenAnswer(x -> getClass().getResourceAsStream(semNetworkPath));

        final String continentalCracPath = "/sem-integration/rao-results-merging/cracs/continental_20260325_2300.json";
        final String continentalCracFileUrl = getClass().getResource(continentalCracPath).getPath().toString();
        Mockito.when(minioAdapter.getFile(continentalCracFileUrl)).thenAnswer(x -> getClass().getResourceAsStream(continentalCracPath));
        final String semCracPath = "/sem-integration/rao-results-merging/cracs/sem_20260325_2300.json";
        final String semCracFileUrl = getClass().getResource(semCracPath).getPath().toString();
        Mockito.when(minioAdapter.getFile(semCracFileUrl)).thenAnswer(x -> getClass().getResourceAsStream(semCracPath));

        final String continentalRaoResultPath = "/sem-integration/rao-results-merging/hourly_rao_results/continental/raoResult.json";
        final String continentalRaoResultFileUrl = getClass().getResource(continentalRaoResultPath).getPath().toString();
        Mockito.when(urlValidationService.openUrlStream(continentalRaoResultFileUrl)).thenAnswer(x -> getClass().getResourceAsStream(continentalRaoResultPath));
        final String semRaoResultPath = "/sem-integration/rao-results-merging/hourly_rao_results/sem/raoResult.json";
        final String semRaoResultFileUrl = getClass().getResource(semRaoResultPath).getPath().toString();
        Mockito.when(urlValidationService.openUrlStream(semRaoResultFileUrl)).thenAnswer(x -> getClass().getResourceAsStream(semRaoResultPath));

        final String raoParametersPath = "/sem-integration/rao-results-merging/raoParameters.json";
        final String raoParametersFileUrl = getClass().getResource(raoParametersPath).getPath().toString();
        Mockito.when(minioAdapter.getFile(raoParametersFileUrl)).thenAnswer(x -> getClass().getResourceAsStream(raoParametersPath));

        final String virtualHubsPath = "/sem-integration/rao-results-merging/20260326-F327-v1-17XTSO-CS------W-to-22XCORESO------S.xml";
        final String virtualHubsFileUrl = getClass().getResource(virtualHubsPath).getPath().toString();
        Mockito.when(urlValidationService.openUrlStream(virtualHubsFileUrl)).thenAnswer(x -> getClass().getResourceAsStream(virtualHubsPath));

        final String cbcoraPath = "/sem-integration/rao-results-merging/20260326-F301-v1-17XTSO-CS------W-to-22XCORESO------S.xml";
        final String cbcoraFileUrl = getClass().getResource(cbcoraPath).getPath().toString();
        Mockito.when(urlValidationService.openUrlStream(cbcoraFileUrl)).thenAnswer(x -> getClass().getResourceAsStream(cbcoraPath));

        final String refProgPath = "/sem-integration/rao-results-merging/20260326-F120-v1-17XTSO-CS------W-to-22XCORESO------S.xml";
        final String refProgFileUrl = getClass().getResource(refProgPath).getPath().toString();
        Mockito.when(urlValidationService.openUrlStream(refProgFileUrl)).thenAnswer(x -> getClass().getResourceAsStream(refProgPath));

        final String glskPath = "/sem-integration/rao-results-merging/20260326-F319-v1-17XTSO-CS------W-to-22XCORESO------S.xml";
        final String glskFileUrl = getClass().getResource(glskPath).getPath().toString();
        Mockito.when(urlValidationService.openUrlStream(glskFileUrl)).thenAnswer(x -> getClass().getResourceAsStream(glskPath));

        final String cgmZipPath = "/sem-integration/rao-results-merging/20260326-F119-v1-17XTSO-CS------W-to-22XCORESO------S.zip";
        final String cgmZipFileUrl = getClass().getResource(cgmZipPath).getPath().toString();
        Mockito.when(urlValidationService.openUrlStream(cgmZipFileUrl)).thenAnswer(x -> getClass().getResourceAsStream(cgmZipPath));

        final String dcCgmZipPath = "/sem-integration/rao-results-merging/20260326-F139-v1-17XTSO-CS------W-to-22XCORESO------S.zip";
        final String dcCgmZipFileUrl = getClass().getResource(dcCgmZipPath).getPath().toString();
        Mockito.when(urlValidationService.openUrlStream(dcCgmZipFileUrl)).thenAnswer(x -> getClass().getResourceAsStream(dcCgmZipPath));

        final RaoResult continentalRaoResult = getRaoResult(continentalNetworkFileUrl,
                                                            continentalCracFileUrl,
                                                            continentalRaoResultFileUrl);
        final RaoResult semRaoResult = getRaoResult(semNetworkFileUrl,
                                                    semCracFileUrl,
                                                    semRaoResultFileUrl);
        final Network fullAcNetwork = importNetworkFromZip(cgmFileResource, "2026-03-25T23:00:00Z");
        final Network fullDcNetwork = importNetworkFromZip(dcCgmFileResource, "2026-03-25T23:00:00Z");
        final VirtualHubsConfiguration virtualHubsConfiguration = fileImporter.importVirtualHubs(virtualHubsFileResource);
        final FbConstraintCreationContext fbConstraintCreationContext = fileImporter.importCbcora(cbcoraFileUrl, OffsetDateTime.parse("2026-03-25T23:00:00Z"), fullDcNetwork, virtualHubsConfiguration);
        final Crac fullCrac = fbConstraintCreationContext.getCrac();
        final ReferenceProgram refProg = fileImporter.importReferenceProgram(refProgFileResource, OffsetDateTime.parse("2026-03-25T23:00:00Z"));
        final ZonalData<SensitivityVariableSet> glskProvider = fileImporter.importGlsk(glskFileUrl, OffsetDateTime.parse("2026-03-25T23:00:00Z"), fullDcNetwork);
        final RaoParameters raoParameters = fileImporter.importRaoParameters(raoParametersFileUrl);
        final ReportNode reportNode = ReportNode.NO_OP;
        final String variantIdToWorkOn = fullAcNetwork.getVariantManager().getWorkingVariantId();

        FieldUtils.writeDeclaredField(raoResultMerger, "mergeParallelization", 1, true);

        final RaoResult mergedRaoResults = raoResultMerger.mergeRaoResults(
            continentalRaoResult,
            semRaoResult,
            fullAcNetwork,
            fullCrac,
            refProg,
            glskProvider,
            raoParameters,
            reportNode,
            variantIdToWorkOn
        );

        Assertions.assertThat(mergedRaoResults).isNotNull();
        Assertions.assertThat(mergedRaoResults.getActivatedRangeActionsDuringState(fullCrac.getPreventiveState()))
            .containsAll(continentalRaoResult.getActivatedRangeActionsDuringState(fullCrac.getPreventiveState()))
            .containsAll(semRaoResult.getActivatedRangeActionsDuringState(fullCrac.getPreventiveState()));
        Assertions.assertThat(mergedRaoResults.getActivatedNetworkActionsDuringState(fullCrac.getPreventiveState()))
            .containsAll(continentalRaoResult.getActivatedNetworkActionsDuringState(fullCrac.getPreventiveState()))
            .containsAll(semRaoResult.getActivatedNetworkActionsDuringState(fullCrac.getPreventiveState()));
//        Assertions.assertThat(mergedRaoResults.getOptimizedTapsOnState(fullCrac.getPreventiveState()))
//            .containsAllEntriesOf(continentalRaoResult.getOptimizedTapsOnState(fullCrac.getPreventiveState()))
//            .containsAllEntriesOf(semRaoResult.getOptimizedTapsOnState(fullCrac.getPreventiveState()));
    }
}
