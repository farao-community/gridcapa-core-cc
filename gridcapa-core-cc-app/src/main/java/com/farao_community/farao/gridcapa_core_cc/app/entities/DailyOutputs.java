/*
 * Copyright (c) 2021, RTE (http://www.rte-france.com)
 */
package com.farao_community.farao.gridcapa_core_cc.app.entities;

import com.rte_france.farao.rao_integration.server.ApplicationStartupConfig;
import com.rte_france.farao.rao_integration.server.exceptions.RaoIntegrationException;
import org.apache.commons.lang3.RandomStringUtils;

import javax.persistence.*;
import java.io.IOException;
import java.io.Serializable;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.attribute.FileAttribute;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.Set;

/**
 * @author Mohamed BenRejeb {@literal <mohamed.ben-rejeb at rte-france.com>}
 */

@Entity
public class DailyOutputs implements Serializable {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private long ref;

    private String raoRequestAckPath;

    private String cgmsZipPath;

    private String cnesZipPath;

    private String logsZipPath;

    private String flowBasedConstraintDocumentPath;

    private String raoIntegrationResponsePath;

    private String metadataOutputsPath;

    private String outputCgmXmlHeaderMessageId;

    private String outputFlowBasedConstraintDocumentMessageId;

    private String cneTmpOutputsPath;
    private String networkTmpOutputsPath;
    private String logsTmpOutputPath;

    public DailyOutputs() {
        try {
            FileAttribute<Set<PosixFilePermission>> attr = PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rwx------"));
            this.cneTmpOutputsPath  = Files.createDirectories(Paths.get(ApplicationStartupConfig.getTaskTempOutputsDir(), "cnes", RandomStringUtils.randomAlphanumeric(8)), attr).toString();
            this.networkTmpOutputsPath = Files.createDirectories(Paths.get(ApplicationStartupConfig.getTaskTempOutputsDir(), "cgms", RandomStringUtils.randomAlphanumeric(8)), attr).toString();
            this.logsTmpOutputPath = Files.createDirectories(Paths.get(ApplicationStartupConfig.getTaskTempOutputsDir(), "logs", RandomStringUtils.randomAlphanumeric(8)), attr).toString();
        } catch (IOException e) {
            throw new RaoIntegrationException("IO exception, cannot create temporary directories for post processing, cause: " + e.getMessage(), e);
        }
    }

    public String getRaoRequestAckPath() {
        return raoRequestAckPath;
    }

    public void setRaoRequestAckPath(String raoRequestAckPath) {
        this.raoRequestAckPath = raoRequestAckPath;
    }

    public String getCneTmpOutputsPath() {
        return cneTmpOutputsPath;
    }

    public String getNetworkTmpOutputsPath() {
        return networkTmpOutputsPath;
    }

    public String getLogsTmpOutputPath() {
        return logsTmpOutputPath;
    }

    public String getCgmsZipPath() {
        return cgmsZipPath;
    }

    public void setCgmsZipPath(String cgmsZipPath) {
        this.cgmsZipPath = cgmsZipPath;
    }

    public String getCnesZipPath() {
        return cnesZipPath;
    }

    public void setCnesZipPath(String cnesZipPath) {
        this.cnesZipPath = cnesZipPath;
    }

    public String getLogsZipPath() {
        return logsZipPath;
    }

    public void setLogsZipPath(String logsZipPath) {
        this.logsZipPath = logsZipPath;
    }

    public String getFlowBasedConstraintDocumentPath() {
        return flowBasedConstraintDocumentPath;
    }

    public void setFlowBasedConstraintDocumentPath(String flowBasedConstraintDocumentPath) {
        this.flowBasedConstraintDocumentPath = flowBasedConstraintDocumentPath;
    }

    public String getRaoIntegrationResponsePath() {
        return raoIntegrationResponsePath;
    }

    public void setRaoIntegrationResponsePath(String raoIntegrationResponseUrl) {
        this.raoIntegrationResponsePath = raoIntegrationResponseUrl;
    }

    public String getOutputCgmXmlHeaderMessageId() {
        return outputCgmXmlHeaderMessageId;
    }

    public void setOutputCgmXmlHeaderMessageId(String outputCgmXmlHeaderMessageId) {
        this.outputCgmXmlHeaderMessageId = outputCgmXmlHeaderMessageId;
    }

    public String getOutputFlowBasedConstraintDocumentMessageId() {
        return outputFlowBasedConstraintDocumentMessageId;
    }

    public void setOutputFlowBasedConstraintDocumentMessageId(String outputFlowBasedConstraintDocumentMessageId) {
        this.outputFlowBasedConstraintDocumentMessageId = outputFlowBasedConstraintDocumentMessageId;
    }

    public String getMetadataOutputsPath() {
        return metadataOutputsPath;
    }

    public void setMetadataOutputsPath(String metadataOutputsPath) {
        this.metadataOutputsPath = metadataOutputsPath;
    }
}
