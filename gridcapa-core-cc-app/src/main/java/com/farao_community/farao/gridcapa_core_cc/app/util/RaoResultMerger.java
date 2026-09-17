/*
 * Copyright (c) 2026, RTE (http://www.rte-france.com)
 *  This Source Code Form is subject to the terms of the Mozilla Public
 *  License, v. 2.0. If a copy of the MPL was not distributed with this
 *  file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package com.farao_community.farao.gridcapa_core_cc.app.util;

import com.powsybl.commons.report.ReportNode;
import com.powsybl.contingency.Contingency;
import com.powsybl.glsk.commons.ZonalData;
import com.powsybl.iidm.network.Network;
import com.powsybl.iidm.network.VariantManager;
import com.powsybl.openrao.data.crac.api.Crac;
import com.powsybl.openrao.data.crac.api.Instant;
import com.powsybl.openrao.data.crac.api.State;
import com.powsybl.openrao.data.crac.api.cnec.FlowCnec;
import com.powsybl.openrao.data.crac.api.networkaction.NetworkAction;
import com.powsybl.openrao.data.raoresult.api.RaoResult;
import com.powsybl.openrao.data.refprog.referenceprogram.ReferenceProgram;
import com.powsybl.openrao.raoapi.RaoInput;
import com.powsybl.openrao.raoapi.parameters.RaoParameters;
import com.powsybl.openrao.searchtreerao.castor.algorithm.PostPerimeterSensitivityAnalysis;
import com.powsybl.openrao.searchtreerao.castor.algorithm.PrePerimeterSensitivityAnalysis;
import com.powsybl.openrao.searchtreerao.castor.algorithm.StateTree;
import com.powsybl.openrao.searchtreerao.commons.ToolProvider;
import com.powsybl.openrao.searchtreerao.result.api.OptimizationResult;
import com.powsybl.openrao.searchtreerao.result.api.PrePerimeterResult;
import com.powsybl.openrao.searchtreerao.result.impl.NetworkActionsResultImpl;
import com.powsybl.openrao.searchtreerao.result.impl.OptimizationResultImpl;
import com.powsybl.openrao.searchtreerao.result.impl.PostPerimeterResult;
import com.powsybl.openrao.searchtreerao.result.impl.PreventiveAndCurativesRaoResultImpl;
import com.powsybl.openrao.searchtreerao.result.impl.RangeActionActivationResultImpl;
import com.powsybl.openrao.sensitivityanalysis.AppliedRemedialActions;
import com.powsybl.sensitivity.SensitivityVariableSet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * @author Vincent Bochet {@literal <vincent.bochet at rte-france.com>}
 * @author Thomas Bouquet {@literal <thomas.bouquet at rte-france.com>}
 */
@Service
public class RaoResultMerger {
    private static final Logger LOGGER = LoggerFactory.getLogger(RaoResultMerger.class);
    private static final String MERGING_VARIANT_NAME = "RaoResultsMerging";

    private final Logger businessLogger;

    public RaoResultMerger(final Logger businessLogger) {
        this.businessLogger = businessLogger;
    }

    public RaoResult mergeRaoResults(final RaoResult continentalRaoResult,
                                     final RaoResult semRaoResult,
                                     final Network fullNetwork,
                                     final Crac fullCrac,
                                     final ReferenceProgram referenceProgram,
                                     final ZonalData<SensitivityVariableSet> glskProvider,
                                     final RaoParameters raoParameters,
                                     final ReportNode reportNode,
                                     final String variantIdToWorkOn) {
        businessLogger.info("Merging continental and SEM RAO results [start]");
        LOGGER.info("Merging performed on variant {}", variantIdToWorkOn);

        // store network variants data
        final VariantManager networkVariantManager = fullNetwork.getVariantManager();
        final String initialVariantId = networkVariantManager.getWorkingVariantId();
        networkVariantManager.setWorkingVariant(variantIdToWorkOn);

        final RaoInput.RaoInputBuilder raoInputBuilder = RaoInput.build(fullNetwork, fullCrac);
        raoInputBuilder.withRefProg(referenceProgram);
        raoInputBuilder.withGlskProvider(glskProvider);
        final ToolProvider toolProvider = ToolProvider.buildFromRaoInputAndParameters(
            raoInputBuilder.build(), raoParameters
        );
        final PrePerimeterSensitivityAnalysis initialPrePerimeterSensitivityAnalysis = new PrePerimeterSensitivityAnalysis(
            fullCrac, fullCrac.getFlowCnecs(), fullCrac.getRangeActions(), raoParameters, toolProvider, true
        );
        final PrePerimeterResult initialFlowResult = initialPrePerimeterSensitivityAnalysis.runInitialSensitivityAnalysis(fullNetwork, reportNode);

        // create a new network variant for performing the results merging
        networkVariantManager.cloneVariant(variantIdToWorkOn, MERGING_VARIANT_NAME);
        networkVariantManager.setWorkingVariant(MERGING_VARIANT_NAME);

        // apply PRAs
        businessLogger.info("Applying preventive remedial actions");
        final State preventiveState = fullCrac.getPreventiveState();
        NetworkUtil.applyActivatedRemedialActionsForState(fullNetwork, continentalRaoResult, preventiveState);
        NetworkUtil.applyActivatedRemedialActionsForState(fullNetwork, semRaoResult, preventiveState);

        // this result is only used as a data holder for flows: it does not contain the proper objective function value in costly
        final PrePerimeterResult preventivePrePerimeterResult = initialPrePerimeterSensitivityAnalysis.runBasedOnInitialResults(
            fullNetwork, initialFlowResult, Set.of(), new AppliedRemedialActions(), reportNode
        );

        final RangeActionActivationResultImpl preventiveRangeActionActivationResult = new RangeActionActivationResultImpl(initialFlowResult);
        continentalRaoResult.getActivatedRangeActionsDuringState(preventiveState).forEach(rangeAction -> preventiveRangeActionActivationResult.putResult(rangeAction, preventiveState, continentalRaoResult.getOptimizedSetPointOnState(preventiveState, rangeAction)));
        semRaoResult.getActivatedRangeActionsDuringState(preventiveState).forEach(rangeAction -> preventiveRangeActionActivationResult.putResult(rangeAction, preventiveState, semRaoResult.getOptimizedSetPointOnState(preventiveState, rangeAction)));

        final Set<NetworkAction> preventiveNetworkActions = new HashSet<>(continentalRaoResult.getActivatedNetworkActionsDuringState(preventiveState));
        preventiveNetworkActions.addAll(semRaoResult.getActivatedNetworkActionsDuringState(preventiveState));

        final OptimizationResult preventiveResult = new OptimizationResultImpl(
            preventivePrePerimeterResult, preventivePrePerimeterResult, preventivePrePerimeterResult,
            new NetworkActionsResultImpl(Map.of(
                preventiveState, preventiveNetworkActions
            )),
            preventiveRangeActionActivationResult
        );

        final PostPerimeterResult preventivePostPerimeterResult =
            new PostPerimeterSensitivityAnalysis(fullCrac, fullCrac.getFlowCnecs(), fullCrac.getRangeActions(), raoParameters, toolProvider, true)
                .runBasedOnInitialPreviousAndOptimizationResults(fullNetwork, initialFlowResult, preventivePrePerimeterResult, Set.of(), preventiveResult, new AppliedRemedialActions(), reportNode);

        final Map<State, PostPerimeterResult> postRegulationPostContingencyResults = new HashMap<>();

        final List<Instant> postOutageInstants = fullCrac.getSortedInstants().stream()
            .filter(instant -> instant.isAuto() || instant.isCurative())
            .toList();

        for (final Contingency contingency : fullCrac.getContingencies()) {
            businessLogger.info("Applying curative remedial actions for contingency {}", contingency.getId());
            final AppliedRemedialActions appliedRemedialActions = new AppliedRemedialActions();

            networkVariantManager.cloneVariant(MERGING_VARIANT_NAME, contingency.getId());
            networkVariantManager.setWorkingVariant(contingency.getId());

            PrePerimeterResult contingencyPrePerimeterResult = preventivePostPerimeterResult.prePerimeterResultForAllFollowingStates();

            for (final Instant instant : postOutageInstants) {
                final State state = fullCrac.getState(contingency, instant);
                if (state != null) {
                    final RangeActionActivationResultImpl rangeActionActivationResult = new RangeActionActivationResultImpl(contingencyPrePerimeterResult);
                    appliedRemedialActions.addAppliedNetworkActions(state, continentalRaoResult.getActivatedNetworkActionsDuringState(state));
                    appliedRemedialActions.addAppliedNetworkActions(state, semRaoResult.getActivatedNetworkActionsDuringState(state));
                    continentalRaoResult.getActivatedRangeActionsDuringState(state).forEach(
                        rangeAction -> {
                            final double optimizedSetPointOnState = continentalRaoResult.getOptimizedSetPointOnState(state, rangeAction);
                            appliedRemedialActions.addAppliedRangeAction(state, rangeAction, optimizedSetPointOnState);
                            rangeActionActivationResult.putResult(rangeAction, state, optimizedSetPointOnState);
                        }
                    );
                    semRaoResult.getActivatedRangeActionsDuringState(state).forEach(
                        rangeAction -> {
                            final double optimizedSetPointOnState = semRaoResult.getOptimizedSetPointOnState(state, rangeAction);
                            appliedRemedialActions.addAppliedRangeAction(state, rangeAction, optimizedSetPointOnState);
                            rangeActionActivationResult.putResult(rangeAction, state, optimizedSetPointOnState);
                        }
                    );

                    final PrePerimeterSensitivityAnalysis statePrePerimeterSensitivityAnalysis = new PrePerimeterSensitivityAnalysis(
                        fullCrac, fullCrac.getFlowCnecs(state), fullCrac.getRangeActions(), raoParameters, toolProvider, true
                    );

                    final PrePerimeterResult statePrePerimeterResult = statePrePerimeterSensitivityAnalysis.runBasedOnInitialResults(
                        fullNetwork, initialFlowResult, Collections.emptySet(), appliedRemedialActions, reportNode
                    );

                    final Set<NetworkAction> stateNetworkActions = new HashSet<>(continentalRaoResult.getActivatedNetworkActionsDuringState(state));
                    stateNetworkActions.addAll(semRaoResult.getActivatedNetworkActionsDuringState(state));

                    final OptimizationResult stateOptimizationResult = new OptimizationResultImpl(
                        statePrePerimeterResult,
                        statePrePerimeterResult,
                        statePrePerimeterResult,
                        new NetworkActionsResultImpl(Map.of(state, stateNetworkActions)),
                        rangeActionActivationResult
                    );
                    final Set<FlowCnec> statePostPerimeterFlowCnecs = fullCrac.getFlowCnecs().stream()
                        .filter(cnec -> !cnec.getState().getInstant().comesBefore(instant))
                        .filter(cnec -> cnec.getState().getContingency().orElseThrow().equals(contingency))
                        .collect(Collectors.toSet());

                    final PostPerimeterResult statePostPerimeterResult =
                        new PostPerimeterSensitivityAnalysis(fullCrac, statePostPerimeterFlowCnecs, fullCrac.getRangeActions(), raoParameters, toolProvider, true)
                            .runBasedOnInitialPreviousAndOptimizationResults(fullNetwork, initialFlowResult, contingencyPrePerimeterResult, Set.of(), stateOptimizationResult, appliedRemedialActions, reportNode);
                    postRegulationPostContingencyResults.put(state, statePostPerimeterResult);

                    contingencyPrePerimeterResult = statePrePerimeterResult;
                }
            }

            networkVariantManager.setWorkingVariant(MERGING_VARIANT_NAME);
            networkVariantManager.removeVariant(contingency.getId());
        }

        final StateTree stateTree = new StateTree(fullCrac, reportNode);
        final PreventiveAndCurativesRaoResultImpl postRegulationRaoResult = new PreventiveAndCurativesRaoResultImpl(
            stateTree,
            initialFlowResult,
            preventivePostPerimeterResult,
            postRegulationPostContingencyResults,
            fullCrac,
            raoParameters,
            reportNode
        );
        final String executionDetails = String.format("[CONTINENTAL] %s / [SEM] %s", continentalRaoResult.getExecutionDetails(), semRaoResult.getExecutionDetails());
        postRegulationRaoResult.setExecutionDetails(executionDetails);

        // post-process variants
        networkVariantManager.setWorkingVariant(initialVariantId);
        networkVariantManager.removeVariant(MERGING_VARIANT_NAME);

        businessLogger.info("Merging continental and SEM RAO results [end]");

        return postRegulationRaoResult;
    }
}
