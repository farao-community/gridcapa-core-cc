/*
 * Copyright (c) 2021, RTE (http://www.rte-france.com)
 */
package com.farao_community.farao.gridcapa_core_cc.app.postprocessing;

import com.farao_community.farao.gridcapa_core_cc.api.exception.CoreCCInternalException;
import com.farao_community.farao.gridcapa_core_cc.api.resource.HourlyRaoResult;
import com.farao_community.farao.gridcapa_core_cc.api.resource.InternalCoreCCRequest;
import com.farao_community.farao.minio_adapter.starter.MinioAdapter;
import org.apache.commons.collections4.map.MultiKeyMap;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.time.temporal.ChronoUnit;

import static com.farao_community.farao.gridcapa_core_cc.app.util.RaoMetadata.Indicator.*;

/**
 * @author Peter Mitri {@literal <peter.mitri at rte-france.com>}
 */
public class CoreCCMetadataGenerator {

    private final MinioAdapter minioAdapter;

    public CoreCCMetadataGenerator(MinioAdapter minioAdapter) {
        this.minioAdapter = minioAdapter;
    }

    public void exportMetadataFile(InternalCoreCCRequest coreCCRequest, String targetMinioFolder, boolean isManualRun) {
        byte[] csv = generateMetadataCsv(coreCCRequest).getBytes();
        String metadataFileName = OutputFileNameUtil.generateMetadataFileName(coreCCRequest);
        String metadataDestinationPath = OutputFileNameUtil.generateOutputsDestinationPath(targetMinioFolder, metadataFileName);

        try (InputStream csvIs = new ByteArrayInputStream(csv)) {
            minioAdapter.uploadOutputForTimestamp(metadataDestinationPath, csvIs, "CORE_CC", "METADATA", coreCCRequest.getTimestamp());
        } catch (IOException e) {
            throw new CoreCCInternalException(String.format("Exception occurred while uploading metadata file of task %s", coreCCRequest.getId()));
        }
        coreCCRequest.getDailyOutputs().setMetadataOutputsPath(metadataDestinationPath);
    }

    private static String generateMetadataCsv(InternalCoreCCRequest coreCCRequest) {
        MultiKeyMap data = structureDataFromTask(coreCCRequest);
        return writeCsvFromMap(coreCCRequest, data);
    }

    private static MultiKeyMap structureDataFromTask(InternalCoreCCRequest coreCCRequest) {
        // Store data in a MultiKeyMap
        // First key is column (indicator)
        // Second key is timestamp (or whole business day)
        // Value is the value of the indicator for the given timestamp
        MultiKeyMap data = new MultiKeyMap<>();

        data.put(RAO_REQUESTS_RECEIVED, coreCCRequest.getTimestamp(), coreCCRequest.getRaoRequest().getFilename());
        data.put(RAO_REQUEST_RECEPTION_TIME, coreCCRequest.getTimestamp(), coreCCRequest.getInputsReceivedInstant().toString());
        data.put(RAO_OUTPUTS_SENT, coreCCRequest.getTimestamp(), coreCCRequest.getStatus().equals(InternalCoreCCRequest.Status.SUCCESS) ? "YES" : "NO");
        data.put(RAO_OUTPUTS_SENDING_TIME, coreCCRequest.getTimestamp(), coreCCRequest.getOutputsSentInstant().toString());
        HourlyRaoResult hourlyRaoResult = coreCCRequest.getHourlyRaoResult();
        data.put(RAO_START_TIME, hourlyRaoResult.getInstant(), hourlyRaoResult.getComputationStartInstant().toString());
        data.put(RAO_END_TIME, hourlyRaoResult.getInstant(), hourlyRaoResult.getComputationEndInstant().toString());
        data.put(RAO_COMPUTATION_TIME, hourlyRaoResult.getInstant(), String.valueOf(ChronoUnit.MINUTES.between(hourlyRaoResult.getComputationStartInstant(), hourlyRaoResult.getComputationEndInstant())));
        data.put(RAO_RESULTS_PROVIDED, hourlyRaoResult.getInstant(), hourlyRaoResult.getStatus().equals(HourlyRaoResult.Status.SUCCESS) ? "YES" : "NO");
        data.put(RAO_COMPUTATION_STATUS, hourlyRaoResult.getInstant(), hourlyRaoResult.getStatus().toString());
        return data;
    }

    private static String writeCsvFromMap(InternalCoreCCRequest coreCCRequest, MultiKeyMap data) {
        /*// Get headers for columns & lines
        List<RaoMetadata.Indicator> indicators = Arrays.stream(values())
                .sorted(Comparator.comparing(RaoMetadata.Indicator::getOrder))
                .collect(Collectors.toList());
        List<String> timestamps = coreCCRequest.getHourlyRaoResults().stream().map(HourlyRaoResult::getInstant).sorted(String::compareTo).collect(Collectors.toList());
        timestamps.add(0, coreCCRequest.getTimestamp().toString());

        // Generate CSV string
        char delimiter = ';';
        char cr = '\n';
        StringBuilder csvBuilder = new StringBuilder();
        csvBuilder.append(delimiter);
        csvBuilder.append(indicators.stream().map(RaoMetadata.Indicator::getCsvLabel).collect(Collectors.joining(";")));
        csvBuilder.append(cr);
        for (String timestamp : timestamps) {
            csvBuilder.append(timestamp);
            for (RaoMetadata.Indicator indicator : indicators) {
                String value = data.containsKey(indicator, timestamp) ? data.get(indicator, timestamp).toString() : "";
                csvBuilder.append(delimiter);
                csvBuilder.append(value);
            }
            csvBuilder.append(cr);
        }
        return csvBuilder.toString();*/
        return null;
    }
}
