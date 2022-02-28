/*
 * Copyright (c) 2022, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package com.farao_community.farao.gridcapa.core_cc.app.util;

import com.farao_community.farao.gridcapa.core_cc.app.exceptions.RaoIntegrationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;

/**
 * @author Mohamed BenRejeb {@literal <mohamed.ben-rejeb at rte-france.com>}
 */
public final class FileUtil {

    private static final Logger LOGGER = LoggerFactory.getLogger(FileUtil.class);

    private FileUtil() {
    }

    public static Path setFilePermissions(Path path) {
        File file = path.toFile();
        boolean permissionOK;
        // Set RWX to false by default
        permissionOK = file.setReadable(false) && file.setWritable(false) && file.setExecutable(false);
        // Set RWX to true for owner only
        permissionOK = permissionOK && file.setReadable(true, true) && file.setWritable(true, true) && file.setExecutable(true, true);

        if (!permissionOK) {
            LOGGER.warn("Permissions of file {} has not been set properly", file.toString());
        }

        return path;
    }

    public static ResponseEntity<byte[]> toFileAttachmentResponse(byte[] fileContent, String fileName) {
        try {
            ContentDisposition contentDisposition = ContentDisposition.builder("attachment").filename(fileName).build();
            HttpHeaders headers = new HttpHeaders();
            headers.setContentDisposition(contentDisposition);
            return ResponseEntity.ok().contentType(MediaType.APPLICATION_OCTET_STREAM).headers(headers).body(fileContent);
        } catch (Exception e) {
            throw new RaoIntegrationException("Cannot return attachment file", e);
        }
    }

    public static Path findFileFromPath(Path tempInputPath, String fileName, String exceptionMessage) {
        try (Stream<Path> pathStream = Files.find(tempInputPath, 2, (path, basicFileAttributes) -> path.toFile().getName().matches(fileName))) {
            return pathStream.findFirst()
                .orElseThrow(() -> new RaoIntegrationException(exceptionMessage));
        } catch (IOException e) {
            throw new RaoIntegrationException(fileName + " not found in input archive");
        }
    }

    public static Path createTempDirectory(String directoryName) throws IOException {
        Path directory = Files.createTempDirectory(directoryName);
        setFilePermissions(directory);
        return directory;
    }
}
