/*
 * Copyright (c) 2023, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package com.farao_community.farao.gridcapa_core_cc.app.services.results_export;

import com.farao_community.farao.data.crac_api.Contingency;
import com.farao_community.farao.gridcapa_core_cc.app.limiting_branch.LimitingBranchResult;
import com.farao_community.farao.gridcapa_core_cc.app.study_point.StudyPointResult;
import com.farao_community.farao.gridcapa_core_cc.api.exception.CoreCCInvalidDataException;
import com.farao_community.farao.minio_adapter.starter.MinioAdapter;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStreamWriter;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * ResultFileExporter implementation generating a zip archive with the following files:
 * <ul>
 *     <li>An overview of all limitingBranch for each study-point of the timestamp</li>
 * </ul>
 *
 * @author Alexandre Montigny {@literal <alexandre.montigny at rte-france.com>}
 */
@Component
public class RexResultFileExporter extends AbstractResultFileExporter {

    private static final Logger LOGGER = LoggerFactory.getLogger(RexResultFileExporter.class);
    private static final String REX_SAMPLE_CSV_FILE = "outputs/%s-CCCORE-REX-v[v].csv";
    private static final CSVFormat REX_CSV_FORMAT = CSVFormat.EXCEL.builder()
        .setDelimiter(';')
        .setHeader("Period", "Vertice ID", "Branch ID", "Branch Name", "Outage Name", "Branch Status", "RAM before", "RAM after", "flow before", "flow after")
        .build();

    private final MinioAdapter minioAdapter;

    public RexResultFileExporter(MinioAdapter minioAdapter) {
        this.minioAdapter = minioAdapter;
    }

    public String exportStudyPointResult(List<StudyPointResult> studyPointResults, OffsetDateTime timestamp) {
        ByteArrayOutputStream resultBaos = new ByteArrayOutputStream();
        try {
            CSVPrinter resultCsvPrinter = new CSVPrinter(new OutputStreamWriter(resultBaos), REX_CSV_FORMAT);

            List<List<String>> resultCsvItems = studyPointResults.stream()
                .map(RexResultFileExporter::getResultCsvItemsFromStudyPointResult)
                .flatMap(Collection::stream)
                .distinct()
                .collect(Collectors.toList());

            for (List<String> resultCsvItem : resultCsvItems) {
                resultCsvPrinter.printRecord(resultCsvItem);
            }

            resultCsvPrinter.flush();
            resultCsvPrinter.close();
        } catch (IOException e) {
            throw new CoreCCInvalidDataException("Error during export of studypoint results on Minio", e);
        }
        String filePath = getFormattedFilename(REX_SAMPLE_CSV_FILE, timestamp, minioAdapter);
        InputStream inStream = new ByteArrayInputStream(resultBaos.toByteArray());
        minioAdapter.uploadOutputForTimestamp(filePath, inStream, "CORE_CC", ResultType.REX_RESULT.getFileType(), timestamp);
        LOGGER.info("Rex result file was successfully uploaded on minIO");
        return minioAdapter.generatePreSignedUrl(filePath);
    }

    private static List<List<String>> getResultCsvItemsFromStudyPointResult(StudyPointResult studyPointResult) {
        return studyPointResult.getListLimitingBranchResult().stream()
            .map(limitingBranchResult -> getRexResultFields(limitingBranchResult, studyPointResult))
            .collect(Collectors.toList());
    }

    private static List<String> getRexResultFields(LimitingBranchResult limitingBranchResult, StudyPointResult studyPointResult) {
        List<String> rexResultFields = new ArrayList<>();

        rexResultFields.add(studyPointResult.getPeriod());
        rexResultFields.add(studyPointResult.getId());
        rexResultFields.add(limitingBranchResult.getCriticalBranchId());
        rexResultFields.add(limitingBranchResult.getCriticalBranchName());
        Optional<Contingency> optionalContingency = limitingBranchResult.getState().getContingency();
        if (optionalContingency.isPresent()) {
            rexResultFields.add(optionalContingency.get().getName());
        } else {
            rexResultFields.add("");
        }
        rexResultFields.add(limitingBranchResult.getBranchStatus());
        rexResultFields.add(String.valueOf(Math.round(limitingBranchResult.getRamBefore())));
        rexResultFields.add(String.valueOf(Math.round(limitingBranchResult.getRamAfter())));
        rexResultFields.add(String.valueOf(Math.round(limitingBranchResult.getFlowBefore())));
        rexResultFields.add(String.valueOf(Math.round(limitingBranchResult.getFlowAfter())));

        return rexResultFields;
    }
}
