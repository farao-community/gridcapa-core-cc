/*
 * Copyright (c) 2023, RTE (http://www.rte-france.com)
 *  This Source Code Form is subject to the terms of the Mozilla Public
 *  License, v. 2.0. If a copy of the MPL was not distributed with this
 *  file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.farao_community.farao.gridcapa_core_cc.app.preprocessing;

import com.farao_community.farao.gridcapa_core_cc.api.exception.CoreCCInternalException;
import com.farao_community.farao.gridcapa_core_cc.api.exception.CoreCCInvalidDataException;
import com.farao_community.farao.gridcapa_core_cc.api.resource.HourlyRaoRequest;
import com.farao_community.farao.gridcapa_core_cc.api.resource.HourlyRaoResult;
import com.farao_community.farao.gridcapa_core_cc.api.resource.InternalCoreCCRequest;
import com.farao_community.farao.gridcapa_core_cc.app.domain.CoreCCTaskParameters;
import com.farao_community.farao.gridcapa_core_cc.app.entities.CgmsAndXmlHeader;
import com.farao_community.farao.gridcapa_core_cc.app.inputs.rao_request.RequestMessage;
import com.farao_community.farao.gridcapa_core_cc.app.inputs.rao_response.Header;
import com.farao_community.farao.gridcapa_core_cc.app.inputs.rao_response.Reply;
import com.farao_community.farao.gridcapa_core_cc.app.inputs.rao_response.ResponseMessage;
import com.farao_community.farao.gridcapa_core_cc.app.services.FileImporter;
import com.farao_community.farao.gridcapa_core_cc.app.util.CoreNetworkImporterWrapper;
import com.farao_community.farao.gridcapa_core_cc.app.util.NamingRules;
import com.farao_community.farao.minio_adapter.starter.MinioAdapter;
import com.powsybl.iidm.network.Network;
import com.powsybl.openrao.data.crac.api.CracCreationContext;
import com.powsybl.openrao.virtualhubs.VirtualHubsConfiguration;
import com.unicorn.request.request_payload.RequestItem;
import com.unicorn.request.request_payload.RequestItems;
import jakarta.xml.bind.JAXBContext;
import jakarta.xml.bind.JAXBElement;
import jakarta.xml.bind.Marshaller;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.threeten.extra.Interval;

import javax.xml.XMLConstants;
import javax.xml.namespace.QName;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringWriter;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;

import static com.farao_community.farao.gridcapa_core_cc.api.resource.HourlyRaoResult.ErrorCode.TS_PREPROCESSING_FAILURE;
import static com.farao_community.farao.gridcapa_core_cc.api.resource.HourlyRaoResult.Status.FAILURE;
import static com.farao_community.farao.gridcapa_core_cc.app.util.NamingRules.IIDM_EXTENSION;
import static com.farao_community.farao.gridcapa_core_cc.app.util.NamingRules.JSON_EXTENSION;
import static com.farao_community.farao.gridcapa_core_cc.app.util.NamingRules.S_INPUTS_CRACS_S;
import static com.farao_community.farao.gridcapa_core_cc.app.util.NamingRules.S_INPUTS_NETWORKS_S;
import static com.farao_community.farao.gridcapa_core_cc.app.util.NamingRules.UTC_HOURLY_NAME_FORMATTER;

/**
 * @author Mohamed BenRejeb {@literal <mohamed.ben-rejeb at rte-france.com>}
 */
@Service
public class CoreCCPreProcessService {
    private static final Logger LOGGER = LoggerFactory.getLogger(CoreCCPreProcessService.class);
    private static final String S_HOURLY_RAO_RESULTS_S = "%s/hourly_rao_results";
    private static final String XIIDM_EXPORT_FORMAT = "XIIDM";
    private static final String XIIDM_EXTENSION = ".xiidm";
    private static final String UCT_EXTENSION = ".uct";
    private static final String JSON_CRAC_PROVIDER = "JSON";

    private final Logger businessLogger;
    private final MinioAdapter minioAdapter;
    private final RaoParametersService raoParametersService;
    private final FileImporter fileImporter;
    private static final String GENERAL_ERROR = "Error occurred while trying to import inputs at timestamp: %s. Origin cause : %s";

    public CoreCCPreProcessService(final Logger businessLogger,
                                   final MinioAdapter minioAdapter,
                                   final RaoParametersService raoParametersService,
                                   final FileImporter fileImporter) {
        this.businessLogger = businessLogger;
        this.minioAdapter = minioAdapter;
        this.raoParametersService = raoParametersService;
        this.fileImporter = fileImporter;
    }

    public void initializeTaskFromAutomatedLaunch(final InternalCoreCCRequest coreCCRequest) {
        splitRaoRequest(coreCCRequest);
    }

    /**
     * RaoRequest contains the following data:
     * - timeIntervals, correlation ID used for sanity checks
     * - the list of hourly timestamps for which a RAO needs to be computed. That's why we check if the request's timestamp
     * matches one of the list's timestamps.
     * - specific RAO parameters to be overloaded.
     */
    private void splitRaoRequest(final InternalCoreCCRequest coreCCRequest) {
        final String destinationKey = NamingRules.getDestinationKey(coreCCRequest.getTimestamp());
        final String destinationPath = generateResultDestinationPath(destinationKey);
        final CoreCCTaskParameters parameters = new CoreCCTaskParameters(coreCCRequest.getParameters());
        logCoreCCParameters(coreCCRequest, parameters);

        final RequestMessage raoRequestMessage = fileImporter.importRaoRequest(coreCCRequest.getRaoRequest());
        final RequestItems raoRequestItems = raoRequestMessage.getPayload().getRequestItems();
        coreCCRequest.setTimeInterval(raoRequestItems.getTimeInterval());
        coreCCRequest.setCorrelationId(raoRequestMessage.getHeader().getCorrelationID());

        final VirtualHubsConfiguration virtualHubsConfiguration = fileImporter.importVirtualHubs(coreCCRequest.getVirtualHub());
        final String raoParametersFileUrl = raoParametersService.uploadJsonRaoParameters(raoRequestMessage, virtualHubsConfiguration, destinationKey);
        final CgmsAndXmlHeader cgmsAndXmlHeader = fileImporter.importCgmsZip(coreCCRequest.getCgm());
        final CgmsAndXmlHeader dcCgmsAndXmlHeader = coreCCRequest.getDcCgm() != null ? fileImporter.importCgmsZip(coreCCRequest.getDcCgm()) : null;
        //check intervals validity
        try {
            final Interval cgmsTimeInterval = Interval.parse(cgmsAndXmlHeader
                                   .getXmlHeader()
                                   .getPayload()
                                   .getResponseItems()
                                   .getTimeInterval());
            if (!Interval.parse(raoRequestItems.getTimeInterval()).equals(cgmsTimeInterval)) {
                throw new CoreCCInvalidDataException("RaoRequest and CGM header time intervals don't match");
            }
        } catch (final DateTimeParseException | NullPointerException e) {
            throw new CoreCCInvalidDataException("Malformed time intervals", e);
        }
        final OffsetDateTime ccRequestTime = coreCCRequest.getTimestamp();
        final AtomicReference<HourlyRaoRequest> raoRequest = new AtomicReference<>();
        final AtomicReference<HourlyRaoResult> raoResult = new AtomicReference<>();
        // Looping through all raoRequest items. Only item matching coreCCRequest's timestamp will set raoRequest
        raoRequestItems.getRequestItem().stream()
                .map(RequestItem::getTimeInterval)
                .map(Interval::parse)
                .forEach(interval -> {
                    final Instant start = interval.getStart();
                    if (interval.contains(ccRequestTime.toInstant())) {
                        LOGGER.info("CoreCCRequest timestamp : {} matched raoRequest timestamp : {}", ccRequestTime, start);
                        sendRaoRequestAcknowledgement(coreCCRequest, NamingRules.getAckDestinationKey(ccRequestTime), raoRequestMessage);
                        try {
                            final Path cgmPath = resolveCgmPath(dcCgmsAndXmlHeader, start, cgmsAndXmlHeader, parameters);
                            final Network network = convertNetworkToIidm(cgmPath);
                            String networkFileUrl = uploadIidmNetwork(destinationKey, cgmPath, network, cgmPath.toFile().getName(), start);
                            String jsonCracFileUrl = uploadCracJson(coreCCRequest, destinationKey, start, network);
                            raoRequest.set(new HourlyRaoRequest(minioAdapter, start.toString(),
                                                                networkFileUrl, jsonCracFileUrl,
                                                                coreCCRequest.getRefProg().getUrl(),
                                                                coreCCRequest.getVirtualHub().getUrl(),
                                                                coreCCRequest.getGlsk().getUrl(),
                                                                raoParametersFileUrl, destinationPath));
                        } catch (final Exception e) {
                            raoRequest.set(new HourlyRaoRequest(minioAdapter, start.toString(),
                                                                null, null,
                                                                null, null,
                                                                null, null,
                                                                destinationPath));
                            final String errorMessage = String.format(GENERAL_ERROR, start, e.getMessage());
                            LOGGER.error(errorMessage);
                            raoResult.set(new HourlyRaoResult(start.toString()));
                            raoResult.get().setErrorCode(TS_PREPROCESSING_FAILURE);
                            raoResult.get().setErrorMessage(errorMessage);
                            raoResult.get().setStatus(FAILURE);
                        }
                    }
                });
        if (Objects.isNull(raoRequest.get())) {
            final String message = "Missing raoRequest";
            raoRequest.set(new HourlyRaoRequest(minioAdapter, null, null, null, null, null, null, null, destinationPath));
            LOGGER.error(message);
            businessLogger.error("Timestamp not included in RAO request for this business date.");
            raoResult.set(new HourlyRaoResult(null));
            raoResult.get().setErrorCode(TS_PREPROCESSING_FAILURE);
            raoResult.get().setErrorMessage(message);
            raoResult.get().setStatus(FAILURE);
        }
        coreCCRequest.setHourlyRaoRequest(raoRequest.get());
        coreCCRequest.setHourlyRaoResult(raoResult.get());
    }

    void logCoreCCParameters(final InternalCoreCCRequest coreCCRequest,
                             final CoreCCTaskParameters parameters) {
        final String loggedParameters = String.format("Core CC task parameters : '%s'", parameters.toJsonString());
        if (coreCCRequest.getParameters().stream().anyMatch(p -> !Objects.equals(p.getValue(), p.getDefaultValue()))) {
            businessLogger.warn(loggedParameters);
        } else {
            businessLogger.info(loggedParameters);
        }
    }

    Path resolveCgmPath(final CgmsAndXmlHeader dcCgmsPaths,
                                  final Instant utcInstant,
                                  final CgmsAndXmlHeader cgmsAndXmlHeader,
                                  final CoreCCTaskParameters parameters) {
        final Path cgmPath;
        if (parameters.isUseDcCgmInput()) {
            if (dcCgmsPaths != null && dcCgmsPaths.getNetworkPath(utcInstant) != null) {
                cgmPath = dcCgmsPaths.getNetworkPath(utcInstant);
            } else {
                businessLogger.warn("DC_CGM not available, using CGM input instead.");
                cgmPath = cgmsAndXmlHeader.getNetworkPath(utcInstant);
            }
        } else {
            cgmPath = cgmsAndXmlHeader.getNetworkPath(utcInstant);
        }
        return cgmPath;
    }

    private String generateResultDestinationPath(final String destinationKey) {
        return String.format(S_HOURLY_RAO_RESULTS_S, destinationKey);
    }

    private Network convertNetworkToIidm(final Path cgmPath) {
        return CoreNetworkImporterWrapper.loadNetwork(cgmPath);
    }

    private String uploadIidmNetwork(final String destinationKey,
                                     final Path cgmPath,
                                     final Network network,
                                     final String cgmFileName,
                                     final Instant utcInstant) {
        String iidmFileName = cgmFileName.replaceAll("(?i)" + UCT_EXTENSION, XIIDM_EXTENSION);
        Path iidmTmpPath = Paths.get(cgmPath.toString().replace(cgmFileName, iidmFileName)); //NOSONAR
        network.write(XIIDM_EXPORT_FORMAT, null, iidmTmpPath);
        String iidmNetworkDestinationPath = String.format(S_INPUTS_NETWORKS_S,
                                                          destinationKey,
                                                          UTC_HOURLY_NAME_FORMATTER.format(utcInstant).concat(IIDM_EXTENSION));
        try (final FileInputStream inputStream = new FileInputStream(iidmTmpPath.toString())) { //NOSONAR
            minioAdapter.uploadArtifact(iidmNetworkDestinationPath, inputStream);
        } catch (final Exception e) {
            throw new CoreCCInternalException("IIDM network could not be uploaded to minio", e);
        }
        return iidmNetworkDestinationPath;
    }

    private String uploadCracJson(final InternalCoreCCRequest coreCCRequest,
                                  final String destinationKey,
                                  final Instant utcInstant,
                                  final Network network) {
        final CracCreationContext cracCreationContext = fileImporter.importCrac(coreCCRequest.getCbcora().getUrl(),
                                                                    OffsetDateTime.parse(utcInstant.toString()),
                                                                    network);
        try (final ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            cracCreationContext.getCrac().write(JSON_CRAC_PROVIDER, outputStream);
            String jsonCracFilePath = String.format(S_INPUTS_CRACS_S,
                                                    destinationKey,
                                                    UTC_HOURLY_NAME_FORMATTER.format(utcInstant).concat(JSON_EXTENSION));
            uploadCracJsonToMinio(outputStream, jsonCracFilePath);
            return jsonCracFilePath;
        } catch (Exception e) {
            throw new CoreCCInternalException(String.format("Exception occurred while importing CRAC file: %s",
                                                            coreCCRequest.getCbcora().getFilename()), e);
        }
    }

    private void uploadCracJsonToMinio(final ByteArrayOutputStream outputStream,
                                       final String jsonCracFilePath) {
        try (final InputStream is = new ByteArrayInputStream(outputStream.toByteArray())) {
            minioAdapter.uploadArtifact(jsonCracFilePath, is);
        } catch (final Exception e) {
            throw new CoreCCInternalException("CRAC JSON file could not be uploaded to minio", e);
        }
    }

    private void sendRaoRequestAcknowledgement(final InternalCoreCCRequest coreCCRequest,
                                               final String destinationKey,
                                               final RequestMessage receivedRequestMessage) {
        final ResponseMessage responseMessage = buildRaoRequestAckResponseMessage(coreCCRequest, receivedRequestMessage);
        exportRaoRequestAcknowledgement(responseMessage, coreCCRequest, destinationKey);
    }

    private ResponseMessage buildRaoRequestAckResponseMessage(final InternalCoreCCRequest coreCCRequest,
                                                              final RequestMessage received) {

        final ResponseMessage responseMessage = new ResponseMessage();
        final Header header = new Header();
        header.setVerb("reply");
        header.setNoun(received.getHeader().getNoun());
        header.setRevision(received.getHeader().getRevision());
        header.setContext(received.getHeader().getContext());
        header.setTimestamp(received.getHeader().getTimestamp());
        header.setSource(NamingRules.XML_RESPONSE_GENERATOR_SENDER_ID);
        header.setMessageID(String.format("%s-%s-F302-ACK", NamingRules.XML_RESPONSE_GENERATOR_SENDER_ID,
                                          DateTimeFormatter.ofPattern("yyyyMMdd").format(coreCCRequest.getTimestamp())));
        header.setCorrelationID(received.getHeader().getCorrelationID());
        responseMessage.setHeader(header);
        final Reply reply = new Reply();
        reply.setResult("OK");
        responseMessage.setReply(reply);
        return responseMessage;
    }

    private void exportRaoRequestAcknowledgement(final ResponseMessage responseMessage,
                                                 final InternalCoreCCRequest coreCCRequest,
                                                 final String destinationKey) {
        final byte[] xml = marshallMessageAndSetJaxbProperties(responseMessage);

        final String raoRequestAckFileName = NamingRules.generateRaoRequestAckFileName(coreCCRequest);
        final String destinationPath = NamingRules.generateOutputsDestinationPath(destinationKey, raoRequestAckFileName);
        // Only upload ACK if no ACK has been uploaded
        if (minioAdapter.fileExists(destinationPath)) {
            LOGGER.info("ACK has already been uploaded !");
        } else {
            try (final InputStream xmlIs = new ByteArrayInputStream(xml)) {
                LOGGER.info("Uploading ACK !");
                minioAdapter.uploadArtifact(destinationPath, xmlIs);
            } catch (final IOException e) {
                throw new CoreCCInternalException(String.format("Exception occurred while uploading rao request ACK file of task %s",
                                                                coreCCRequest.getId()), e);
            }
        }
    }

    private byte[] marshallMessageAndSetJaxbProperties(final ResponseMessage responseMessage) {
        try {
            final StringWriter stringWriter = new StringWriter();
            final Marshaller jaxbMarshaller = JAXBContext.newInstance(ResponseMessage.class).createMarshaller();
            jaxbMarshaller.setProperty(Marshaller.JAXB_FORMATTED_OUTPUT, true);
            final String eventMessage = "ResponseMessage";
            QName qName = new QName(XMLConstants.W3C_XML_SCHEMA_INSTANCE_NS_URI, eventMessage);
            JAXBElement<ResponseMessage> root = new JAXBElement<>(qName, ResponseMessage.class, responseMessage);
            jaxbMarshaller.marshal(root, stringWriter);
            return stringWriter.toString()
                    .replace("xsi:ResponseMessage", eventMessage)
                    .getBytes();
        } catch (final Exception e) {
            throw new CoreCCInternalException("Exception occurred during RAO Request ACK export.", e);
        }
    }

}
