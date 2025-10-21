package com.farao_community.farao.gridcapa_core_cc.app.entities;

import com.farao_community.farao.gridcapa_core_cc.api.exception.CoreCCInvalidDataException;
import com.farao_community.farao.gridcapa_core_cc.app.inputs.rao_response.ResponseMessage;
import com.unicorn.response.response_payload.File;
import com.unicorn.response.response_payload.Files;
import com.unicorn.response.response_payload.ResponseItem;
import org.threeten.extra.Interval;

import java.nio.file.Path;
import java.time.Instant;
import java.util.List;

public class CgmsAndXmlHeader {
    private final ResponseMessage xmlHeader;
    private final List<Path> networkPaths;
    private static final int ROOT_FOLDER_PATH_LENGTH = 11;

    public CgmsAndXmlHeader(final ResponseMessage xmlHeader,
                            final List<Path> networkPaths) {
        this.xmlHeader = xmlHeader;
        this.networkPaths = networkPaths;
    }

    public ResponseMessage getXmlHeader() {
        return xmlHeader;
    }

    public Path getNetworkPath(final Instant instant) {
        final String pathUrl = xmlHeader.getPayload()
                .getResponseItems()
                .getResponseItem().stream()
                .filter(responseItem -> Interval.parse(responseItem.getTimeInterval()).contains(instant))
                .map(ResponseItem::getFiles)
                .map(Files::getFile)
                .map(List::getFirst)
                .map(File::getUrl)
                .map(fullUrl -> fullUrl.substring(ROOT_FOLDER_PATH_LENGTH))
                .findFirst()
                .orElseThrow(() -> new CoreCCInvalidDataException("cannot find instant " + instant + " in cgm xml header time intervals"));

        return networkPaths.stream()
                .filter(p -> p.toString().contains(pathUrl))
                .findFirst()
                .orElseThrow(() -> new CoreCCInvalidDataException("cannot find cgm for instant " + instant + " in zip folder"));
    }
}
