/*
 * Copyright (c) 2026, RTE (http://www.rte-france.com)
 *  This Source Code Form is subject to the terms of the Mozilla Public
 *  License, v. 2.0. If a copy of the MPL was not distributed with this
 *  file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.farao_community.farao.gridcapa_core_cc.app.postprocessing;

import com.farao_community.farao.gridcapa_core_cc.api.resource.InternalCoreCCRequest;
import com.powsybl.iidm.network.Network;
import com.powsybl.openrao.data.crac.api.Crac;
import com.powsybl.openrao.data.crac.io.fbconstraint.FbConstraintCreationContext;
import com.powsybl.openrao.data.raoresult.api.RaoResult;
import com.powsybl.openrao.raoapi.parameters.RaoParameters;

/**
 * @author Vincent Bochet {@literal <vincent.bochet at rte-france.com>}
 */
public class CoreCCPostProcessingData {
    private InternalCoreCCRequest request;
    private Network acNetwork;
    private String initialAcNetworkVariantId;
    private Network dcNetwork;
    private FbConstraintCreationContext cracCreationContext;
    private FbConstraintCreationContext continentalCracCreationContext;
    private FbConstraintCreationContext semCracCreationContext;
    private RaoParameters raoParameters;
    private RaoResult raoResult;

    public InternalCoreCCRequest getRequest() {
        return request;
    }

    public void setRequest(final InternalCoreCCRequest request) {
        this.request = request;
    }

    public String getRaoRequestInstant() {
        if (request != null && request.getContinentalHourlyRaoRequest() != null) {
            return request.getContinentalHourlyRaoRequest().getRaoRequestInstant();
        } else if (request != null && request.getSemHourlyRaoRequest() != null) {
            return request.getSemHourlyRaoRequest().getRaoRequestInstant();
        }
        return null;
    }

    public String getResultsDestination() {
        if (request == null) {
            return null;
        }
        return request.getDestinationPath();
    }

    public Network getAcNetwork() {
        return acNetwork;
    }

    public void setAcNetwork(final Network acNetwork) {
        this.acNetwork = acNetwork;
    }

    public Network getDcNetwork() {
        return dcNetwork;
    }

    public void setDcNetwork(final Network dcNetwork) {
        this.dcNetwork = dcNetwork;
    }

    public String getInitialAcNetworkVariantId() {
        return initialAcNetworkVariantId;
    }

    public void setInitialAcNetworkVariantId(final String initialAcNetworkVariantId) {
        this.initialAcNetworkVariantId = initialAcNetworkVariantId;
    }

    public FbConstraintCreationContext getCracCreationContext() {
        return cracCreationContext;
    }

    public void setCracCreationContext(final FbConstraintCreationContext cracCreationContext) {
        this.cracCreationContext = cracCreationContext;
    }

    public Crac getCrac() {
        if (cracCreationContext == null) {
            return null;
        }
        return cracCreationContext.getCrac();
    }

    public FbConstraintCreationContext getContinentalCracCreationContext() {
        return continentalCracCreationContext;
    }

    public void setContinentalCracCreationContext(final FbConstraintCreationContext continentalCracCreationContext) {
        this.continentalCracCreationContext = continentalCracCreationContext;
    }

    public Crac getContinentalCrac() {
        if (continentalCracCreationContext == null) {
            return null;
        }
        return continentalCracCreationContext.getCrac();
    }

    public FbConstraintCreationContext getSemCracCreationContext() {
        return semCracCreationContext;
    }

    public void setSemCracCreationContext(final FbConstraintCreationContext semCracCreationContext) {
        this.semCracCreationContext = semCracCreationContext;
    }

    public Crac getSemCrac() {
        if (semCracCreationContext == null) {
            return null;
        }
        return semCracCreationContext.getCrac();
    }

    public RaoParameters getRaoParameters() {
        return raoParameters;
    }

    public void setRaoParameters(final RaoParameters raoParameters) {
        this.raoParameters = raoParameters;
    }

    public RaoResult getRaoResult() {
        return raoResult;
    }

    public void setRaoResult(final RaoResult raoResult) {
        this.raoResult = raoResult;
    }
}
