package com.farao_community.farao.gridcapa_core_cc.app.entities;

import com.farao_community.farao.gridcapa_core_cc.api.exception.CoreCCInvalidDataException;
import com.farao_community.farao.gridcapa_core_cc.app.inputs.rao_response.ResponseMessage;
import com.unicorn.response.response_payload.ResponseItem;
import org.threeten.extra.Interval;

import java.nio.file.Path;
import java.time.Instant;
import java.util.List;

public class CgmsAndXmlHeader {
    private final ResponseMessage xmlHeader;
    private final List<Path> networkPaths;

    public CgmsAndXmlHeader(ResponseMessage xmlHeader, List<Path> networkPaths) {
        this.xmlHeader = xmlHeader;
        this.networkPaths = networkPaths;
    }

    public ResponseMessage getXmlHeader() {
        return xmlHeader;
    }

    public List<Path> getNetworkPaths() {
        return networkPaths;
    }

    public Path getNetworkPath(Instant instant) {
        ResponseItem instantResponseItem = xmlHeader.getPayload().getResponseItems().getResponseItem().stream()
            .filter(responseItem -> Interval.parse(responseItem.getTimeInterval()).contains(instant))
            .findFirst().orElseThrow(() -> new CoreCCInvalidDataException("cannot find instant " + instant + " in cgm xml header time intervals"));
        return networkPaths.stream().filter(p -> p.toString().contains(instantResponseItem.getFiles().getFile().get(0).getUrl().substring(11)))
            .findFirst().orElseThrow(() -> new CoreCCInvalidDataException("cannot find cgm for instant " + instant + " in zip folder"));
    }
}
