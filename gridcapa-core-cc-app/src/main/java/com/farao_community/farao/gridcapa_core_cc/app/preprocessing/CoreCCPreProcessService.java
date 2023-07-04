/*
 * Copyright (c) 2021, RTE (http://www.rte-france.com)
 */
package com.farao_community.farao.gridcapa_core_cc.app.preprocessing;

import com.farao_community.farao.data.crac_creation.creator.api.CracCreationContext;
import com.farao_community.farao.data.crac_io_api.CracExporters;
import com.farao_community.farao.gridcapa_core_cc.api.exception.CoreCCInternalException;
import com.farao_community.farao.gridcapa_core_cc.api.exception.CoreCCInvalidDataException;
import com.farao_community.farao.gridcapa_core_cc.api.resource.InternalCoreCCRequest;
import com.farao_community.farao.gridcapa_core_cc.app.entities.CgmsAndXmlHeader;
import com.farao_community.farao.gridcapa_core_cc.app.constants.NamingRules;
import com.farao_community.farao.gridcapa_core_cc.api.resource.HourlyRaoRequest;
import com.farao_community.farao.gridcapa_core_cc.api.resource.HourlyRaoResult;
import com.farao_community.farao.gridcapa_core_cc.app.postprocessing.OutputFileNameUtil;
import com.farao_community.farao.gridcapa_core_cc.app.services.FileImporter;
import com.farao_community.farao.gridcapa_core_cc.app.util.CoreNetworkImporterWrapper;
import com.farao_community.farao.minio_adapter.starter.MinioAdapter;
import com.farao_community.farao.virtual_hubs.VirtualHubsConfiguration;
import com.farao_community.farao.virtual_hubs.network_extension_builder.VirtualHubAssigner;
import com.powsybl.iidm.network.Network;
import com.farao_community.farao.gridcapa_core_cc.app.inputs.rao_request.RequestMessage;
import com.farao_community.farao.gridcapa_core_cc.app.inputs.rao_response.Header;
import com.farao_community.farao.gridcapa_core_cc.app.inputs.rao_response.Reply;
import com.farao_community.farao.gridcapa_core_cc.app.inputs.rao_response.ResponseMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.threeten.extra.Interval;

import javax.xml.XMLConstants;
import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBElement;
import javax.xml.bind.Marshaller;
import javax.xml.namespace.QName;
import java.io.*;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;

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
    private static final String JSON_CRAC_PROVIDER = "Json";

    private final MinioAdapter minioAdapter;
    private final RaoParametersService raoParametersService;
    private final FileImporter fileImporter;
    private static final DateTimeFormatter HOURLY_NAME_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMdd'_'HHmm").withZone(ZoneId.of("UTC"));

    private static final String GENERAL_ERROR = "Error occurred while trying to import inputs at timestamp: %s. Origin cause : %s";

    @Value("${core-cc-runner.async-time-out}")
    private long raoTimeOut;

    public CoreCCPreProcessService(MinioAdapter minioAdapter, RaoParametersService raoParametersService, FileImporter fileImporter) {
        this.minioAdapter = minioAdapter;
        this.raoParametersService = raoParametersService;
        this.fileImporter = fileImporter;
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
    private void splitRaoRequest(InternalCoreCCRequest coreCCRequest) {
        String destinationKey = coreCCRequest.getDestinationKey();
        RequestMessage raoRequestMessage = fileImporter.importRaoRequest(coreCCRequest.getRaoRequest());
        coreCCRequest.setTimeInterval(raoRequestMessage.getPayload().getRequestItems().getTimeInterval());
        coreCCRequest.setCorrelationId(raoRequestMessage.getHeader().getCorrelationID());
        String raoParametersFileUrl = raoParametersService.uploadJsonRaoParameters(raoRequestMessage, destinationKey);
        CgmsAndXmlHeader cgmsAndXmlHeader = fileImporter.importCgmsZip(coreCCRequest.getCgm());

        if (!Interval.parse(raoRequestMessage.getPayload().getRequestItems().getTimeInterval()).equals(Interval.parse(cgmsAndXmlHeader.getXmlHeader().getPayload().getResponseItems().getTimeInterval()))) {
            throw new CoreCCInvalidDataException("RaoRequest and CGM header time intervals don't match");
        }

        VirtualHubsConfiguration virtualHubsConfiguration = fileImporter.importVirtualHubs(coreCCRequest.getVirtualHub());

        AtomicReference<HourlyRaoRequest> raoRequest = new AtomicReference<>();
        AtomicReference<HourlyRaoResult> raoResult = new AtomicReference<>();
        // Looping through all raoRequest items. Only item matching coreCCRequest's timestamp will set raoRequest
        raoRequestMessage.getPayload().getRequestItems().getRequestItem().forEach(requestItem -> {
            Instant utcInstant = Interval.parse(requestItem.getTimeInterval()).getStart();
            if (Interval.parse(requestItem.getTimeInterval()).contains(coreCCRequest.getTimestamp().toInstant())) {
                LOGGER.info("CoreCCRequest timestamp : {} matched raoRequest timestamp : {}", coreCCRequest.getTimestamp(), utcInstant);
                sendRaoRequestAcknowledgment(coreCCRequest, coreCCRequest.getAckDestinationKey(), raoRequestMessage);
                try {
                    Path cgmPath = cgmsAndXmlHeader.getNetworkPath(utcInstant);
                    Network network = convertNetworkToIidm(cgmPath, virtualHubsConfiguration);
                    String networkFileUrl = uploadIidmNetwork(destinationKey, cgmPath, network, cgmPath.toFile().getName(), utcInstant);
                    String jsonCracFileUrl = uploadJsonCrac(coreCCRequest, destinationKey, utcInstant, network);
                    String destinationPath = generateResultDestinationPath(destinationKey);
                    Instant targetEndInstant = Instant.now().plusMillis(raoTimeOut);
                    raoRequest.set(new HourlyRaoRequest(minioAdapter, utcInstant.toString(), networkFileUrl, jsonCracFileUrl,
                        coreCCRequest.getRefProg().getUrl(),
                        coreCCRequest.getGlsk().getUrl(),
                        raoParametersFileUrl, destinationPath, targetEndInstant));
                } catch (Exception e) {
                    raoRequest.set(new HourlyRaoRequest(minioAdapter, utcInstant.toString(), null, null, null, null, null, null));
                    String errorMessage = String.format(GENERAL_ERROR, utcInstant, e.getMessage());
                    LOGGER.error(errorMessage);
                    raoResult.set(new HourlyRaoResult(utcInstant.toString()));
                    raoResult.get().setErrorCode(HourlyRaoResult.ErrorCode.TS_PREPROCESSING_FAILURE);
                    raoResult.get().setErrorMessage(errorMessage);
                }
            }
        });
        if (Objects.isNull(raoRequest.get())) {
            String message = "No raoRequest timestamp matched the coreCCRequest timestamp";
            LOGGER.warn(message);
            raoResult.set(new HourlyRaoResult(null));
            raoResult.get().setErrorCode(HourlyRaoResult.ErrorCode.TS_PREPROCESSING_FAILURE);
            raoResult.get().setErrorMessage(message);
            raoResult.get().setStatus(HourlyRaoResult.Status.FAILURE);
        }
        coreCCRequest.setHourlyRaoRequest(raoRequest.get());
        coreCCRequest.setHourlyRaoResult(raoResult.get());
    }

    private String generateResultDestinationPath(String destinationKey) {
        return String.format(S_HOURLY_RAO_RESULTS_S, destinationKey);
    }

    private Network convertNetworkToIidm(Path cgmPath, VirtualHubsConfiguration virtualHubsConfiguration) {
        Network network = CoreNetworkImporterWrapper.loadNetwork(cgmPath);
        addVirtualHubsExtensionToNetwork(network, virtualHubsConfiguration);
        return network;
    }

    private String uploadIidmNetwork(String destinationKey, Path cgmPath, Network network, String cgmFileName, Instant utcInstant) throws IOException {
        String iidmFileName = cgmFileName.replaceAll("(?i)" + UCT_EXTENSION, XIIDM_EXTENSION);
        Path iidmTmpPath = Paths.get(cgmPath.toString().replace(cgmFileName, iidmFileName)); //NOSONAR
        network.write(XIIDM_EXPORT_FORMAT, null, iidmTmpPath);
        String iidmNetworkDestinationPath = String.format(NamingRules.S_INPUTS_NETWORKS_S, destinationKey, HOURLY_NAME_FORMATTER.format(utcInstant).concat(NamingRules.IIDM_EXTENSION));
        try (FileInputStream iidmNetworkInputStream = new FileInputStream(iidmTmpPath.toString())) { //NOSONAR
            minioAdapter.uploadArtifact(iidmNetworkDestinationPath, iidmNetworkInputStream);
        }
        return iidmNetworkDestinationPath;
    }

    private void addVirtualHubsExtensionToNetwork(Network network, VirtualHubsConfiguration virtualHubsConfiguration) {
        VirtualHubAssigner virtualHubAssigner = new VirtualHubAssigner(virtualHubsConfiguration.getVirtualHubs());
        virtualHubAssigner.addVirtualLoads(network);
        LOGGER.info("Virtual hubs configuration found. Virtual loads are added to network '{}'", network.getNameOrId());
    }

    private String uploadJsonCrac(InternalCoreCCRequest coreCCRequest, String destinationKey, Instant utcInstant, Network network) {
        CracCreationContext cracCreationContext = fileImporter
            .importCrac(coreCCRequest.getCbcora().getUrl(), OffsetDateTime.parse(utcInstant.toString()), network);

        try (ByteArrayOutputStream cracByteArrayOutputStream = new ByteArrayOutputStream()) {
            CracExporters.exportCrac(cracCreationContext.getCrac(), JSON_CRAC_PROVIDER, cracByteArrayOutputStream);
            String jsonCracFilePath = String.format(NamingRules.S_INPUTS_CRACS_S, destinationKey, HOURLY_NAME_FORMATTER.format(utcInstant).concat(NamingRules.JSON_EXTENSION));

            try (InputStream is = new ByteArrayInputStream(cracByteArrayOutputStream.toByteArray())) {
                minioAdapter.uploadArtifact(jsonCracFilePath, is);
            }
            return jsonCracFilePath;
        } catch (Exception e) {
            throw new CoreCCInternalException(String.format("Exception occurred while importing CRAC file: %s. Cause: %s", coreCCRequest.getCbcora().getFilename(), e.getMessage()));
        }
    }

    private void sendRaoRequestAcknowledgment(InternalCoreCCRequest coreCCRequest, String destinationKey, RequestMessage receivedRequestMessage) {
        ResponseMessage responseMessage = buildRaoRequestAckResponseMessage(coreCCRequest, receivedRequestMessage);
        exportRaoRequestAcknowledgment(responseMessage, coreCCRequest, destinationKey);
    }

    private ResponseMessage buildRaoRequestAckResponseMessage(InternalCoreCCRequest coreCCRequest, RequestMessage receivedRequestMessage) {
        ResponseMessage responseMessage = new ResponseMessage();
        responseMessage.setHeader(new Header());
        responseMessage.getHeader().setVerb("reply");
        responseMessage.getHeader().setNoun(receivedRequestMessage.getHeader().getNoun());
        responseMessage.getHeader().setRevision(receivedRequestMessage.getHeader().getRevision());
        responseMessage.getHeader().setContext(receivedRequestMessage.getHeader().getContext());
        responseMessage.getHeader().setTimestamp(receivedRequestMessage.getHeader().getTimestamp());
        responseMessage.getHeader().setSource(NamingRules.XML_RESPONSE_GENERATOR_SENDER_ID);
        responseMessage.getHeader().setMessageID(String.format("%s-%s-F302-ACK", NamingRules.XML_RESPONSE_GENERATOR_SENDER_ID, DateTimeFormatter.ofPattern("yyyyMMdd").format(coreCCRequest.getTimestamp())));
        responseMessage.getHeader().setCorrelationID(receivedRequestMessage.getHeader().getCorrelationID());
        Reply reply = new Reply();
        reply.setResult("OK");
        responseMessage.setReply(reply);
        return responseMessage;
    }

    private void exportRaoRequestAcknowledgment(ResponseMessage responseMessage, InternalCoreCCRequest coreCCRequest, String destinationKey) {
        byte[] xml = marshallMessageAndSetJaxbProperties(responseMessage);

        String raoRequestAckFileName = OutputFileNameUtil.generateRaoRequestAckFileName(coreCCRequest);
        String destinationPath = OutputFileNameUtil.generateOutputsDestinationPath(destinationKey, raoRequestAckFileName);
        // Only upload ACK if no ACK has been uploaded
        if (minioAdapter.fileExists(destinationPath)) {
            LOGGER.info("ACK has already been uploaded !");
        } else {
            try (InputStream xmlIs = new ByteArrayInputStream(xml)) {
                LOGGER.info("Uploading ACK !");
                minioAdapter.uploadArtifact(destinationPath, xmlIs);
            } catch (IOException e) {
                throw new CoreCCInternalException(String.format("Exception occurred while uploading rao request ACK file of task %s", coreCCRequest.getId()));
            }
        }
    }

    private byte[] marshallMessageAndSetJaxbProperties(ResponseMessage responseMessage) {
        try {
            StringWriter stringWriter = new StringWriter();
            JAXBContext jaxbContext = JAXBContext.newInstance(ResponseMessage.class);
            Marshaller jaxbMarshaller = jaxbContext.createMarshaller();
            jaxbMarshaller.setProperty(Marshaller.JAXB_FORMATTED_OUTPUT, true);
            String eventMessage = "ResponseMessage";
            QName qName = new QName(XMLConstants.W3C_XML_SCHEMA_INSTANCE_NS_URI, eventMessage);
            JAXBElement<ResponseMessage> root = new JAXBElement<>(qName, ResponseMessage.class, responseMessage);
            jaxbMarshaller.marshal(root, stringWriter);
            return stringWriter.toString()
                    .replace("xsi:ResponseMessage", "ResponseMessage")
                    .getBytes();
        } catch (Exception e) {
            throw new CoreCCInternalException("Exception occurred during RAO Request ACK export.", e);
        }
    }

}
