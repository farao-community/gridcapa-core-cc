/*
 * Copyright (c) 2022, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package com.farao_community.farao.gridcapa.core_cc.app.entities;

import com.farao_community.farao.gridcapa.core_cc.app.exceptions.RaoIntegrationException;
import com.farao_community.farao.gridcapa.core_cc.app.util.FileUtil;

import javax.persistence.*;
import java.io.File;
import java.io.IOException;
import java.io.Serializable;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.HashSet;
import java.util.Set;

import static com.farao_community.farao.gridcapa.core_cc.app.util.FileUtil.createTempDirectory;

@Entity
public class RaoIntegrationTask implements Serializable {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private long taskId;

    private int version;

    private TaskStatus taskStatus;

    private String correlationId;

    private String timeInterval;

    private String tmpInputsPath;

    private String tmpCgmInputsPath;

    private String raoRequestFileName;

    @Column(columnDefinition = "TEXT")
    private String inputCracXmlFileUrl;

    @ElementCollection(fetch = FetchType.EAGER)
    Set<HourlyRaoRequest> hourlyRaoRequests = new HashSet<>();

    @ElementCollection(fetch = FetchType.EAGER)
    Set<HourlyRaoResult> hourlyRaoResults = new HashSet<>();

    @OneToOne(cascade = {CascadeType.ALL})
    private DailyOutputs dailyOutputs = new DailyOutputs();

    private Instant inputsReceptionInstant;

    private Instant computationStartInstant = Instant.ofEpochSecond(0);

    private Instant computationEndInstant = Instant.ofEpochSecond(0);

    private Instant outputsSendingInstant = Instant.ofEpochSecond(0);

    public RaoIntegrationTask() {
        try {
            this.tmpInputsPath = createTempDirectory("rao-integration-temp-dir").toString();
            this.tmpCgmInputsPath = FileUtil.setFilePermissions(Files.createDirectories(Paths.get(tmpInputsPath + File.separator + "cgm"))).toString();
            this.taskStatus = TaskStatus.CREATED;
            inputsReceptionInstant = Instant.now();
        } catch (IOException e) {
            throw new RaoIntegrationException("IO exception, cannot create temporary directory for pre processing, cause: " + e.getMessage(), e);
        }
    }

    public long getTaskId() {
        return taskId;
    }

    public void setTaskId(long taskId) {
        this.taskId = taskId;
    }

    public int getVersion() {
        return version;
    }

    public void setVersion(int version) {
        this.version = version;
    }

    public TaskStatus getTaskStatus() {
        return taskStatus;
    }

    public void setTaskStatus(TaskStatus taskStatus) {
        this.taskStatus = taskStatus;
    }

    public Set<HourlyRaoRequest> getHourlyRaoRequests() {
        return hourlyRaoRequests;
    }

    public void setHourlyRaoRequests(Set<HourlyRaoRequest> hourlyInputs) {
        this.hourlyRaoRequests = hourlyInputs;
    }

    public Set<HourlyRaoResult> getHourlyRaoResults() {
        return hourlyRaoResults;
    }

    public void setHourlyRaoResults(Set<HourlyRaoResult> hourlyArtifacts) {
        this.hourlyRaoResults = hourlyArtifacts;
    }

    public String getCorrelationId() {
        return correlationId;
    }

    public void setCorrelationId(String correlationId) {
        this.correlationId = correlationId;
    }

    public DailyOutputs getDailyOutputs() {
        return dailyOutputs;
    }

    public void setDailyOutputs(DailyOutputs dailyOutputs) {
        this.dailyOutputs = dailyOutputs;
    }

    public String getTimeInterval() {
        return timeInterval;
    }

    public void setTimeInterval(String timeInterval) {
        this.timeInterval = timeInterval;
    }

    public String getInputCracXmlFileUrl() {
        return inputCracXmlFileUrl;
    }

    public void setInputCracXmlFileUrl(String inputCracXmlFileUrl) {
        this.inputCracXmlFileUrl = inputCracXmlFileUrl;
    }

    public String getTmpInputsPath() {
        return tmpInputsPath;
    }

    public String getTmpCgmInputsPath() {
        return tmpCgmInputsPath;
    }

    public HourlyRaoRequest getHourlyRequestFromResponse(HourlyRaoResult hourlyRaoResponse) {
        return this.getHourlyRaoRequests().stream().filter(request -> request.getInstant().equals(hourlyRaoResponse.getInstant()))
                .findFirst().orElseThrow(() -> new RaoIntegrationException(String.format("Rao response not found for timestamp %s .", hourlyRaoResponse.getInstant())));
    }

    public Instant getInputsReceptionInstant() {
        return inputsReceptionInstant;
    }

    public void setInputsReceptionInstant(Instant inputsReceptionInstant) {
        this.inputsReceptionInstant = inputsReceptionInstant;
    }

    public Instant getComputationStartInstant() {
        return computationStartInstant;
    }

    public void setComputationStartInstant(Instant computationStartInstant) {
        this.computationStartInstant = computationStartInstant;
    }

    public Instant getComputationEndInstant() {
        return computationEndInstant;
    }

    public void setComputationEndInstant(Instant computationEndInstant) {
        this.computationEndInstant = computationEndInstant;
    }

    public Instant getOutputsSendingInstant() {
        return outputsSendingInstant;
    }

    public void setOutputsSendingInstant(Instant outputsSendingInstant) {
        this.outputsSendingInstant = outputsSendingInstant;
    }

    public String getRaoRequestFileName() {
        return raoRequestFileName;
    }

    public void setRaoRequestFileName(String raoRequestFileName) {
        this.raoRequestFileName = raoRequestFileName;
    }
}
