/*
 * Copyright (c) 2023, RTE (http://www.rte-france.com)
 *  This Source Code Form is subject to the terms of the Mozilla Public
 *  License, v. 2.0. If a copy of the MPL was not distributed with this
 *  file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.farao_community.farao.gridcapa_core_cc.app.preprocessing;

import com.farao_community.farao.gridcapa_core_cc.api.exception.CoreCCInternalException;
import com.farao_community.farao.gridcapa_core_cc.api.exception.CoreCCInvalidDataException;
import com.farao_community.farao.gridcapa_core_cc.api.resource.CoreCCFileResource;
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
import com.farao_community.farao.gridcapa_core_cc.app.util.DataUtil;
import com.farao_community.farao.gridcapa_core_cc.app.util.NamingRules;
import com.farao_community.farao.gridcapa_core_cc.app.util.NetworkUtil;
import com.farao_community.farao.minio_adapter.starter.MinioAdapter;
import com.powsybl.iidm.network.Network;
import com.powsybl.openrao.data.crac.api.CracCreationContext;
import com.powsybl.openrao.virtualhubs.VirtualHubsConfiguration;
import com.unicorn.request.request_payload.RequestItem;
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
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringWriter;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Objects;
import java.util.Optional;

/**
 * @author Mohamed BenRejeb {@literal <mohamed.ben-rejeb at rte-france.com>}
 */
@Service
public class CoreCCPreProcessService {
    private static final Logger LOGGER = LoggerFactory.getLogger(CoreCCPreProcessService.class);
    private static final String S_HOURLY_RAO_RESULTS = "%s/hourly_rao_results";
    private static final String XIIDM_EXPORT_FORMAT = "XIIDM";
    private static final String XIIDM_EXTENSION = ".xiidm";
    private static final String UCT_EXTENSION = ".uct";
    private static final String JSON_CRAC_PROVIDER = "JSON";
    private static final String PREFIXED_FILENAME_FORMAT = "%s_%s";

    private final Logger businessLogger;
    private final MinioAdapter minioAdapter;
    private final RaoParametersService raoParametersService;
    private final FileImporter fileImporter;
    private static final String GENERAL_ERROR = "Error occurred while trying to import inputs at timestamp: %s. Origin cause : %s";

    public CoreCCPreProcessService(Logger businessLogger, MinioAdapter minioAdapter, RaoParametersService raoParametersService, FileImporter fileImporter) {
        this.businessLogger = businessLogger;
        this.minioAdapter = minioAdapter;
        this.raoParametersService = raoParametersService;
        this.fileImporter = fileImporter;
    }

    record RequestResult(HourlyRaoRequest raoRequest, HourlyRaoResult raoResult) {
    }

    public void initializeTaskFromAutomatedLaunch(InternalCoreCCRequest coreCCRequest) {
        splitRaoRequest(coreCCRequest);
    }

    /**
     * RaoRequest contains the following data:
     * - timeIntervals, correlation ID used for sanity checks
     * - the list of hourly timestamps for which a RAO needs to be computed. That's why we check if coreCCRequest's timestamp
     * matches one of the list's timestamps.
     * - specific RAO parameters to be overloaded.
     */
    private void splitRaoRequest(final InternalCoreCCRequest coreCCRequest) {
        final String destinationKey = NamingRules.getDestinationKey(coreCCRequest.getTimestamp());
        final String destinationPath = generateResultDestinationPath(destinationKey);
        // artifacts will be area-specific (continental/SEM) but outputs do not depend on the area,
        // therefore we need to store the common root path in a common object
        coreCCRequest.setDestinationPath(destinationPath);

        final CoreCCTaskParameters parameters = new CoreCCTaskParameters(coreCCRequest.getParameters());
        logCoreCCParameters(coreCCRequest, parameters);

        final RequestMessage raoRequestMessage = fileImporter.importRaoRequest(coreCCRequest.getRaoRequest().getUrl());
        coreCCRequest.setTimeInterval(raoRequestMessage.getPayload().getRequestItems().getTimeInterval());
        coreCCRequest.setCorrelationId(raoRequestMessage.getHeader().getCorrelationID());

        final VirtualHubsConfiguration virtualHubsConfiguration = fileImporter.importVirtualHubs(coreCCRequest.getVirtualHub());
        final boolean semEnabled = isSemEnabled(virtualHubsConfiguration);
        coreCCRequest.setSemEnabled(semEnabled);
        if (semEnabled) {
            businessLogger.info("SEM enabled in VirtualHubs configuration");
        } else {
            businessLogger.warn("SEM disabled in VirtualHubs configuration");
        }

        final String raoParametersFileUrl = raoParametersService.uploadJsonRaoParameters(raoRequestMessage, virtualHubsConfiguration, destinationKey);
        final String raoParametersFilename = new File(raoParametersFileUrl).getName();
        // RaoParameters are the same for both continental and SEM areas and will be required for common outputs generation,
        // so we store the file path in a common object
        coreCCRequest.setRaoParameters(new CoreCCFileResource(raoParametersFilename, raoParametersFileUrl));

        final CgmsAndXmlHeader cgmsAndXmlHeader = fileImporter.importCgmsZip(coreCCRequest.getCgm());
        final CgmsAndXmlHeader dcCgmsAndXmlHeader = coreCCRequest.getDcCgm() != null ? fileImporter.importCgmsZip(coreCCRequest.getDcCgm()) : null;

        checkIfRaoRequestAndCgmHeaderTimeIntervalsMatch(raoRequestMessage, cgmsAndXmlHeader);

        RequestResult continentalRequestResult;
        RequestResult semRequestResult = null;
        // At most one raoRequest item should match coreCCRequest's timestamp
        final Optional<RequestItem> requestItemOptional = raoRequestMessage.getPayload().getRequestItems().getRequestItem().stream()
            .filter(item -> Interval.parse(item.getTimeInterval()).contains(coreCCRequest.getTimestamp().toInstant()))
            .collect(DataUtil.toOptional());

        if (requestItemOptional.isPresent()) {
            final Instant utcInstant = Interval.parse(requestItemOptional.get().getTimeInterval()).getStart();
            LOGGER.info("CoreCCRequest timestamp : {} matched raoRequest timestamp : {}", coreCCRequest.getTimestamp(), utcInstant);
            sendRaoRequestAcknowledgment(coreCCRequest, NamingRules.getAckDestinationKey(coreCCRequest.getTimestamp()), raoRequestMessage);

            continentalRequestResult = getRequestAndResult(
                coreCCRequest, utcInstant, dcCgmsAndXmlHeader, cgmsAndXmlHeader, parameters, destinationKey, raoParametersFileUrl, destinationPath, false
            );

            if (coreCCRequest.isSemEnabled()) {
                semRequestResult = getRequestAndResult(
                    coreCCRequest, utcInstant, dcCgmsAndXmlHeader, cgmsAndXmlHeader, parameters, destinationKey, raoParametersFileUrl, destinationPath, true
                );
            }
        } else {
            final HourlyRaoRequest raoRequest = new HourlyRaoRequest(
                minioAdapter,
                null,
                null, null, null, null, null, null,
                destinationPath
            );

            final String errorMessage = "Missing raoRequest";
            LOGGER.error(errorMessage);
            businessLogger.error("Timestamp not included in RAO request for this business date.");
            final HourlyRaoResult raoResult = buildFailedHourlyRaoResult(null, errorMessage);

            continentalRequestResult = new RequestResult(raoRequest, raoResult);
            if (coreCCRequest.isSemEnabled()) {
                semRequestResult = new RequestResult(raoRequest, raoResult);
            }
        }

        coreCCRequest.setContinentalHourlyRaoRequest(continentalRequestResult.raoRequest());
        coreCCRequest.setContinentalHourlyRaoResult(continentalRequestResult.raoResult());
        if (semRequestResult != null) {
            coreCCRequest.setSemHourlyRaoRequest(semRequestResult.raoRequest());
            coreCCRequest.setSemHourlyRaoResult(semRequestResult.raoResult());
        }
    }

    private RequestResult getRequestAndResult(final InternalCoreCCRequest coreCCRequest,
                                              final Instant utcInstant,
                                              final CgmsAndXmlHeader dcCgmsAndXmlHeader,
                                              final CgmsAndXmlHeader cgmsAndXmlHeader,
                                              final CoreCCTaskParameters parameters,
                                              final String destinationKey,
                                              final String raoParametersFileUrl,
                                              final String destinationPath,
                                              final boolean isSem) {
        HourlyRaoRequest raoRequest;
        HourlyRaoResult raoResult = null;

        final String areaIdentifier = isSem ? "sem" : "continental";

        try {
            final Path cgmPath = resolveCgmPath(dcCgmsAndXmlHeader, utcInstant, cgmsAndXmlHeader, parameters);
            final Network network = convertNetworkToIidm(cgmPath);

            if (isSem) {
                NetworkUtil.SEM_NETWORK_REDUCER.reduce(network);
            } else {
                NetworkUtil.CONTINENTAL_NETWORK_REDUCER.reduce(network);
            }
            final String networkFileUrl = uploadIidmNetwork(destinationKey, cgmPath, network, areaIdentifier, utcInstant);
            final String jsonCracFileUrl = uploadJsonCrac(coreCCRequest, destinationKey, areaIdentifier, utcInstant, network);

            raoRequest = new HourlyRaoRequest(
                minioAdapter,
                utcInstant.toString(),
                networkFileUrl,
                jsonCracFileUrl,
                coreCCRequest.getRefProg().getUrl(),
                coreCCRequest.getVirtualHub().getUrl(),
                coreCCRequest.getGlsk().getUrl(),
                raoParametersFileUrl,
                destinationPath + "/" + areaIdentifier
            );
        } catch (final Exception e) {
            raoRequest = new HourlyRaoRequest(
                minioAdapter,
                utcInstant.toString(),
                null, null, null, null, null, null,
                destinationPath + "/" + areaIdentifier
            );
            final String errorMessage = String.format(GENERAL_ERROR, utcInstant, e.getMessage());
            LOGGER.error(errorMessage, e);
            raoResult = buildFailedHourlyRaoResult(utcInstant.toString(), errorMessage);
        }

        return new RequestResult(raoRequest, raoResult);
    }

    private static HourlyRaoResult buildFailedHourlyRaoResult(final String raoRequestInstant, final String errorMessage) {
        final HourlyRaoResult raoResult = new HourlyRaoResult(raoRequestInstant);
        raoResult.setErrorCode(HourlyRaoResult.ErrorCode.TS_PREPROCESSING_FAILURE);
        raoResult.setErrorMessage(errorMessage);
        raoResult.setStatus(HourlyRaoResult.Status.FAILURE);
        return raoResult;
    }

    private static void checkIfRaoRequestAndCgmHeaderTimeIntervalsMatch(final RequestMessage raoRequestMessage, final CgmsAndXmlHeader cgmsAndXmlHeader) {
        final Interval raoRequestTimeInterval = Interval.parse(raoRequestMessage.getPayload().getRequestItems().getTimeInterval());
        final Interval cgmHeaderTimeInterval = Interval.parse(cgmsAndXmlHeader.getXmlHeader().getPayload().getResponseItems().getTimeInterval());
        if (!raoRequestTimeInterval.equals(cgmHeaderTimeInterval)) {
            throw new CoreCCInvalidDataException("RaoRequest and CGM header time intervals don't match");
        }
    }

    private boolean isSemEnabled(final VirtualHubsConfiguration virtualHubsConfiguration) {
        final boolean semMarketAreaExists = virtualHubsConfiguration.getMarketAreas().stream().anyMatch(ma -> "SEM".equals(ma.code()));
        final boolean semSiVirtualHubExists = virtualHubsConfiguration.getVirtualHubs().stream().anyMatch(vh -> "SEM_CI".equals(vh.code()));
        return semMarketAreaExists && semSiVirtualHubExists;
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

    Path resolveCgmPath(final CgmsAndXmlHeader dcCgmsAndXmlHeader,
                        final Instant utcInstant,
                        final CgmsAndXmlHeader cgmsAndXmlHeader,
                        final CoreCCTaskParameters parameters) {
        final Path cgmPath;
        if (parameters.isUseDcCgmInput()) {
            if (dcCgmsAndXmlHeader != null && dcCgmsAndXmlHeader.getNetworkPath(utcInstant) != null) {
                cgmPath = dcCgmsAndXmlHeader.getNetworkPath(utcInstant);
            } else {
                businessLogger.warn("DC_CGM not available, using CGM input instead.");
                cgmPath = cgmsAndXmlHeader.getNetworkPath(utcInstant);
            }
        } else {
            cgmPath = cgmsAndXmlHeader.getNetworkPath(utcInstant);
        }
        return cgmPath;
    }

    private String generateResultDestinationPath(String destinationKey) {
        return String.format(S_HOURLY_RAO_RESULTS, destinationKey);
    }

    private Network convertNetworkToIidm(Path cgmPath) {
        return CoreNetworkImporterWrapper.loadNetwork(cgmPath);
    }

    private String uploadIidmNetwork(final String destinationKey,
                                     final Path cgmPath,
                                     final Network network,
                                     final String prefix,
                                     final Instant utcInstant) {
        final String initialCgmFilename = cgmPath.getFileName().toString();
        final String tmpFilename = initialCgmFilename.replaceAll("(?i)" + UCT_EXTENSION, XIIDM_EXTENSION);
        final Path tmpFilePath = Paths.get(cgmPath.toString().replace(initialCgmFilename, String.format(PREFIXED_FILENAME_FORMAT, prefix, tmpFilename))); //NOSONAR

        network.write(XIIDM_EXPORT_FORMAT, null, tmpFilePath);

        final String iidmNetworkFilename = NamingRules.UTC_HOURLY_NAME_FORMATTER.format(utcInstant).concat(NamingRules.IIDM_EXTENSION);
        final String iidmNetworkDestinationPath = String.format(NamingRules.S_INPUTS_NETWORKS_S, destinationKey, String.format(PREFIXED_FILENAME_FORMAT, prefix, iidmNetworkFilename));
        try (FileInputStream iidmNetworkInputStream = new FileInputStream(tmpFilePath.toString())) { //NOSONAR
            minioAdapter.uploadArtifact(iidmNetworkDestinationPath, iidmNetworkInputStream);
        } catch (Exception e) {
            throw new CoreCCInternalException("IIDM network could not be uploaded to minio", e);
        }
        return iidmNetworkDestinationPath;
    }

    private String uploadJsonCrac(final InternalCoreCCRequest coreCCRequest,
                                  final String destinationKey,
                                  final String prefix,
                                  final Instant utcInstant,
                                  final Network network) {
        final CracCreationContext cracCreationContext = fileImporter.importCbcora(coreCCRequest.getCbcora().getUrl(), OffsetDateTime.parse(utcInstant.toString()), network);
        try (final ByteArrayOutputStream cracByteArrayOutputStream = new ByteArrayOutputStream()) {
            cracCreationContext.getCrac().write(JSON_CRAC_PROVIDER, cracByteArrayOutputStream);
            final String filename = NamingRules.UTC_HOURLY_NAME_FORMATTER.format(utcInstant).concat(NamingRules.JSON_EXTENSION);
            final String jsonCracFilePath = String.format(NamingRules.S_INPUTS_CRACS_S, destinationKey, String.format(PREFIXED_FILENAME_FORMAT, prefix, filename));
            uploadCracJsonToMinio(cracByteArrayOutputStream, jsonCracFilePath);
            return jsonCracFilePath;
        } catch (Exception e) {
            throw new CoreCCInternalException(String.format("Exception occurred while importing CRAC file: %s", coreCCRequest.getCbcora().getFilename()), e);
        }
    }

    private void uploadCracJsonToMinio(final ByteArrayOutputStream cracByteArrayOutputStream,
                                       final String jsonCracFilePath) {
        try (final InputStream is = new ByteArrayInputStream(cracByteArrayOutputStream.toByteArray())) {
            minioAdapter.uploadArtifact(jsonCracFilePath, is);
        } catch (final Exception e) {
            throw new CoreCCInternalException("Crac JSON file could not be uploaded to minio", e);
        }
    }

    private void sendRaoRequestAcknowledgment(final InternalCoreCCRequest coreCCRequest,
                                              final String destinationKey,
                                              final RequestMessage receivedRequestMessage) {
        final ResponseMessage responseMessage = buildRaoRequestAckResponseMessage(coreCCRequest, receivedRequestMessage);
        exportRaoRequestAcknowledgment(responseMessage, coreCCRequest, destinationKey);
    }

    private ResponseMessage buildRaoRequestAckResponseMessage(final InternalCoreCCRequest coreCCRequest,
                                                              final RequestMessage receivedRequestMessage) {
        final ResponseMessage responseMessage = new ResponseMessage();
        responseMessage.setHeader(new Header());
        responseMessage.getHeader().setVerb("reply");
        responseMessage.getHeader().setNoun(receivedRequestMessage.getHeader().getNoun());
        responseMessage.getHeader().setRevision(receivedRequestMessage.getHeader().getRevision());
        responseMessage.getHeader().setContext(receivedRequestMessage.getHeader().getContext());
        responseMessage.getHeader().setTimestamp(receivedRequestMessage.getHeader().getTimestamp());
        responseMessage.getHeader().setSource(NamingRules.XML_RESPONSE_GENERATOR_SENDER_ID);
        responseMessage.getHeader().setMessageID(String.format("%s-%s-F302-ACK", NamingRules.XML_RESPONSE_GENERATOR_SENDER_ID, DateTimeFormatter.ofPattern("yyyyMMdd").format(coreCCRequest.getTimestamp())));
        responseMessage.getHeader().setCorrelationID(receivedRequestMessage.getHeader().getCorrelationID());
        final Reply reply = new Reply();
        reply.setResult("OK");
        responseMessage.setReply(reply);
        return responseMessage;
    }

    private void exportRaoRequestAcknowledgment(final ResponseMessage responseMessage,
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
                throw new CoreCCInternalException(String.format("Exception occurred while uploading rao request ACK file of task %s", coreCCRequest.getId()), e);
            }
        }
    }

    private byte[] marshallMessageAndSetJaxbProperties(final ResponseMessage responseMessage) {
        try {
            final StringWriter stringWriter = new StringWriter();
            final JAXBContext jaxbContext = JAXBContext.newInstance(ResponseMessage.class);
            final Marshaller jaxbMarshaller = jaxbContext.createMarshaller();
            jaxbMarshaller.setProperty(Marshaller.JAXB_FORMATTED_OUTPUT, true);
            final String eventMessage = "ResponseMessage";
            final QName qName = new QName(XMLConstants.W3C_XML_SCHEMA_INSTANCE_NS_URI, eventMessage);
            final JAXBElement<ResponseMessage> root = new JAXBElement<>(qName, ResponseMessage.class, responseMessage);
            jaxbMarshaller.marshal(root, stringWriter);
            return stringWriter.toString()
                .replace("xsi:ResponseMessage", "ResponseMessage")
                .getBytes();
        } catch (final Exception e) {
            throw new CoreCCInternalException("Exception occurred during RAO Request ACK export.", e);
        }
    }

}
