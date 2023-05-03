/*
 * Copyright (c) 2021, RTE (http://www.rte-france.com)
 */
package com.farao_community.farao.gridcapa_core_cc.app.util;

import com.farao_community.farao.gridcapa_core_cc.api.exception.CoreCCInternalException;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;

/**
 * @author Mohamed BenRejeb {@literal <mohamed.ben-rejeb at rte-france.com>}
 */
public final class FileUtil {

    private FileUtil() {
    }

    public static ResponseEntity<byte[]> toFileAttachmentResponse(byte[] fileContent, String fileName) {
        try {
            ContentDisposition contentDisposition = ContentDisposition.builder("attachment").filename(fileName).build();
            HttpHeaders headers = new HttpHeaders();
            headers.setContentDisposition(contentDisposition);
            return ResponseEntity.ok().contentType(MediaType.APPLICATION_OCTET_STREAM).headers(headers).body(fileContent);
        } catch (Exception e) {
            throw new CoreCCInternalException("Cannot return attachment file", e);
        }
    }

    public static Path findFileFromPath(Path tempInputPath, String fileName, String exceptionMessage) {
        try (Stream<Path> pathStream = Files.find(tempInputPath, 2, (path, basicFileAttributes) -> path.toFile().getName().matches(fileName))) {
            return pathStream.findFirst()
                .orElseThrow(() -> new CoreCCInternalException(exceptionMessage));
        } catch (IOException e) {
            throw new CoreCCInternalException(fileName + " not found in input archive");
        }
    }
}
