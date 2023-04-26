package com.farao_community.farao.gridcapa_core_cc.app.util;

import com.farao_community.farao.gridcapa_core_cc.api.exception.CoreCCInternalException;
import com.farao_community.farao.gridcapa_core_cc.api.exception.CoreCCInvalidDataException;
import org.threeten.extra.Interval;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;

public class ImportUtil {

    public static Path findFileFromPath(Path tempInputPath, String fileName, String exceptionMessage) {
        try (Stream<Path> pathStream = Files.find(tempInputPath, 2, (path, basicFileAttributes) -> path.toFile().getName().matches(fileName))) {
            return pathStream.findFirst()
                .orElseThrow(() -> new CoreCCInternalException(exceptionMessage));
        } catch (IOException e) {
            throw new CoreCCInternalException(fileName + " not found in input archive");
        }
    }

    public static void checkTimeIntervalsCoherence(String timeInterval1, String timeInterval2) {
        if (!Interval.parse(timeInterval1).equals(Interval.parse(timeInterval2))) {
            throw new CoreCCInvalidDataException("Inconsistency in time intervals");
        }
    }
}
