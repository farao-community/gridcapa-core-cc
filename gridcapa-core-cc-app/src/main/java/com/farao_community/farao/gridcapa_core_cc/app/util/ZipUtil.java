/*
 * Copyright (c) 2021, RTE (http://www.rte-france.com)
 */
package com.farao_community.farao.gridcapa_core_cc.app.util;

import com.farao_community.farao.gridcapa_core_cc.api.exception.CoreCCInternalException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * @author Mohamed BenRejeb {@literal <mohamed.ben-rejeb at rte-france.com>}
 */
public final class ZipUtil {

    private static final int BUFFER_SIZE = 4096;
    private static final double THRESHOLD_RATIO = 100;
    private static final int THRESHOLD_ENTRIES = 10000;
    private static final Logger LOGGER = LoggerFactory.getLogger(ZipUtil.class);

    private ZipUtil() {
        throw new AssertionError("Utility class should not be instantiated");
    }

    public static List<Path> unzipInputStream(InputStream inputStream, Path destDirectory) {
        List<Path> unzippedPaths = new ArrayList<>();
        try (ZipInputStream zipIn = new ZipInputStream(inputStream)) {
            ZipEntry entry = zipIn.getNextEntry(); //NOSONAR
            int totalEntries = 0;
            // iterates over entries in the zip file
            while (entry != null) {
                totalEntries++;
                Path filePath = Path.of(destDirectory + File.separator + entry.getName()).normalize();
                if (!filePath.startsWith(destDirectory)) {
                    throw new IOException("Entry is outside of the target directory");
                }
                if (!entry.isDirectory()) {
                    // if the entry is a file, extracts it
                    extractFile(zipIn, filePath.toString(), entry.getCompressedSize());
                    unzippedPaths.add(filePath);
                } else {
                    // if the entry is a directory, make the directory
                    File dir = new File(filePath.toString()); //NOSONAR
                    dir.mkdir();
                }
                zipIn.closeEntry();
                entry = zipIn.getNextEntry(); //NOSONAR
                if (totalEntries > THRESHOLD_ENTRIES) {
                    throw new IOException("Entry threshold reached while unzipping.");
                }
            }
        } catch (IOException e) {
            LOGGER.error("Error while extracting input stream");
            throw new CoreCCInternalException("Error while extracting input stream", e);
        }
        return unzippedPaths;
    }

    private static void extractFile(ZipInputStream zipIn, String filePath, long compressedSize) throws IOException {
        float totalSizeEntry = 0;
        try (BufferedOutputStream bos = new BufferedOutputStream(new FileOutputStream(filePath))) { //NOSONAR
            byte[] bytesIn = new byte[BUFFER_SIZE];
            int read;
            while ((read = zipIn.read(bytesIn)) != -1) {
                bos.write(bytesIn, 0, read);
                totalSizeEntry += read;
                double compressionRatio = totalSizeEntry / compressedSize;
                if (compressionRatio > THRESHOLD_RATIO) {
                    throw new IOException("Ratio between compressed and uncompressed data suspiciously large.");
                }
            }
        }
        catch (Exception e) {
            throw new CoreCCInternalException("Zip file could not be extracted", e);
        }
    }
}

