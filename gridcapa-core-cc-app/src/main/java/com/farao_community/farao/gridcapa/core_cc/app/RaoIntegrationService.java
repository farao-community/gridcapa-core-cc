/*
 * Copyright (c) 2022, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package com.farao_community.farao.gridcapa.core_cc.app;

import com.farao_community.farao.gridcapa.core_cc.app.entities.RaoIntegrationRepository;
import com.farao_community.farao.gridcapa.core_cc.app.entities.RaoIntegrationTask;
import com.farao_community.farao.gridcapa.core_cc.app.entities.TaskStatus;
import com.farao_community.farao.gridcapa.core_cc.app.exceptions.RaoIntegrationException;
import com.farao_community.farao.gridcapa.core_cc.app.messaging.MinioAdapter;
import com.farao_community.farao.gridcapa.core_cc.app.preprocessing.RaoIPreProcessService;
import com.farao_community.farao.gridcapa.core_cc.app.util.ZipUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

import static com.farao_community.farao.gridcapa.core_cc.app.util.FileUtil.createTempDirectory;

/**
 * @author Pengbo Wang {@literal <pengbo.wang at rte-international.com>}
 * @author Mohamed BenRejeb {@literal <mohamed.ben-rejeb at rte-france.com>}
 */
@Service
public class RaoIntegrationService {

    private static final Logger LOGGER = LoggerFactory.getLogger(RaoIntegrationService.class);

    private final RaoIntegrationRepository repository;

    private final RaoClient raoClient;
    private final MinioAdapter minioAdapter;
    private final RaoIPreProcessService raoIPreprocessService;

    public RaoIntegrationService(RaoIntegrationRepository repository, RaoClient raoClient,
                                 RaoIPreProcessService raoIPreprocessService, MinioAdapter minioAdapter) {
        this.repository = repository;
        this.raoClient = raoClient;
        this.raoIPreprocessService = raoIPreprocessService;
        this.minioAdapter = minioAdapter;
    }

    public RaoIntegrationTask createRaoIntegrationTask(MultipartFile inputFilesArchive) {
        RaoIntegrationTask raoIntegrationTask = new RaoIntegrationTask();
        repository.save(raoIntegrationTask);
        try {
            raoIPreprocessService.initializeTaskFromZipArchive(raoIntegrationTask, inputFilesArchive);
            repository.save(raoIntegrationTask);
            LOGGER.info("Rao integration task created with id: '{}'", raoIntegrationTask.getTaskId());
            return raoIntegrationTask;
        } catch (Exception e) {
            repository.delete(raoIntegrationTask);
            String errorMessage = "Error during Rao integration task creation. Cause: " + e.getMessage();
            LOGGER.error(errorMessage);
            throw new RaoIntegrationException(errorMessage);
        }
    }

    public RaoIntegrationTask runRaoAsynchronously(MultipartFile inputFilesArchive) {
        RaoIntegrationTask raoIntegrationTask = createRaoIntegrationTask(inputFilesArchive);
        startTaskAsync(raoIntegrationTask.getTaskId());
        return raoIntegrationTask;
    }

    public RaoIntegrationTask getEntityByTaskId(long taskId) {
        return repository.findById(taskId).orElseThrow(() -> new RaoIntegrationException("Task not available"));
    }

    public void runTaskAsynchronouslyAutomatically(RaoIntegrationTask raoIntegrationTask, String targetMinioOutputsFolder) {
        raoIntegrationTask.setTaskStatus(TaskStatus.RUNNING);
        LOGGER.info("Run RAO integration for task ID: '{}'", raoIntegrationTask.getTaskId());
        raoClient.startRunningAllRaoHourlyInputsAsync(raoIntegrationTask, targetMinioOutputsFolder, false);
    }

    public void startTaskAsync(long taskId) {
        RaoIntegrationTask raoIntegrationTask = getEntityByTaskId(taskId);
        raoIntegrationTask.setTaskStatus(TaskStatus.RUNNING);
        repository.save(raoIntegrationTask);
        LOGGER.info("Run RAO integration for task ID: '{}'", taskId);
        String targetMinioFolder = String.valueOf(raoIntegrationTask.getTaskId());
        raoClient.startRunningAllRaoHourlyInputsAsync(raoIntegrationTask, targetMinioFolder, true);
    }

    public RaoIntegrationTask checkAndGetTask(long taskId) {
        return repository.findById(taskId).orElseThrow(() -> new RaoIntegrationException("Task not available"));
    }

    public byte[] getDailyResultsZip(long taskId) {
        try {
            RaoIntegrationTask raoIntegrationTask = checkAndGetTask(taskId);
            if (raoIntegrationTask.getTaskStatus().equals(TaskStatus.RUNNING)) {
                throw new RaoIntegrationException("Task still running, cant retrieve outputs");
            }
            Path archiveTempPath = createTempDirectory("rao-integration-temp-results-dir");

            if (raoIntegrationTask.getTaskStatus() == TaskStatus.ERROR) {
                //check raoIntegrationTask status, when finish with error, return only rao response file, this is case when input inconsistency.
                String raoIntegrationResponseFileUrl = minioAdapter.generatePreSignedUrl(raoIntegrationTask.getDailyOutputs().getRaoIntegrationResponsePath());

                try (InputStream inputStreamRaoResponse = minioAdapter.getInputStreamFromUrl(raoIntegrationResponseFileUrl)) {
                    File targetRaoResponseFile = new File(archiveTempPath.toString(), minioAdapter.getFileNameFromUrl(raoIntegrationResponseFileUrl)); //NOSONAR
                    Files.copy(inputStreamRaoResponse, targetRaoResponseFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
                }

                byte[] zipResult = ZipUtil.zipDirectory(archiveTempPath.toString());
                ZipUtil.deletePath(archiveTempPath);
                return zipResult;
            }

            //raoi response
            String raoIntegrationResponseFileUrl = minioAdapter.generatePreSignedUrl(raoIntegrationTask.getDailyOutputs().getRaoIntegrationResponsePath());
            try (InputStream inputStreamRaoResponse = minioAdapter.getInputStreamFromUrl(raoIntegrationResponseFileUrl)) {
                File targetRaoResponseFile = new File(archiveTempPath.toString(), minioAdapter.getFileNameFromUrl(raoIntegrationResponseFileUrl)); //NOSONAR
                Files.copy(inputStreamRaoResponse, targetRaoResponseFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
            }

            //daily FbDocument
            String dailyFbConstraintDocumentFileUrl = minioAdapter.generatePreSignedUrl(raoIntegrationTask.getDailyOutputs().getFlowBasedConstraintDocumentPath());
            try (InputStream inputStreamDailyFbConstraintDocument = minioAdapter.getInputStreamFromUrl(dailyFbConstraintDocumentFileUrl)) {
                File targetFbConstraintFile = new File(archiveTempPath.toString(), minioAdapter.getFileNameFromUrl(dailyFbConstraintDocumentFileUrl)); //NOSONAR
                Files.copy(inputStreamDailyFbConstraintDocument, targetFbConstraintFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
            }

            //cne's zip

            String cnesZipMinioUrl = minioAdapter.generatePreSignedUrl(raoIntegrationTask.getDailyOutputs().getCnesZipPath());
            try (InputStream inputStreamCnesZip = minioAdapter.getInputStreamFromUrl(cnesZipMinioUrl)) {
                File targetCnesZipFile = new File(archiveTempPath.toString(), minioAdapter.getFileNameFromUrl(cnesZipMinioUrl)); //NOSONAR
                Files.copy(inputStreamCnesZip, targetCnesZipFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
            }

            //cgm's zip
            String cgmsZipMinioUrl = minioAdapter.generatePreSignedUrl(raoIntegrationTask.getDailyOutputs().getCgmsZipPath());
            try (InputStream inputStreamCgmsZip = minioAdapter.getInputStreamFromUrl(cgmsZipMinioUrl)) {
                File targetCgmsZipFile = new File(archiveTempPath.toString(), minioAdapter.getFileNameFromUrl(cgmsZipMinioUrl)); //NOSONAR
                Files.copy(inputStreamCgmsZip, targetCgmsZipFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
            }

            byte[] zipResult = ZipUtil.zipDirectory(archiveTempPath.toString());
            ZipUtil.deletePath(archiveTempPath);
            return zipResult;
        } catch (IOException e) {
            throw new RaoIntegrationException("exception occurred during zip outputs creation: " + e.getMessage());
        }
    }

}
