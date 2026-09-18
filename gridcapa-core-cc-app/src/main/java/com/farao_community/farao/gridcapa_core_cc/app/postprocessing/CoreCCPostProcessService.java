/*
 * Copyright (c) 2026, RTE (http://www.rte-france.com)
 *  This Source Code Form is subject to the terms of the Mozilla Public
 *  License, v. 2.0. If a copy of the MPL was not distributed with this
 *  file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.farao_community.farao.gridcapa_core_cc.app.postprocessing;

import com.farao_community.farao.gridcapa_core_cc.api.resource.CoreCCFileResource;
import com.farao_community.farao.gridcapa_core_cc.api.resource.HourlyRaoRequest;
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
            hourlyRaoResult.setErrorCode(HourlyRaoResult.ErrorCode.RAO_FAILURE);
            hourlyRaoResult.setErrorMessage(errorMessage);
        }
    }

    private void initialisePostProcessingData(final CoreCCPostProcessingData postProcessingData) {
        final InternalCoreCCRequest coreCCRequest = postProcessingData.getRequest();

        // Full AC network will be needed for network export
        final Network acNetwork = importNetworkFromZip(coreCCRequest.getCgm(), postProcessingData.getRaoRequestInstant());
        final String initialAcNetworkVariantId = acNetwork.getVariantManager().getWorkingVariantId();

        // Full DC network will be needed to import CBCORA (and GLSK at RAO results merging step)
        final Network dcNetwork = importNetworkFromZip(coreCCRequest.getDcCgm(), postProcessingData.getRaoRequestInstant());

        // Full CRAC is also needed for the RAO results merging. Therefore, CRAC must be imported using full DC Network.
        // We can't use already imported JSON CRACs as they are only limited to continental or SEM area.
        final FbConstraintCreationContext fbConstraintCreationContext = fileImporter.importCbcora(coreCCRequest.getCbcora().getUrl(), coreCCRequest.getTimestamp(), dcNetwork);

        final RaoParameters raoParameters = fileImporter.importRaoParameters(coreCCRequest.getRaoParameters().getUrl());

        postProcessingData.setAcNetwork(acNetwork);
        postProcessingData.setDcNetwork(dcNetwork);
        postProcessingData.setInitialAcNetworkVariantId(initialAcNetworkVariantId);
        postProcessingData.setCracCreationContext(fbConstraintCreationContext);
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
        final boolean semRaoSucceeded = coreCCRequest.getSemHourlyRaoResult().getStatus() == HourlyRaoResult.Status.SUCCESS;

        final RaoResult raoResult;
        if (semEnabled && continentalRaoSucceeded && semRaoSucceeded) {
            // If SEM is enabled and both SEM and continental RAO succeeded, the RAO results must be merged
            raoResult = mergeRaoResults(postProcessingData);
        } else if (semEnabled && !continentalRaoSucceeded) {
            // If SEM is enabled and only SEM RAO succeeded (continental RAO failed),
            // the RAO result that will be used for outputs generation is the one from SEM computation
            final String semRaoResultFileUrl = postProcessingData.getRequest().getSemHourlyRaoResult().getRaoResultFileUrl();
            final Crac crac = postProcessingData.getCrac();
            raoResult = fileImporter.importRaoResult(semRaoResultFileUrl, crac);
        } else {
            // If SEM is disabled or if only continental RAO succeeded,
            // the RAO result that will be used for outputs generation is the one from continental computation
            final String continentalRaoResultFileUrl = postProcessingData.getRequest().getContinentalHourlyRaoResult().getRaoResultFileUrl();
            final Crac crac = postProcessingData.getCrac();
            raoResult = fileImporter.importRaoResult(continentalRaoResultFileUrl, crac);
        }
        postProcessingData.setRaoResult(raoResult);
    }

    private RaoResult mergeRaoResults(final CoreCCPostProcessingData postProcessingData) {
        final InternalCoreCCRequest coreCCRequest = postProcessingData.getRequest();

        final HourlyRaoRequest continentalHourlyRaoRequest = coreCCRequest.getContinentalHourlyRaoRequest();
        final HourlyRaoResult continentalHourlyRaoResult = coreCCRequest.getContinentalHourlyRaoResult();
        final HourlyRaoRequest semHourlyRaoRequest = coreCCRequest.getSemHourlyRaoRequest();
        final HourlyRaoResult semHourlyRaoResult = coreCCRequest.getSemHourlyRaoResult();

        final RaoResult continentalRaoResult = getRaoResult(continentalHourlyRaoRequest.getNetworkFileUrl(),
                                                            continentalHourlyRaoRequest.getCracFileUrl(),
                                                            continentalHourlyRaoResult.getRaoResultFileUrl());
        final RaoResult semRaoResult = getRaoResult(semHourlyRaoRequest.getNetworkFileUrl(),
                                                    semHourlyRaoRequest.getCracFileUrl(),
                                                    semHourlyRaoResult.getRaoResultFileUrl());

        // ReferenceProgram and GLSK are required for RaoResult merging but we don't need them for any other action
        // So we only import those files here without adding them to the postProcessingData
        final ReferenceProgram referenceProgram = fileImporter.importReferenceProgram(coreCCRequest.getRefProg(), coreCCRequest.getTimestamp());
        final ZonalData<SensitivityVariableSet> glskProvider = fileImporter.importGlsk(coreCCRequest.getGlsk().getUrl(), coreCCRequest.getTimestamp(), postProcessingData.getDcNetwork());

        return raoResultMerger.mergeRaoResults(continentalRaoResult,
                                               semRaoResult,
                                               postProcessingData.getAcNetwork(),
                                               postProcessingData.getCrac(),
                                               referenceProgram,
                                               glskProvider,
                                               postProcessingData.getRaoParameters(),
                                               ReportNode.NO_OP,
                                               postProcessingData.getInitialAcNetworkVariantId());
    }

    private RaoResult getRaoResult(final String networkFileUrl,
                                   final String cracFileUrl,
                                   final String raoResultFileUrl) {
        final Network network = fileImporter.importNetwork(networkFileUrl);
        final Crac crac = fileImporter.importCrac(cracFileUrl, network);

        return fileImporter.importRaoResult(raoResultFileUrl, crac);
    }
}
