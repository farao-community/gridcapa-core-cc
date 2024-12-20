/*
 * Copyright (c) 2023, RTE (http://www.rte-france.com)
 *  This Source Code Form is subject to the terms of the Mozilla Public
 *  License, v. 2.0. If a copy of the MPL was not distributed with this
 *  file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.farao_community.farao.gridcapa_core_cc.app;

import com.farao_community.farao.minio_adapter.starter.MinioAdapter;
import com.farao_community.farao.minio_adapter.starter.MinioAdapterProperties;
import io.minio.MinioClient;
import org.apache.commons.io.FileUtils;

import jakarta.annotation.Nullable;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

/**
 * @author Thomas Bouquet {@literal <thomas.bouquet at rte-france.com>}
 */
public class MinioFileWriter extends MinioAdapter {

    private final String tempDir = System.getProperty("java.io.tmpdir");

    public MinioFileWriter(MinioAdapterProperties properties, MinioClient minioClient) {
        super(properties, minioClient);
    }

    @Override
    public void uploadOutput(String path, InputStream inputStream) {
        File outputDir = new File(tempDir + "/outputs/");
        if (!outputDir.exists()) {
            boolean created = outputDir.mkdirs();
            System.out.println(created);
        }
        File targetFile = new File(path);
        try {
            FileUtils.copyInputStreamToFile(inputStream, targetFile);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void uploadOutputForTimestamp(String path, InputStream inputStream, @Nullable String targetProcess, @Nullable String type, OffsetDateTime timestamp) {
        String outputDir = tempDir + "/gridcapa-core-cc/";
        LocalDateTime utcLocalDateTime = timestamp.atZoneSameInstant(ZoneOffset.UTC).toLocalDateTime();
        String additionalPathName = targetProcess + "/" + type + "/" + utcLocalDateTime + "/" + utcLocalDateTime.plusHours(1L) + "/";
        File tmpDir = new File(outputDir + additionalPathName);
        if (!tmpDir.exists()) {
            boolean created = tmpDir.mkdirs();
            System.out.println(created);
        }
        String fullPath = outputDir + additionalPathName + path;
        File targetFile = new File(fullPath);
        try {
            FileUtils.copyInputStreamToFile(inputStream, targetFile);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public InputStream getFile(String path) {
        return getClass().getResourceAsStream(path);
    }
}
