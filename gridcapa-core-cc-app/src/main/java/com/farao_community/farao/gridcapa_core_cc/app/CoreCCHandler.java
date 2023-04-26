/*
 * Copyright (c) 2023, RTE (http://www.rte-france.com)
 *  This Source Code Form is subject to the terms of the Mozilla Public
 *  License, v. 2.0. If a copy of the MPL was not distributed with this
 *  file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package com.farao_community.farao.gridcapa_core_cc.app;

import com.farao_community.farao.data.crac_creation.creator.fb_constraint.crac_creator.FbConstraintCreationContext;
import com.farao_community.farao.data.rao_result_api.RaoResult;
import com.farao_community.farao.data.refprog.reference_program.ReferenceProgram;
import com.farao_community.farao.gridcapa_core_cc.api.exception.CoreCCInternalException;
import com.farao_community.farao.gridcapa_core_cc.api.exception.CoreCCRaoException;
import com.farao_community.farao.gridcapa_core_cc.api.resource.CoreCCRequest;
import com.farao_community.farao.gridcapa_core_cc.api.resource.CoreCCResponse;
import com.farao_community.farao.gridcapa_core_cc.app.services.FileExporter;
import com.farao_community.farao.gridcapa_core_cc.app.services.FileImporter;
import com.farao_community.farao.gridcapa_core_cc.app.services.results_export.ResultType;
import com.farao_community.farao.minio_adapter.starter.MinioAdapter;
import com.farao_community.farao.rao_api.parameters.RaoParameters;
import com.farao_community.farao.rao_runner.api.resource.RaoRequest;
import com.farao_community.farao.rao_runner.api.resource.RaoResponse;
import com.powsybl.glsk.api.GlskDocument;
import com.powsybl.glsk.commons.ZonalData;
import com.powsybl.iidm.modification.scalable.Scalable;
import com.powsybl.iidm.network.Network;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

/**
 * @author Ameni Walha {@literal <ameni.walha at rte-france.com>}
 * @author Theo Pascoli {@literal <theo.pascoli at rte-france.com>}
 * @author Vincent Bochet {@literal <vincent.bochet at rte-france.com>}
 */
@Component
public class CoreCCHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(CoreCCHandler.class);
    private static final DateTimeFormatter ARTIFACTS_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMdd_HHmm");
    private static final DateTimeFormatter TIMESTAMP_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd' 'HH:mm");

    private final Logger eventsLogger;
    private final FileExporter fileExporter;
    private final FileImporter fileImporter;
    private final MinioAdapter minioAdapter;

    public CoreCCHandler(FileImporter fileImporter, FileExporter fileExporter, MinioAdapter minioAdapter, Logger eventsLogger) {
        this.fileImporter = fileImporter;
        this.fileExporter = fileExporter;
        this.minioAdapter = minioAdapter;
        this.eventsLogger = eventsLogger;
    }

    public CoreCCResponse handleCoreCCRequest(CoreCCRequest coreCCRequest) {
        final String formattedTimestamp = setUpEventLogging(coreCCRequest);

        try {
            Instant computationStartInstant = Instant.now();
            List<HourlyRaoResult> studyPointResults = computeStudyPoints(coreValidRequest, network, cracCreationContext, formattedTimestamp);
            Instant computationEndInstant = Instant.now();

            Map<ResultType, String> resultFileUrls = postTreatment(studyPointResults, coreValidRequest, cracCreationContext, formattedTimestamp);

            return new CoreValidResponse(coreValidRequest.getId(), resultFileUrls.get(ResultType.MAIN_RESULT), resultFileUrls.get(ResultType.REX_RESULT), resultFileUrls.get(ResultType.REMEDIAL_ACTIONS_RESULT), computationStartInstant, computationEndInstant);
        } catch (InterruptedException e) {
            eventsLogger.error("Error during core request running for timestamp {}.", formattedTimestamp);
            Thread.currentThread().interrupt();
            throw new CoreValidInternalException(String.format("Error during core request running for timestamp '%s'", coreValidRequest.getTimestamp()), e);
        } catch (ExecutionException e) {
            eventsLogger.error("Error during core request running for timestamp {}.", formattedTimestamp);
            throw new CoreValidInternalException(String.format("Error during core request running for timestamp '%s'", coreValidRequest.getTimestamp()), e);
        }




        try {
            CoreCCPreProcessor coreCCPreProcessor = new CoreCCPreProcessor();
            minioAdapter.createBucketIfDoesNotExist();
            RaoIntegrationTask raoIntegrationTask = new RaoIntegrationTask();
            Path targetTempPath = Paths.get(raoIntegrationTask.getTmpInputsPath()); //NOSONAR
            Path raoRequestP = minioAdapter.copyFileInTargetSystemPath(raoRequestMinioObjectName, targetTempPath);
            Path cracP = minioAdapter.copyFileInTargetSystemPath(cracMinioObjectName, targetTempPath);
            Path cgmsZipP = minioAdapter.copyFileInTargetSystemPath(cgmsZipMinioObjectName, targetTempPath);
            Path cgmsUnZippedTempFolderPath = Paths.get(raoIntegrationTask.getTmpCgmInputsPath()); //NOSONAR
            ZipUtil.unzipFile(cgmsZipP, cgmsUnZippedTempFolderPath);
            ZipUtil.deletePath(cgmsZipP);
            Path virtualHubsP = minioAdapter.copyFileInTargetSystemPath(virtualHubsMinioObjectName, targetTempPath);

            String preSignedCracXmlUrl = minioAdapter.generatePreSignedUrl(cracMinioObjectName);
            String raoRequestFileName = minioAdapter.getFileNameFromUrl(preSignedCracXmlUrl);
            raoIntegrationTask.setRaoRequestFileName(raoRequestFileName);
            raoIntegrationTask.setInputCracXmlFileUrl(preSignedCracXmlUrl);
            repository.save(raoIntegrationTask);
            preprocessService.initializeTaskFromAutomatedLaunch(raoIntegrationTask, targetMinioFolder, raoRequestP, cracP, cgmsUnZippedTempFolderPath, virtualHubsP, refProgPreSignedUrl, glskPreSignedUrl);

            repository.save(raoIntegrationTask);
            raoIntegrationService.runTaskAsynchronouslyAutomatically(raoIntegrationTask, targetMinioFolder);

            coreCCPreProcessor.initializeTaskFromAutomatedLaunch(coreCCRequest);



            Network network = fileImporter.importNetwork(coreCCRequest.getCgm());
            FbConstraintCreationContext cracCreationContext = fileImporter.importCrac(coreCCRequest.getCbcora().getUrl(), coreCCRequest.getTimestamp(), network);

            Instant computationStartInstant = Instant.now();
            List<StudyPointResult> studyPointResults = computeStudyPoints(coreCCRequest, network, cracCreationContext, formattedTimestamp);
            Instant computationEndInstant = Instant.now();

            Map<ResultType, String> resultFileUrls = postTreatment(studyPointResults, coreCCRequest, cracCreationContext, formattedTimestamp);

            return new CoreCCResponse(coreCCRequest.getId(), resultFileUrls.get(ResultType.MAIN_RESULT), resultFileUrls.get(ResultType.REX_RESULT), resultFileUrls.get(ResultType.REMEDIAL_ACTIONS_RESULT), computationStartInstant, computationEndInstant);
        } catch (InterruptedException e) {
            eventsLogger.error("Error during core request running for timestamp {}.", formattedTimestamp);
            Thread.currentThread().interrupt();
            throw new CoreCCInternalException(String.format("Error during core request running for timestamp '%s'", coreCCRequest.getTimestamp()), e);
        } catch (ExecutionException e) {
            eventsLogger.error("Error during core request running for timestamp {}.", formattedTimestamp);
            throw new CoreCCInternalException(String.format("Error during core request running for timestamp '%s'", coreCCRequest.getTimestamp()), e);
        }
    }

    private static String setUpEventLogging(CoreCCRequest coreCCRequest) {
        MDC.put("gridcapa-task-id", coreCCRequest.getId());
        return TIMESTAMP_FORMATTER.format(coreCCRequest.getTimestamp());
    }

    private List<StudyPointResult> computeStudyPoints(CoreCCRequest coreCCRequest, Network network, FbConstraintCreationContext cracCreationContext, String formattedTimestamp) throws InterruptedException, ExecutionException {
        Map<StudyPoint, RaoRequest> studyPointRaoRequests = new HashMap<>();
        Map<StudyPoint, CompletableFuture<RaoResponse>> studyPointCompletableFutures = new HashMap<>();
        List<StudyPointResult> studyPointResults = new ArrayList<>();

        List<StudyPoint> studyPoints = fileImporter.importStudyPoints(coreCCRequest.getRaoRequest(), coreCCRequest.getTimestamp());
        if (!studyPoints.isEmpty()) {
            StudyPointData studyPointData = fillStudyPointData(coreCCRequest, network, cracCreationContext);
            studyPoints.forEach(studyPoint -> studyPointRaoRequests.put(studyPoint, studyPointService.computeStudyPointShift(studyPoint, studyPointData, coreCCRequest.getTimestamp(), coreCCRequest.getId())));
            eventsLogger.info("All studypoints shifts are done for timestamp {}", formattedTimestamp);
            runRaoForEachStudyPoint(studyPointRaoRequests, studyPointCompletableFutures);
            studyPointResults = fillResultsForEachStudyPoint(studyPointData, studyPointCompletableFutures);
        }
        return studyPointResults;
    }

    private StudyPointData fillStudyPointData(CoreCCRequest coreCCRequest, Network network, FbConstraintCreationContext cracCreationContext) {
        ReferenceProgram referenceProgram = fileImporter.importReferenceProgram(coreCCRequest.getRefProg(), coreCCRequest.getTimestamp());
        Map<String, Double> coreNetPositions = NetPositionsHandler.computeCoreReferenceNetPositions(referenceProgram);
        GlskDocument glskDocument = fileImporter.importGlskFile(coreCCRequest.getGlsk());
        ZonalData<Scalable> scalableZonalData = glskDocument.getZonalScalable(network, coreCCRequest.getTimestamp().toInstant());
        String jsonCracUrl = fileExporter.saveCracInJsonFormat(cracCreationContext.getCrac(), coreCCRequest.getTimestamp());
        RaoParameters raoParameters = RaoParameters.load();
        String raoParametersUrl = fileExporter.saveRaoParametersAndGetUrl(raoParameters);
        return new StudyPointData(network, coreNetPositions, scalableZonalData, cracCreationContext, jsonCracUrl, raoParametersUrl);
    }

    private void runRaoForEachStudyPoint(Map<StudyPoint, RaoRequest> studyPointRaoRequests, Map<StudyPoint, CompletableFuture<RaoResponse>> studyPointCompletableFutures) throws ExecutionException, InterruptedException {
        studyPointRaoRequests.forEach((studyPoint, raorequest) -> {
            CompletableFuture<RaoResponse> raoResponse = studyPointService.computeStudyPointRao(studyPoint, raorequest);
            studyPointCompletableFutures.put(studyPoint, raoResponse);
            raoResponse.thenApply(raoResponse1 -> {
                LOGGER.info("End of RAO for studypoint {} ...", studyPoint.getVerticeId());
                return null;
            }).exceptionally(exception -> {
                studyPoint.getStudyPointResult().setStatusToError();
                eventsLogger.error("Error during RAO computation for studypoint {}.", studyPoint.getVerticeId());
                throw new CoreCCRaoException(String.format("Error during RAO computation for studypoint %s .", studyPoint.getVerticeId()));
            });
        });
        CompletableFuture.allOf(studyPointCompletableFutures.values().toArray(new CompletableFuture[0])).get();
    }

    private List<StudyPointResult> fillResultsForEachStudyPoint(StudyPointData studyPointData, Map<StudyPoint, CompletableFuture<RaoResponse>> studyPointCompletableFutures) throws InterruptedException, ExecutionException {
        List<StudyPointResult> studyPointResults = new ArrayList<>();
        for (Map.Entry<StudyPoint, CompletableFuture<RaoResponse>> entry : studyPointCompletableFutures.entrySet()) {
            StudyPoint studyPoint = entry.getKey();
            RaoResponse raoResponse = entry.getValue().get();
            Network networkWithPra = fileImporter.importNetworkFromUrl(raoResponse.getNetworkWithPraFileUrl());
            String fileName = networkWithPra.getNameOrId() + "_" + studyPoint.getVerticeId() + "_withPra.uct";
            fileExporter.saveShiftedCgmWithPra(networkWithPra, fileName);
            studyPointResults.add(studyPointService.postTreatRaoResult(studyPoint, studyPointData, raoResponse));
        }
        return studyPointResults;
    }

    private Map<ResultType, String> postTreatment(List<StudyPointResult> studyPointResults, CoreCCRequest coreCCRequest, FbConstraintCreationContext cracCreationContext, String formattedTimestamp) {
        Map<ResultType, String> resultFileUrls = saveProcessOutputs(studyPointResults, coreCCRequest, cracCreationContext);
        if (coreCCRequest.getLaunchedAutomatically()) {
            deleteArtifacts(coreCCRequest);
        }
        eventsLogger.info("Process done for timestamp {}.", formattedTimestamp);
        return resultFileUrls;
    }

    private Map<ResultType, String> saveProcessOutputs(List<StudyPointResult> studyPointResults, CoreCCRequest coreCCRequest, FbConstraintCreationContext cracCreationContext) {
        return fileExporter.exportStudyPointResult(studyPointResults, coreCCRequest, cracCreationContext);
    }

    private void deleteArtifacts(CoreCCRequest coreCCRequest) {
        deleteCgmBeforeRao(ARTIFACTS_FORMATTER.format(coreCCRequest.getTimestamp().atZoneSameInstant(ZoneId.of("Europe/Paris"))));
    }

    private void deleteCgmBeforeRao(String prefix) {
        List<String> results = minioAdapter.listFiles("artifacts/" + prefix);
        minioAdapter.deleteFiles(results);
    }
}
