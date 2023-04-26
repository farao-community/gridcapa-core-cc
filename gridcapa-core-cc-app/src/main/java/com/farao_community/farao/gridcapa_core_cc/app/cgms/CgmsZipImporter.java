/*
 * Copyright (c) 2021, RTE (http://www.rte-france.com)
 *  This Source Code Form is subject to the terms of the Mozilla Public
 *  License, v. 2.0. If a copy of the MPL was not distributed with this
 *  file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package com.farao_community.farao.gridcapa_core_cc.app.cgms;

import com.farao_community.farao.gridcapa_core_cc.api.exception.CoreCCInvalidDataException;
import com.farao_community.farao.gridcapa_core_cc.app.constants.InputsNamingRules;
import com.farao_community.farao.gridcapa_core_cc.app.util.ImportUtil;
import com.farao_community.farao.gridcapa_core_cc.app.util.JaxbUtil;
import com.farao_community.farao.gridcapa_core_cc.app.util.ZipUtil;
import com.farao_community.farao.minio_adapter.starter.MinioAdapter;
import com.farao_community.farao.gridcapa_core_cc.app.inputs.rao_request.RequestMessage;
import com.farao_community.farao.gridcapa_core_cc.app.inputs.rao_response.Header;
import com.farao_community.farao.gridcapa_core_cc.app.inputs.rao_response.Reply;
import com.farao_community.farao.gridcapa_core_cc.app.inputs.rao_response.ResponseMessage;
import com.opencsv.CSVParser;
import com.opencsv.CSVParserBuilder;
import com.opencsv.CSVReader;
import com.opencsv.CSVReaderBuilder;
import org.springframework.context.annotation.Import;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileAttribute;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.text.NumberFormat;
import java.text.ParseException;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.*;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import static com.farao_community.farao.gridcapa_core_cc.app.CoreCCPreProcessor.ERR_CGM;

/**
 * @author Ameni Walha {@literal <ameni.walha at rte-france.com>}
 */
public final class CgmsZipImporter {

    private static final String ERR_CGM = "Please check the naming format of the CGMs. No match with: %s";

    private CgmsZipImporter() {
        throw new IllegalStateException("Utility class");
    }

    public static CgmsAndHeader importCgmsAndHeader(InputStream inputStream, RequestMessage raoRequestMessage) throws IOException {
        FileAttribute<Set<PosixFilePermission>> attr = PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rwx------"));
        Path tempCgmInputPath = Files.createTempDirectory("gridcapa-core-cc-temp-dir" + File.separator + "cgm" + attr);
        ZipUtil.unzipInputStream(inputStream, tempCgmInputPath);
        //cgm header
        ResponseMessage cgmXmlHeaderMessage = JaxbUtil.unmarshalFile(ResponseMessage.class, ImportUtil.findFileFromPath(tempCgmInputPath, InputsNamingRules.CGM_XML_HEADER_NAME, String.format(ERR_CGM, InputsNamingRules.RAO_REQUEST_FILE_NAME)));
        ImportUtil.checkTimeIntervalsCoherence(raoRequestMessage.getPayload().getRequestItems().getTimeInterval(), cgmXmlHeaderMessage.getPayload().getResponseItems().getTimeInterval());

        //cgms

    }

    private static CgmsAndHeader importCgmsAndHeader(Reader reader) {
        try {
            CSVReader csvReader = new CSVReaderBuilder(reader)
                .withCSVParser(buildParser())
                .build();
            List<String[]> lines = csvReader.readAll();
            String[] headers = lines.get(0);
            for (int i = 1; i < lines.size(); i++) {
                studyPoints.add(importStudyPoint(headers, lines.get(i)));
            }
            return studyPoints;
        } catch (Exception e) {
            throw new CoreValidInvalidDataException("Exception occurred during parsing study point file", e);
        }
    }

    private static StudyPoint importStudyPoint(String[] headers, String[] data) throws ParseException {
        int period = Integer.parseInt(data[0]);
        String id = data[1];
        Map<String, Double> positions = new HashMap<>();
        for (int i = 2; i < data.length; i++) {
            positions.put(headers[i], NumberFormat.getInstance(Locale.FRANCE).parse(data[i]).doubleValue());
        }
        return new StudyPoint(period, id, positions);
    }

    private static CSVParser buildParser() {
        return new CSVParserBuilder()
            .withSeparator(';')
            .build();
    }
}
