/*
 * Copyright (c) 2021, RTE (http://www.rte-france.com)
 */
package com.farao_community.farao.gridcapa_core_cc.app.postprocessing;

import com.farao_community.farao.data.crac_creation.creator.fb_constraint.xsd.FlowBasedConstraintDocument;
import com.farao_community.farao.gridcapa_core_cc.api.exception.CoreCCInternalException;
import com.farao_community.farao.gridcapa_core_cc.api.resource.InternalCoreCCRequest;
import com.farao_community.farao.gridcapa_core_cc.app.util.JaxbUtil;
import com.farao_community.farao.gridcapa_core_cc.app.util.ZipUtil;
import com.farao_community.farao.minio_adapter.starter.MinioAdapter;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Paths;
import java.time.Instant;

/**
 * @author Pengbo Wang {@literal <pengbo.wang at rte-international.com>}
 * @author Mohamed Ben Rejeb {@literal <mohamed.ben-rejeb at rte-france.com>}
 */
@Service
public class CoreCCPostProcessService {

    private final MinioAdapter minioAdapter;
    private final CoreCCXmlResponseGenerator coreCCXmlResponseGenerator;
    private final DailyF303Generator dailyF303Generator;
    private final FileExporterHelper fileExporterHelper;

    public CoreCCPostProcessService(MinioAdapter minioAdapter, CoreCCXmlResponseGenerator coreCCXmlResponseGenerator, DailyF303Generator dailyF303Generator, FileExporterHelper fileExporterHelper) {
        this.minioAdapter = minioAdapter;
        this.coreCCXmlResponseGenerator = coreCCXmlResponseGenerator;
        this.dailyF303Generator = dailyF303Generator;
        this.fileExporterHelper = fileExporterHelper;
    }

    public String postProcessHourlyResults(InternalCoreCCRequest coreCCRequest, boolean isManualRun) {
        String targetMinioFolder = coreCCRequest.getDestinationKey();
        String outputsTargetMinioFolder = targetMinioFolder.replace("RAO_WORKING_DIR/", "RAO_OUTPUTS_DIR/");
        renameRaoHourlyResultsAndSendToDailyOutputs(coreCCRequest, outputsTargetMinioFolder);
        FlowBasedConstraintDocument dailyFlowBasedConstraintDocument = dailyF303Generator.generate(coreCCRequest);
        uploadDailyOutputFlowBasedConstraintDocument(coreCCRequest, dailyFlowBasedConstraintDocument, outputsTargetMinioFolder, isManualRun);
        String responsePath = coreCCXmlResponseGenerator.generateRaoResponse(coreCCRequest, outputsTargetMinioFolder); //f305 rao response
        coreCCRequest.setOutputsSentInstant(Instant.now());
        coreCCRequest.setStatus(InternalCoreCCRequest.Status.SUCCESS); // status success should be set before exportMetadataFile because it's displayed within it
        fileExporterHelper.exportMetadataFile(coreCCRequest, outputsTargetMinioFolder, isManualRun);
        return responsePath;
    }

    public void renameRaoHourlyResultsAndSendToDailyOutputs(InternalCoreCCRequest coreCCRequest, String targetMinioFolder) {
        String networksArchiveTempPath = coreCCRequest.getDailyOutputs().getNetworkTmpOutputsPath();
        String cneArchiveTempPath = coreCCRequest.getDailyOutputs().getCneTmpOutputsPath();
        String logsArchiveTempPath = coreCCRequest.getDailyOutputs().getLogsTmpOutputPath();

        coreCCXmlResponseGenerator.generateCgmXmlHeaderFile(coreCCRequest, networksArchiveTempPath); // add cgm xml header to networks folder

        // Zip CGMS
        byte[] cgmsZipResult = ZipUtil.zipDirectory(networksArchiveTempPath);
        String targetCgmsFolderName = OutputFileNameUtil.generateCgmZipName(coreCCRequest);
        String targetCgmsFolderPath = OutputFileNameUtil.generateOutputsDestinationPath(targetMinioFolder, targetCgmsFolderName);
        try (InputStream cgmZipIs = new ByteArrayInputStream(cgmsZipResult)) {
            minioAdapter.uploadOutput(targetCgmsFolderPath, cgmZipIs);
            coreCCRequest.getDailyOutputs().setCgmsZipPath(targetCgmsFolderPath);
        } catch (IOException e) {
            throw new CoreCCInternalException(String.format("Exception occurred while zipping CGMs of task %s", coreCCRequest.getId()));
        } finally {
            ZipUtil.deletePath(Paths.get(networksArchiveTempPath)); //NOSONAR
        }

        // Zip CNE
        byte[] cneZipResult = ZipUtil.zipDirectory(cneArchiveTempPath);
        String targetCneFolderName = OutputFileNameUtil.generateCneZipName(coreCCRequest);
        String targetCneFolderPath = OutputFileNameUtil.generateOutputsDestinationPath(targetMinioFolder, targetCneFolderName);

        try (InputStream cneZipIs = new ByteArrayInputStream(cneZipResult)) {
            minioAdapter.uploadOutput(targetCneFolderPath, cneZipIs);
            coreCCRequest.getDailyOutputs().setCnesZipPath(targetCneFolderPath);
        } catch (IOException e) {
            throw new CoreCCInternalException(String.format("Exception occurred while zipping CNEs of task %s", coreCCRequest.getId()));
        } finally {
            ZipUtil.deletePath(Paths.get(cneArchiveTempPath)); //NOSONAR
        }

        // Zip logs
        byte[] logsZipResult = ZipUtil.zipDirectory(logsArchiveTempPath);
        String targetLogsFolderName = OutputFileNameUtil.generateLogsZipName(coreCCRequest);
        String targetLogsFolderPath = OutputFileNameUtil.generateOutputsDestinationPath(targetMinioFolder, targetLogsFolderName);
        try (InputStream logsZipIs = new ByteArrayInputStream(logsZipResult)) {
            minioAdapter.uploadOutput(targetLogsFolderPath, logsZipIs);
            coreCCRequest.getDailyOutputs().setLogsZipPath(targetLogsFolderPath);
        } catch (IOException e) {
            throw new CoreCCInternalException(String.format("Exception occurred while zipping logs of task %s", coreCCRequest.getId()));
        } finally {
            ZipUtil.deletePath(Paths.get(logsArchiveTempPath));
        }
    }

    void uploadDailyOutputFlowBasedConstraintDocument(InternalCoreCCRequest coreCCRequest, FlowBasedConstraintDocument dailyFbDocument, String targetMinioFolder, boolean isManualRun) {
        byte[] dailyFbConstraint = JaxbUtil.writeInBytes(FlowBasedConstraintDocument.class, dailyFbDocument);
        String fbConstraintFileName = OutputFileNameUtil.generateOptimizedCbFileName(coreCCRequest);
        String fbConstraintDestinationPath = OutputFileNameUtil.generateOutputsDestinationPath(targetMinioFolder, fbConstraintFileName);

        try (InputStream dailyFbIs = new ByteArrayInputStream(dailyFbConstraint)) {
            minioAdapter.uploadOutput(fbConstraintDestinationPath, dailyFbIs);
        } catch (IOException e) {
            throw new CoreCCInternalException(String.format("Exception occurred while uploading F303 file of task %s", coreCCRequest.getId()));
        }

        coreCCRequest.getDailyOutputs().setFlowBasedConstraintDocumentPath(fbConstraintDestinationPath);
    }

}
