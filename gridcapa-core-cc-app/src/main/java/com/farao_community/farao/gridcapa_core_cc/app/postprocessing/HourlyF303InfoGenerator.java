/*
 * Copyright (c) 2021, RTE (http://www.rte-france.com)
 */
package com.farao_community.farao.gridcapa_core_cc.app.postprocessing;

import com.farao_community.farao.data.crac_api.Crac;
import com.farao_community.farao.data.crac_api.Instant;
import com.farao_community.farao.data.crac_api.RemedialAction;
import com.farao_community.farao.data.crac_api.State;
import com.farao_community.farao.data.crac_api.network_action.NetworkAction;
import com.farao_community.farao.data.crac_api.range_action.PstRangeAction;
import com.farao_community.farao.data.crac_api.range_action.RangeAction;
import com.farao_community.farao.data.crac_creation.creator.api.CracCreationContext;
import com.farao_community.farao.data.crac_creation.creator.api.ImportStatus;
import com.farao_community.farao.data.crac_creation.creator.fb_constraint.FbConstraint;
import com.farao_community.farao.data.crac_creation.creator.fb_constraint.crac_creator.FbConstraintCracCreator;
import com.farao_community.farao.data.crac_creation.creator.fb_constraint.crac_creator.FbConstraintCreationContext;
import com.farao_community.farao.data.crac_creation.creator.fb_constraint.xsd.ActionType;
import com.farao_community.farao.data.crac_creation.creator.fb_constraint.xsd.CriticalBranchType;
import com.farao_community.farao.data.crac_creation.creator.fb_constraint.xsd.IndependantComplexVariant;
import com.farao_community.farao.data.crac_creation.creator.fb_constraint.xsd.ObjectFactory;
import com.farao_community.farao.data.crac_creation.creator.fb_constraint.xsd.etso.TimeIntervalType;
import com.farao_community.farao.data.rao_result_api.RaoResult;
import com.farao_community.farao.data.rao_result_json.RaoResultImporter;
import com.farao_community.farao.gridcapa_core_cc.api.exception.CoreCCInternalException;
import com.farao_community.farao.gridcapa_core_cc.api.resource.InternalCoreCCRequest;
import com.farao_community.farao.gridcapa_core_cc.api.resource.HourlyRaoRequest;
import com.farao_community.farao.gridcapa_core_cc.api.resource.HourlyRaoResult;
import com.farao_community.farao.gridcapa_core_cc.app.util.IntervalUtil;
import com.farao_community.farao.minio_adapter.starter.MinioAdapter;
import com.powsybl.iidm.network.Network;
import org.threeten.extra.Interval;

import java.io.IOException;
import java.io.InputStream;
import java.io.Serializable;
import java.nio.file.Path;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.*;
import java.util.stream.Collectors;

/**
 * @author Pengbo Wang {@literal <pengbo.wang at rte-international.com>}
 * @author Mohamed BenRejeb {@literal <mohamed.ben-rejeb at rte-france.com>}
 * @author Baptiste Seguinot {@literal <baptiste.seguinot at rte-france.com}
 */
class HourlyF303InfoGenerator {

    private static final String TATL = "_TATL";
    private static final String PATL = "_PATL";

    private final FbConstraint nativeCrac;
    private final Interval interval;
    private final InternalCoreCCRequest coreCCRequest;
    private final MinioAdapter minioAdapter;

    HourlyF303InfoGenerator(FbConstraint nativeCrac, Interval interval, InternalCoreCCRequest coreCCRequest, MinioAdapter minioAdapter) {
        this.nativeCrac = nativeCrac;
        this.interval = interval;
        this.coreCCRequest = coreCCRequest;
        this.minioAdapter = minioAdapter;
    }

    HourlyF303Info generate() {
        HourlyRaoRequest hourlyRaoRequest = getRaoRequestForInterval(interval, coreCCRequest);
        HourlyRaoResult hourlyRaoResult = getRaoResultForInterval(interval, coreCCRequest);

        if (hourlyRaoRequest == null || hourlyRaoResult == null || !hourlyRaoResult.getStatus().equals(HourlyRaoResult.Status.SUCCESS)) {
            return getInfoForNonRequestedOrFailedInterval(interval, nativeCrac);
        }

        return getInfoForSuccessfulInterval(interval, hourlyRaoRequest, hourlyRaoResult, nativeCrac);
    }

    private HourlyF303Info getInfoForNonRequestedOrFailedInterval(Interval interval, FbConstraint nativeCrac) {

        OffsetDateTime startTime = OffsetDateTime.ofInstant(interval.getStart(), ZoneOffset.UTC);
        List<CriticalBranchType> criticalBranches = new ArrayList<>();
        TimeIntervalType ti = new TimeIntervalType();
        ti.setV(IntervalUtil.getCurrentTimeInterval(startTime));

        nativeCrac.getDocument().getCriticalBranches().getCriticalBranch().stream()
                .filter(cb -> IntervalUtil.isInTimeInterval(startTime, cb.getTimeInterval().getV()))
                .forEach(refCb -> {
                    CriticalBranchType clonedCb = (CriticalBranchType) refCb.clone();
                    clonedCb.setTimeInterval(ti);
                    setPermanentLimit(clonedCb);
                    criticalBranches.add(clonedCb);
                });

        return new HourlyF303Info(criticalBranches);
    }

    private HourlyF303Info getInfoForSuccessfulInterval(Interval interval, HourlyRaoRequest hourlyRaoRequest, HourlyRaoResult hourlyRaoResult, FbConstraint nativeCrac) {

        Network network = getNetworkOfHourlyRequest(hourlyRaoRequest);
        FbConstraintCreationContext cracCreationContext = new FbConstraintCracCreator().createCrac(nativeCrac, network, OffsetDateTime.parse(hourlyRaoRequest.getInstant()).plusMinutes(30), CracHelper.getCracCreationParameters());
        RaoResult raoResult = getRaoResultOfHourlyRaoResult(hourlyRaoResult, cracCreationContext.getCrac());

        Map<State, String> statesWithCra = getUIDOfStatesWithCra(cracCreationContext, raoResult, hourlyRaoRequest.getInstant());

        List<CriticalBranchType> criticalBranches = getCriticalBranchesOfSuccessfulInterval(interval, nativeCrac, cracCreationContext, statesWithCra);
        List<IndependantComplexVariant> complexVariants = getComplexVariantsOfSuccesfulInterval(interval, nativeCrac, cracCreationContext, raoResult, statesWithCra);

        return new HourlyF303Info(criticalBranches, complexVariants);

    }

    private List<CriticalBranchType> getCriticalBranchesOfSuccessfulInterval(Interval interval, FbConstraint nativeCrac, FbConstraintCreationContext cracCreationContext, Map<State, String> statesWithCrac) {

        TimeIntervalType ti = new TimeIntervalType();
        ti.setV(IntervalUtil.getCurrentTimeInterval(OffsetDateTime.ofInstant(interval.getStart(), ZoneOffset.UTC)));
        List<String> contingencyWithCra = statesWithCrac.keySet().stream().map(s -> s.getContingency().orElseThrow().getId()).collect(Collectors.toList());
        Map<String, CriticalBranchType> refCbs = getCriticalBranchesForInstant(cracCreationContext.getTimeStamp(), nativeCrac);
        List<CriticalBranchType> criticalBranches = new ArrayList<>();

        cracCreationContext.getBranchCnecCreationContexts().forEach(bccc -> {
            CriticalBranchType refCb = refCbs.get(bccc.getNativeId());
            if (bccc.getImportStatus() != ImportStatus.NOT_FOR_REQUESTED_TIMESTAMP) {
                if (refCb.getOutage() == null || !contingencyWithCra.contains(refCb.getOutage().getId())) {

                    // N critical branch or N-1 critical branch without CRA
                    // -> export one critical branch with permanent limit and no associated variant
                    CriticalBranchType patlCb = (CriticalBranchType) refCb.clone();
                    patlCb.setTimeInterval(ti);
                    setPermanentLimit(patlCb);
                    criticalBranches.add(patlCb);

                } else {
                    // N-1 critical branch with CRA
                    // -> export one critical branch with temporary limit and no associated variant (OUTAGE)
                    // -> export one critical branch with permanent limit and associated variant (CURATIVE)

                    CriticalBranchType tatlCb = (CriticalBranchType) refCb.clone();
                    tatlCb.setTimeInterval(ti);
                    tatlCb.setId(refCb.getId() + TATL);
                    tatlCb.setOriginalId(refCb.getId());
                    setTemporaryLimit(tatlCb);
                    criticalBranches.add(tatlCb);

                    CriticalBranchType patlCb = (CriticalBranchType) refCb.clone();
                    patlCb.setTimeInterval(ti);
                    patlCb.setId(refCb.getId() + PATL);
                    patlCb.setOriginalId(refCb.getId());
                    patlCb.setComplexVariantId(statesWithCrac.get(cracCreationContext.getCrac().getState(refCb.getOutage().getId(), Instant.CURATIVE)));
                    setPermanentLimit(patlCb);
                    criticalBranches.add(patlCb);
                }
            }
        });

        return criticalBranches;
    }

    private List<IndependantComplexVariant> getComplexVariantsOfSuccesfulInterval(Interval interval, FbConstraint nativeCrac, FbConstraintCreationContext cracCreationContext, RaoResult raoResult, Map<State, String> statesWithCra) {

        List<IndependantComplexVariant> complexVariants = new ArrayList<>();

        TimeIntervalType ti = new TimeIntervalType();
        ti.setV(IntervalUtil.getCurrentTimeInterval(OffsetDateTime.ofInstant(interval.getStart(), ZoneOffset.UTC)));

        Map<String, IndependantComplexVariant> nativeVariants = getComplexVariantsForInstant(cracCreationContext.getTimeStamp(), nativeCrac);
        statesWithCra.forEach((state, variantId) -> {

            Set<NetworkAction> activatedNa = raoResult.getActivatedNetworkActionsDuringState(state);
            Set<RangeAction<?>> activatedRa = raoResult.getActivatedRangeActionsDuringState(state);

            IndependantComplexVariant complexVariant = new IndependantComplexVariant();
            complexVariant.setId(variantId);
            complexVariant.setName(getMergedName(activatedNa, activatedRa));
            complexVariant.setTimeInterval(ti);
            complexVariant.setTsoOrigin(getTsoOrigin(activatedNa, activatedRa));

            activatedNa.forEach(na -> updateComplexVariantWithNetworkAction(complexVariant, nativeVariants.get(na.getId()), state.getContingency().orElseThrow().getId()));
            activatedRa.forEach(ra -> updateComplexVariantWithPstAction(complexVariant, nativeVariants.get(ra.getId()), state.getContingency().orElseThrow().getId(), raoResult.getOptimizedTapOnState(state, (PstRangeAction) ra)));

            complexVariants.add(complexVariant);
        });

        return complexVariants;
    }

    private Network getNetworkOfHourlyRequest(HourlyRaoRequest hourlyRaoRequest) {
        String networkFileUrl = hourlyRaoRequest.getNetworkFileUrl();
        try (InputStream networkInputStream = minioAdapter.getFile(networkFileUrl)) {
            return Network.read(Path.of(networkFileUrl).getFileName().toString(), networkInputStream);
        } catch (IOException e) {
            throw new CoreCCInternalException(String.format("Cannot import network of hourly request of instant %s", hourlyRaoRequest.getInstant()));
        }
    }

    private RaoResult getRaoResultOfHourlyRaoResult(HourlyRaoResult hourlyRaoResult, Crac crac) {
        RaoResultImporter raoResultImporter = new RaoResultImporter();
        try (InputStream raoResultInputStream = minioAdapter.getFile(hourlyRaoResult.getRaoResultFileUrl())) {
            return raoResultImporter.importRaoResult(raoResultInputStream, crac);
        } catch (IOException e) {
            throw new CoreCCInternalException(String.format("Cannot import RAO result of hourly RAO response of instant %s", hourlyRaoResult.getInstant()));
        }
    }

    private static HourlyRaoRequest getRaoRequestForInterval(Interval interval, InternalCoreCCRequest coreCCRequest) {
        return coreCCRequest.getHourlyRaoRequests().stream()
                .filter(hrr -> interval.contains(java.time.Instant.parse(hrr.getInstant())))
                .findAny().orElse(null);
    }

    private static HourlyRaoResult getRaoResultForInterval(Interval interval, InternalCoreCCRequest coreCCRequest) {
        return coreCCRequest.getHourlyRaoResults().stream()
                .filter(hrr -> interval.contains(java.time.Instant.parse(hrr.getInstant())))
                .findAny().orElse(null);
    }

    private static void setPermanentLimit(CriticalBranchType criticalBranch) {
        if (criticalBranch.getPermanentImaxA() != null) {
            criticalBranch.setImaxA(criticalBranch.getPermanentImaxA());
            criticalBranch.setImaxFactor(null);
        } else if (criticalBranch.getPermanentImaxFactor() != null) {
            criticalBranch.setImaxA(null);
            criticalBranch.setImaxFactor(criticalBranch.getPermanentImaxFactor());
        }
        criticalBranch.setPermanentImaxA(null);
        criticalBranch.setTemporaryImaxA(null);
        criticalBranch.setPermanentImaxFactor(null);
        criticalBranch.setTemporaryImaxFactor(null);
    }

    private static void setTemporaryLimit(CriticalBranchType criticalBranch) {
        if (criticalBranch.getTemporaryImaxA() != null) {
            criticalBranch.setImaxA(criticalBranch.getTemporaryImaxA());
            criticalBranch.setImaxFactor(null);
        } else if (criticalBranch.getTemporaryImaxFactor() != null) {
            criticalBranch.setImaxA(null);
            criticalBranch.setImaxFactor(criticalBranch.getTemporaryImaxFactor());
        }
        criticalBranch.setPermanentImaxA(null);
        criticalBranch.setTemporaryImaxA(null);
        criticalBranch.setPermanentImaxFactor(null);
        criticalBranch.setTemporaryImaxFactor(null);
    }

    private static String getMergedName(Set<NetworkAction> activatedNa, Set<RangeAction<?>> activatedRa) {

        String naNames = activatedNa.stream()
                .map(RemedialAction::getName)
                .collect(Collectors.joining(";"));

        String raNames = activatedRa.stream()
                .map(RemedialAction::getName)
                .collect(Collectors.joining(";"));

        if (activatedNa.isEmpty()) {
            return raNames;
        } else if (activatedRa.isEmpty()) {
            return naNames;
        } else {
            return naNames + ";" + raNames;
        }
    }

    private String getTsoOrigin(Set<NetworkAction> activatedNa, Set<RangeAction<?>> activatedRa) {

        Set<String> raTsos = activatedNa.stream()
                .map(RemedialAction::getOperator)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        raTsos.addAll(activatedRa.stream()
                .map(RemedialAction::getOperator)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet()));

        if (raTsos.size() == 1) {
            return raTsos.iterator().next();
        } else {
            return "XX";
        }
    }

    private static void updateComplexVariantWithNetworkAction(IndependantComplexVariant complexVariantToUpdate, IndependantComplexVariant refComplexVariant, String contingencyId) {

        IndependantComplexVariant temporaryClone = (IndependantComplexVariant) refComplexVariant.clone();
        temporaryClone.getActionsSet().get(0).getAfterCOList().getAfterCOId().clear();
        temporaryClone.getActionsSet().get(0).getAfterCOList().getAfterCOId().add(contingencyId);
        complexVariantToUpdate.getActionsSet().add(temporaryClone.getActionsSet().get(0));
    }

    private static void updateComplexVariantWithPstAction(IndependantComplexVariant complexVariantToUpdate, IndependantComplexVariant refComplexVariant, String contingencyId, int tapPosition) {

        IndependantComplexVariant temporaryClone = (IndependantComplexVariant) refComplexVariant.clone();

        temporaryClone.getActionsSet().get(0).getAfterCOList().getAfterCOId().clear();
        temporaryClone.getActionsSet().get(0).getAfterCOList().getAfterCOId().add(contingencyId);

        ObjectFactory objectFactory = new ObjectFactory();
        // remove range and relative range
        List<ActionType> actions = temporaryClone.getActionsSet().get(0).getAction(); //only one actions set
        ActionType newAction = objectFactory.createActionType();
        newAction.setType(actions.get(0).getType()); //only one action
        newAction.getContent().add(actions.get(0).getContent().get(0)); //white space
        newAction.getContent().add(actions.get(0).getContent().get(1)); //only keep branch
        newAction.getContent().add(actions.get(0).getContent().get(actions.get(0).getContent().size() - 1)); //last white space
        actions.clear();
        actions.add(newAction);

        // update complex variant value with tapPosition value
        List<Serializable> contents = temporaryClone.getActionsSet().get(0).getAction().get(0).getContent();
        //indentation is not correct when <value> is added, need to remove contents.last and add contents.first to have the correct indentation
        Serializable lastLine = contents.get(contents.size() - 1);
        contents.remove(contents.size() - 1); //remove last line
        contents.add(contents.get(0)); //white space line
        contents.add(objectFactory.createActionTypeValue(String.valueOf(tapPosition))); //value content
        contents.add(lastLine); //last white space

        complexVariantToUpdate.getActionsSet().add(temporaryClone.getActionsSet().get(0));
    }

    private static Map<State, String> getUIDOfStatesWithCra(CracCreationContext cracCreationContext, RaoResult raoResult, String instant) {

        int hour = OffsetDateTime.parse(instant).getHour();
        int uniqueIdIterator = 1;
        Map<State, String> stateMap = new HashMap<>();

        for (State state : cracCreationContext.getCrac().getStates(Instant.CURATIVE)) {
            if (!raoResult.getActivatedNetworkActionsDuringState(state).isEmpty() || !raoResult.getActivatedRangeActionsDuringState(state).isEmpty()) {
                stateMap.put(state, String.format("CRA_%02d%04d", hour, uniqueIdIterator));
                uniqueIdIterator++;
            }
        }
        return stateMap;
    }

    private static Map<String, CriticalBranchType> getCriticalBranchesForInstant(OffsetDateTime offsetDateTime, FbConstraint nativeCrac) {

        Map<String, CriticalBranchType> nativeCbs = new HashMap<>();

        for (CriticalBranchType cb : nativeCrac.getDocument().getCriticalBranches().getCriticalBranch()) {
            if (!nativeCbs.containsKey(cb.getId())) {
                nativeCbs.put(cb.getId(), cb);
            } else if (IntervalUtil.isInTimeInterval(offsetDateTime, cb.getTimeInterval().getV())) {
                nativeCbs.put(cb.getId(), cb);
            }
        }
        return nativeCbs;
    }

    private static Map<String, IndependantComplexVariant> getComplexVariantsForInstant(OffsetDateTime offsetDateTime, FbConstraint nativeCrac) {

        Map<String, IndependantComplexVariant> nativeVariants = new HashMap<>();

        for (IndependantComplexVariant cv : nativeCrac.getDocument().getComplexVariants().getComplexVariant()) {
            if (!nativeVariants.containsKey(cv.getId())) {
                nativeVariants.put(cv.getId(), cv);
            } else if (IntervalUtil.isInTimeInterval(offsetDateTime, cv.getTimeInterval().getV())) {
                nativeVariants.put(cv.getId(), cv);
            }
        }
        return nativeVariants;
    }
}
