package com.farao_community.farao.gridcapa_core_cc.app.util;

import com.powsybl.commons.report.ReportNode;
import com.powsybl.contingency.Contingency;
import com.powsybl.iidm.network.Network;
import com.powsybl.openrao.commons.logs.OpenRaoLoggerProvider;
import com.powsybl.openrao.data.crac.api.Crac;
import com.powsybl.openrao.data.crac.api.Instant;
import com.powsybl.openrao.data.crac.api.State;
import com.powsybl.openrao.data.crac.api.cnec.FlowCnec;
import com.powsybl.openrao.data.crac.api.networkaction.NetworkAction;
import com.powsybl.openrao.data.raoresult.api.RaoResult;
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

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public class RaoResultMerger {
    private static RaoResult mergeRaoResults(final RaoResult coreRaoResult,
                                             final RaoResult semRaoResult,
                                             final Network network,
                                             final Crac crac,
                                             final RaoParameters raoParameters,
                                             final ReportNode reportNode,
                                             final String initialVariantId) {
        OpenRaoLoggerProvider.BUSINESS_WARNS.warn("Merging Core and SEM RAO results [start]");
        OpenRaoLoggerProvider.BUSINESS_WARNS.warn("Merging performed on variant {}", initialVariantId);

        // store network variants data
        final Set<String> initialVariantsIds = new HashSet<>(network.getVariantManager().getVariantIds());
        network.getVariantManager().setWorkingVariant(initialVariantId);

        final ToolProvider toolProvider = ToolProvider.buildFromRaoInputAndParameters(
            RaoInput.build(network, crac).build(), raoParameters
        );
        final PrePerimeterSensitivityAnalysis initialPrePerimeterSensitivityAnalysis = new PrePerimeterSensitivityAnalysis(
            crac, crac.getFlowCnecs(), crac.getRangeActions(), raoParameters, toolProvider, true
        );
        final PrePerimeterResult initialFlowResult = initialPrePerimeterSensitivityAnalysis.runInitialSensitivityAnalysis(network, reportNode);

        // create a new network variant from initial variant for performing the results merging
        final String variantName = "RaoResultsMerging";
        // TODO utiliser le variant initial et non pas le dernier variant (qui contient possiblement des PRA appliquées ou autre modif)
        network.getVariantManager().cloneVariant(network.getVariantManager().getWorkingVariantId(), variantName);
        network.getVariantManager().setWorkingVariant(variantName);

        // apply PRAs
        final State preventiveState = crac.getPreventiveState();
        applyOptimalRemedialActionsForState(network, coreRaoResult, preventiveState);
        applyOptimalRemedialActionsForState(network, semRaoResult, preventiveState);

        // this result is only used as a data holder for flows: it does not contain the proper objective function value in costly
        final PrePerimeterResult preventivePrePerimeterResult = initialPrePerimeterSensitivityAnalysis.runBasedOnInitialResults(
            network, initialFlowResult, Set.of(), new AppliedRemedialActions(), reportNode
        );

        RangeActionActivationResultImpl preventiveRangeActionActivationResult = new RangeActionActivationResultImpl(initialFlowResult);
        coreRaoResult.getActivatedRangeActionsDuringState(preventiveState).forEach(rangeAction -> preventiveRangeActionActivationResult.putResult(rangeAction, preventiveState, coreRaoResult.getOptimizedSetPointOnState(preventiveState, rangeAction)));
        semRaoResult.getActivatedRangeActionsDuringState(preventiveState).forEach(rangeAction -> preventiveRangeActionActivationResult.putResult(rangeAction, preventiveState, semRaoResult.getOptimizedSetPointOnState(preventiveState, rangeAction)));

        final Set<NetworkAction> preventiveNetworkActions = new HashSet<>(coreRaoResult.getActivatedNetworkActionsDuringState(preventiveState));
        preventiveNetworkActions.addAll(semRaoResult.getActivatedNetworkActionsDuringState(preventiveState));

        final OptimizationResult preventiveResult = new OptimizationResultImpl(
            preventivePrePerimeterResult, preventivePrePerimeterResult, preventivePrePerimeterResult,
            new NetworkActionsResultImpl(Map.of(
                preventiveState, preventiveNetworkActions
            )),
            preventiveRangeActionActivationResult
        );

        final PostPerimeterResult preventivePostPerimeterResult =
            new PostPerimeterSensitivityAnalysis(crac, crac.getFlowCnecs(), crac.getRangeActions(), raoParameters, toolProvider, true)
                .runBasedOnInitialPreviousAndOptimizationResults(network, initialFlowResult, preventivePrePerimeterResult, Set.of(), preventiveResult, new AppliedRemedialActions(), reportNode);

        final Map<State, PostPerimeterResult> postRegulationPostContingencyResults = new HashMap<>();

        final List<Instant> postOutageInstants = crac.getSortedInstants().stream()
            .filter(instant -> instant.isAuto() || instant.isCurative())
            .toList();

        for (final Contingency contingency : crac.getContingencies()) {
            final AppliedRemedialActions appliedRemedialActions = new AppliedRemedialActions();

            network.getVariantManager().cloneVariant(variantName, contingency.getId());
            network.getVariantManager().setWorkingVariant(contingency.getId());

            PrePerimeterResult contingencyPrePerimeterResult = preventivePostPerimeterResult.prePerimeterResultForAllFollowingStates();

            for (final Instant instant : postOutageInstants) {
                final State state = crac.getState(contingency, instant);
                if (state != null) {
                    final RangeActionActivationResultImpl rangeActionActivationResult = new RangeActionActivationResultImpl(contingencyPrePerimeterResult);
                    appliedRemedialActions.addAppliedNetworkActions(state, coreRaoResult.getActivatedNetworkActionsDuringState(state));
                    appliedRemedialActions.addAppliedNetworkActions(state, semRaoResult.getActivatedNetworkActionsDuringState(state));
                    coreRaoResult.getActivatedRangeActionsDuringState(state).forEach(
                        rangeAction -> {
                            final double optimizedSetPointOnState = coreRaoResult.getOptimizedSetPointOnState(state, rangeAction);
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
                        crac, crac.getFlowCnecs(state), crac.getRangeActions(), raoParameters, toolProvider, true
                    );

                    final PrePerimeterResult statePrePerimeterResult = statePrePerimeterSensitivityAnalysis.runBasedOnInitialResults(
                        network, initialFlowResult, Collections.emptySet(), appliedRemedialActions, reportNode
                    );

                    final Set<NetworkAction> stateNetworkActions = new HashSet<>(coreRaoResult.getActivatedNetworkActionsDuringState(state));
                    stateNetworkActions.addAll(semRaoResult.getActivatedNetworkActionsDuringState(state));

                    final OptimizationResult stateOptimizationResult = new OptimizationResultImpl(
                        statePrePerimeterResult,
                        statePrePerimeterResult,
                        statePrePerimeterResult,
                        new NetworkActionsResultImpl(Map.of(state, stateNetworkActions)),
                        rangeActionActivationResult
                    );
                    final Set<FlowCnec> statePostPerimeterFlowCnecs = crac.getFlowCnecs().stream()
                        .filter(cnec -> !cnec.getState().getInstant().comesBefore(instant))
                        .filter(cnec -> cnec.getState().getContingency().orElseThrow().equals(contingency))
                        .collect(Collectors.toSet());

                    final PostPerimeterResult statePostPerimeterResult =
                        new PostPerimeterSensitivityAnalysis(crac, statePostPerimeterFlowCnecs, crac.getRangeActions(), raoParameters, toolProvider, true)
                            .runBasedOnInitialPreviousAndOptimizationResults(network, initialFlowResult, contingencyPrePerimeterResult, Set.of(), stateOptimizationResult, appliedRemedialActions, reportNode);
                    postRegulationPostContingencyResults.put(state, statePostPerimeterResult);

                    contingencyPrePerimeterResult = statePrePerimeterResult;

                    OpenRaoLoggerProvider.BUSINESS_WARNS.warn("Optimal remedial actions of state {} successfully applied", state.getId());
                }
            }
        }

        final StateTree stateTree = new StateTree(crac, reportNode);
        final PreventiveAndCurativesRaoResultImpl postRegulationRaoResult = new PreventiveAndCurativesRaoResultImpl(
            stateTree,
            initialFlowResult,
            preventivePostPerimeterResult,
            postRegulationPostContingencyResults,
            crac,
            raoParameters,
            reportNode
        );
        final String executionDetails = String.format("[CORE] %s / [SEM] %s", coreRaoResult.getExecutionDetails(), semRaoResult.getExecutionDetails());
        postRegulationRaoResult.setExecutionDetails(executionDetails);

        // post-process variants
        network.getVariantManager().setWorkingVariant(initialVariantId);
        Set<String> variantsToRemove = network.getVariantManager().getVariantIds()
            .stream()
            .filter(variantId -> !initialVariantsIds.contains(variantId))
            .collect(Collectors.toSet());
        OpenRaoLoggerProvider.BUSINESS_WARNS.warn("The following variants will be removed: {}", String.join(", ", variantsToRemove));
        variantsToRemove.forEach(network.getVariantManager()::removeVariant);

        OpenRaoLoggerProvider.BUSINESS_WARNS.warn("Merging Core and SEM RAO results [end]");

        return postRegulationRaoResult;
    }

    private static void applyOptimalRemedialActionsForState(Network networkClone, RaoResult raoResult, State state) {
        // network actions need to be applied BEFORE range actions because to apply HVDC range actions we need to apply AC emulation deactivation network actions beforehand
        raoResult.getActivatedNetworkActionsDuringState(state)
            .forEach(networkAction -> networkAction.apply(networkClone));
        raoResult.getActivatedRangeActionsDuringState(state)
            .forEach(rangeAction -> rangeAction.apply(
                networkClone, raoResult.getOptimizedSetPointOnState(state, rangeAction)
            ));
    }
}
