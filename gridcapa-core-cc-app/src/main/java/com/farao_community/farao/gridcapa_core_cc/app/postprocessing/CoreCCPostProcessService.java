/*
 * Copyright (c) 2026, RTE (http://www.rte-france.com)
 *  This Source Code Form is subject to the terms of the Mozilla Public
 *  License, v. 2.0. If a copy of the MPL was not distributed with this
 *  file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.farao_community.farao.gridcapa_core_cc.app.postprocessing;

import com.farao_community.farao.gridcapa_core_cc.api.resource.CoreCCFileResource;
import com.farao_community.farao.gridcapa_core_cc.api.resource.HourlyRaoResult;
import com.farao_community.farao.gridcapa_core_cc.api.resource.InternalCoreCCRequest;
import com.farao_community.farao.gridcapa_core_cc.app.entities.CgmsAndXmlHeader;
import com.farao_community.farao.gridcapa_core_cc.app.services.FileImporter;
import com.farao_community.farao.gridcapa_core_cc.app.util.CoreNetworkImporterWrapper;
import com.farao_community.farao.gridcapa_core_cc.app.util.RaoResultMerger;
import com.powsybl.commons.report.ReportNode;
import com.powsybl.glsk.commons.ZonalData;
import com.powsybl.iidm.network.Network;
import com.powsybl.openrao.data.crac.api.Crac;
import com.powsybl.openrao.data.crac.io.fbconstraint.FbConstraintCreationContext;
import com.powsybl.openrao.data.raoresult.api.RaoResult;
import com.powsybl.openrao.data.refprog.referenceprogram.ReferenceProgram;
import com.powsybl.openrao.raoapi.parameters.RaoParameters;
import com.powsybl.sensitivity.SensitivityVariableSet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.nio.file.Path;
import java.time.Instant;

/**
 * @author Vincent Bochet {@literal <vincent.bochet at rte-france.com>}
 */
@Service
public class CoreCCPostProcessService {
    private static final Logger LOGGER = LoggerFactory.getLogger(CoreCCPostProcessService.class);

    private final FileImporter fileImporter;
    private final FileExporterHelper fileExporterHelper;
    private final RaoResultMerger raoResultMerger;

    public CoreCCPostProcessService(final FileImporter fileImporter,
                                    final FileExporterHelper fileExporterHelper,
                                    final RaoResultMerger raoResultMerger) {
        this.fileImporter = fileImporter;
        this.fileExporterHelper = fileExporterHelper;
        this.raoResultMerger = raoResultMerger;
    }

    public void convertAndSaveReceivedRaoResult(final InternalCoreCCRequest coreCCRequest) {
        // At this point, coreCCRequest necessarily contains a non-null continentalHourlyRaoResult and, is SEM is enabled, a non-null semHourlyRaoResult
        // Also, at least one hourlyRaoResult has a SUCCESS status (situation with no SUCCESS status has been handled previously)

        final CoreCCPostProcessingData postProcessingData = new CoreCCPostProcessingData();
        postProcessingData.setRequest(coreCCRequest);
        try {
            initialisePostProcessingData(postProcessingData);
            initialiseRaoResultInPostProcessingData(coreCCRequest, postProcessingData);

            fileExporterHelper.exportCneToMinio(postProcessingData);
            fileExporterHelper.exportNetworkToMinio(postProcessingData);
            fileExporterHelper.exportRaoResultToMinio(postProcessingData);
            fileExporterHelper.exportMetadataToMinio(postProcessingData);
        } catch (final Exception e) {
            // TODO How should we handle exceptions from outputs export in an area-unrelated manner?
            //no throwing exception, just save cause and pass to next timestamp
            final HourlyRaoResult hourlyRaoResult = coreCCRequest.getContinentalHourlyRaoResult();
            final String errorMessage = String.format("Error occurred while post-processing RAO outputs for timestamp: %s. Cause: %s", hourlyRaoResult.getRaoRequestInstant(), e);
            LOGGER.error(errorMessage, e);
            hourlyRaoResult.setStatus(HourlyRaoResult.Status.FAILURE);
            // TODO Apparently, the only useful information here is the status of the hourlyRaoResult. ErrorCode and message are never used after this point.
            hourlyRaoResult.setErrorCode(HourlyRaoResult.ErrorCode.RAO_FAILURE);
            hourlyRaoResult.setErrorMessage(errorMessage);
        }
    }

    private void initialisePostProcessingData(final CoreCCPostProcessingData postProcessingData) {
        final InternalCoreCCRequest coreCCRequest = postProcessingData.getRequest();

        // Full AC network will be needed for network export
        final Network acNetwork = importNetworkFromZip(coreCCRequest.getCgm(), postProcessingData.getRaoRequestInstant());
        final String initialAcNetworkVariantId = acNetwork.getVariantManager().getWorkingVariantId();
        postProcessingData.setAcNetwork(acNetwork);
        postProcessingData.setInitialAcNetworkVariantId(initialAcNetworkVariantId);

        // Full DC network will be needed to import CBCORA and GLSK in case of RAO results merging
        final Network dcNetwork = importNetworkFromZip(coreCCRequest.getDcCgm(), postProcessingData.getRaoRequestInstant());
        postProcessingData.setDcNetwork(dcNetwork);

        // Area-related CRACs are needed for RAO results import and/or RAO results merging
        // TODO Check what happens if both areas are enabled but computation fails for one of them
        // TODO Same question if pre-processing fails
        final Network continentalNetwork = fileImporter.importNetwork(postProcessingData.getRequest().getContinentalHourlyRaoRequest().getNetworkFileUrl());
        final FbConstraintCreationContext continentalFbConstraintCreationContext = fileImporter.importCbcora(
            coreCCRequest.getCbcora().getUrl(),
            coreCCRequest.getTimestamp(),
            continentalNetwork
        );
        postProcessingData.setContinentalCracCreationContext(continentalFbConstraintCreationContext);

        // TODO same check as above
        if (coreCCRequest.isSemEnabled()) {
            final Network semNetwork = fileImporter.importNetwork(postProcessingData.getRequest().getSemHourlyRaoRequest().getNetworkFileUrl());
            final FbConstraintCreationContext semFbConstraintCreationContext = fileImporter.importCbcora(
                coreCCRequest.getCbcora().getUrl(),
                coreCCRequest.getTimestamp(),
                semNetwork
            );
            postProcessingData.setSemCracCreationContext(semFbConstraintCreationContext);
        }

        final RaoParameters raoParameters = fileImporter.importRaoParameters(coreCCRequest.getRaoParameters().getUrl());
        postProcessingData.setRaoParameters(raoParameters);
    }

    private Network importNetworkFromZip(final CoreCCFileResource fileResource, final String raoRequestInstant) {
        final CgmsAndXmlHeader cgmsAndXmlHeader = fileImporter.importCgmsZip(fileResource);
        final Path cgmPath = cgmsAndXmlHeader.getNetworkPath(Instant.parse(raoRequestInstant));
        return CoreNetworkImporterWrapper.loadNetwork(cgmPath);
    }

    private void initialiseRaoResultInPostProcessingData(final InternalCoreCCRequest coreCCRequest,
                                                         final CoreCCPostProcessingData postProcessingData) {
        final boolean semEnabled = coreCCRequest.isSemEnabled();
        final boolean continentalRaoSucceeded = coreCCRequest.getContinentalHourlyRaoResult().getStatus() == HourlyRaoResult.Status.SUCCESS;
        // SemHourlyRaoResult exists only if SEM is enabled
        final boolean semRaoSucceeded = semEnabled && coreCCRequest.getSemHourlyRaoResult().getStatus() == HourlyRaoResult.Status.SUCCESS;

        final RaoResult raoResult;
        if (semEnabled && continentalRaoSucceeded && semRaoSucceeded) {
            LOGGER.info("RAO result available for continental and SEM areas");
            // If SEM is enabled and both SEM and continental RAO succeeded, the RAO results must be merged

            // Full CBCORA is needed for the RAO results merging. Therefore, CBCORA must be imported using full DC Network.
            final FbConstraintCreationContext fbConstraintCreationContext = fileImporter.importCbcora(
                coreCCRequest.getCbcora().getUrl(),
                coreCCRequest.getTimestamp(),
                postProcessingData.getDcNetwork()
            );
            postProcessingData.setCracCreationContext(fbConstraintCreationContext);

            raoResult = mergeRaoResults(postProcessingData);
        } else if (semEnabled && !continentalRaoSucceeded) {
            LOGGER.info("RAO result available for SEM area only");
            // If SEM is enabled and only SEM RAO succeeded (continental RAO failed),
            // the RAO result that will be used for outputs generation is the one from SEM computation

            // Only area to consider is SEM area, so full CBCORA is SEM CBCORA
            postProcessingData.setCracCreationContext(postProcessingData.getSemCracCreationContext());

            final String semRaoResultFileUrl = postProcessingData.getRequest().getSemHourlyRaoResult().getRaoResultFileUrl();
            final Crac semCrac = postProcessingData.getCrac();
            raoResult = fileImporter.importRaoResult(semRaoResultFileUrl, semCrac);
        } else {
            LOGGER.info("RAO result available for continental area only");
            // If SEM is disabled or if only continental RAO succeeded,
            // the RAO result that will be used for outputs generation is the one from continental computation

            // Only area to consider is continental area, so full CBCORA is continental CBCORA
            postProcessingData.setCracCreationContext(postProcessingData.getContinentalCracCreationContext());

            final String continentalRaoResultFileUrl = postProcessingData.getRequest().getContinentalHourlyRaoResult().getRaoResultFileUrl();
            final Crac continentalCrac = postProcessingData.getCrac();
            raoResult = fileImporter.importRaoResult(continentalRaoResultFileUrl, continentalCrac);
        }
        postProcessingData.setRaoResult(raoResult);
    }

    private RaoResult mergeRaoResults(final CoreCCPostProcessingData postProcessingData) {
        final InternalCoreCCRequest coreCCRequest = postProcessingData.getRequest();
        final HourlyRaoResult continentalHourlyRaoResult = coreCCRequest.getContinentalHourlyRaoResult();
        final HourlyRaoResult semHourlyRaoResult = coreCCRequest.getSemHourlyRaoResult();

        final RaoResult continentalRaoResult = fileImporter.importRaoResult(continentalHourlyRaoResult.getRaoResultFileUrl(), postProcessingData.getContinentalCrac());
        final RaoResult semRaoResult = fileImporter.importRaoResult(semHourlyRaoResult.getRaoResultFileUrl(), postProcessingData.getSemCrac());

        // ReferenceProgram and GLSK are required for RaoResult merging but we don't need them for any other action
        // So we only import those files here without adding them to the postProcessingData
        final ReferenceProgram referenceProgram = fileImporter.importReferenceProgram(coreCCRequest.getRefProg(), coreCCRequest.getTimestamp());
        final ZonalData<SensitivityVariableSet> glskProvider = fileImporter.importGlsk(coreCCRequest.getGlsk().getUrl(), coreCCRequest.getTimestamp(), postProcessingData.getDcNetwork());

        return raoResultMerger.mergeRaoResults(continentalRaoResult,
                                               semRaoResult,
                                               postProcessingData.getAcNetwork(),
                                               postProcessingData.getCrac(), // At this point, this is the full CRAC
                                               referenceProgram,
                                               glskProvider,
                                               postProcessingData.getRaoParameters(),
                                               ReportNode.NO_OP,
                                               postProcessingData.getInitialAcNetworkVariantId());
    }
}
