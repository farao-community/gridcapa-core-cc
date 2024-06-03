/*
 * Copyright (c) 2023, RTE (http://www.rte-france.com)
 *  This Source Code Form is subject to the terms of the Mozilla Public
 *  License, v. 2.0. If a copy of the MPL was not distributed with this
 *  file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package com.farao_community.farao.gridcapa_core_cc.app.postprocessing;

import com.farao_community.farao.gridcapa_core_cc.api.exception.CoreCCInternalException;
import com.farao_community.farao.gridcapa_core_cc.api.resource.HourlyRaoRequest;
import com.farao_community.farao.gridcapa_core_cc.api.resource.HourlyRaoResult;
import com.farao_community.farao.gridcapa_core_cc.api.resource.InternalCoreCCRequest;
import com.farao_community.farao.gridcapa_core_cc.app.entities.CgmsAndXmlHeader;
import com.farao_community.farao.gridcapa_core_cc.app.services.FileImporter;
import com.farao_community.farao.gridcapa_core_cc.app.util.CoreNetworkImporterWrapper;
import com.farao_community.farao.minio_adapter.starter.MinioAdapter;
import com.powsybl.iidm.network.Network;
import com.powsybl.openrao.data.cracapi.Crac;
import com.powsybl.openrao.data.cracapi.State;
import com.powsybl.openrao.data.cracioapi.CracImporters;
import com.powsybl.openrao.data.raoresultapi.RaoResult;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.nio.file.Path;
import java.time.Instant;

/**
 * @author Daniel Thirion {@literal <daniel.thirion at rte-france.com>}
 */
@Service
public class RegularOrDcCgmNetworkResolver {
    private final FileImporter fileImporter;
    private final MinioAdapter minioAdapter;

    public RegularOrDcCgmNetworkResolver(final FileImporter fileImporter,
                                         final MinioAdapter minioAdapter) {
        this.fileImporter = fileImporter;
        this.minioAdapter = minioAdapter;
    }

    public Network resolve(final boolean useDcCGmInput,
                           final InternalCoreCCRequest coreCCRequest) {
        final Network network;
        final HourlyRaoResult hourlyRaoResult = coreCCRequest.getHourlyRaoResult();
        if (useDcCGmInput) {
            //If DC CGM was used, apply PRA to regular CGM
            final CgmsAndXmlHeader cgmsAndXmlHeader = fileImporter.importCgmsZip(coreCCRequest.getCgm());
            final Path cgmPath = cgmsAndXmlHeader.getNetworkPath(Instant.parse(hourlyRaoResult.getRaoRequestInstant()));
            network = CoreNetworkImporterWrapper.loadNetwork(cgmPath);
            final Crac crac = importCracFromHourlyRaoRequest(coreCCRequest, network);
            final RaoResult raoResult = fileImporter.importRaoResult(hourlyRaoResult.getRaoResultFileUrl(), crac);
            applyRemedialActionsForState(network, raoResult, crac.getPreventiveState());
        } else {
            network = fileImporter.importNetworkFromUrl(hourlyRaoResult.getNetworkWithPraUrl());
        }
        return network;
    }

    private static void applyRemedialActionsForState(final Network network,
                                                     final RaoResult raoResult,
                                                     final State state) {
        raoResult.getActivatedNetworkActionsDuringState(state).forEach(networkAction -> networkAction.apply(network));
        raoResult.getActivatedRangeActionsDuringState(state).forEach(rangeAction ->
                rangeAction.apply(network, raoResult.getOptimizedSetPointsOnState(state).get(rangeAction)));
    }

    private Crac importCracFromHourlyRaoRequest(final InternalCoreCCRequest coreCCRequest,
                                        final Network network) {
        final HourlyRaoRequest hourlyRaoRequest = coreCCRequest.getHourlyRaoRequest();
        final String cracFileUrl = hourlyRaoRequest.getCracFileUrl();
        try (final InputStream cracFileInputStream = minioAdapter.getFile(cracFileUrl)) {
            return CracImporters.importCrac(Path.of(cracFileUrl).getFileName().toString(), cracFileInputStream, network);
        } catch (final Exception e) {
            throw new CoreCCInternalException(String.format("Exception occurred while importing CRAC file: %s", Path.of(cracFileUrl).getFileName().toString()), e);
        }
    }
}
