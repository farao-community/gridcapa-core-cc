/*
 * Copyright (c) 2022, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package com.farao_community.farao.gridcapa.core_cc.app.preprocessing;

import com.farao_community.farao.data.crac_creation.creator.api.CracCreationContext;
import com.farao_community.farao.data.crac_creation.creator.api.CracCreationReport;
import com.farao_community.farao.data.crac_creation.creator.api.CracCreators;
import com.farao_community.farao.data.crac_io_api.CracExporters;
import com.farao_community.farao.data.native_crac_api.NativeCrac;
import com.farao_community.farao.data.native_crac_io_api.NativeCracImporters;
import com.farao_community.farao.gridcapa.core_cc.app.constants.InputsNamingRules;
import com.farao_community.farao.gridcapa.core_cc.app.entities.HourlyRaoRequest;
import com.farao_community.farao.gridcapa.core_cc.app.entities.HourlyRaoResult;
import com.farao_community.farao.gridcapa.core_cc.app.entities.RaoIntegrationTask;
import com.farao_community.farao.gridcapa.core_cc.app.entities.TaskStatus;
import com.farao_community.farao.gridcapa.core_cc.app.exceptions.RaoIntegrationException;
import com.farao_community.farao.gridcapa.core_cc.app.inputs.rao_request.RequestMessage;
import com.farao_community.farao.gridcapa.core_cc.app.inputs.rao_response.Header;
import com.farao_community.farao.gridcapa.core_cc.app.inputs.rao_response.Reply;
import com.farao_community.farao.gridcapa.core_cc.app.inputs.rao_response.ResponseMessage;
import com.farao_community.farao.gridcapa.core_cc.app.messaging.MinioAdapter;
import com.farao_community.farao.gridcapa.core_cc.app.postprocessing.OutputFileNameUtil;
import com.farao_community.farao.gridcapa.core_cc.app.postprocessing.RaoIXmlResponseGenerator;
import com.farao_community.farao.gridcapa.core_cc.app.util.CoreNetworkImporterWrapper;
import com.farao_community.farao.gridcapa.core_cc.app.util.IntervalUtil;
import com.farao_community.farao.gridcapa.core_cc.app.util.JaxbUtil;
import com.farao_community.farao.gridcapa.core_cc.app.util.ZipUtil;
import com.farao_community.farao.virtual_hubs.VirtualHubsConfiguration;
import com.farao_community.farao.virtual_hubs.network_extension_builder.VirtualHubAssigner;
import com.farao_community.farao.virtual_hubs.xml.XmlVirtualHubsConfiguration;
import com.powsybl.iidm.export.Exporters;
import com.powsybl.iidm.network.Network;
import com.unicorn.response.response_payload.ResponseItem;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.threeten.extra.Interval;

import javax.xml.XMLConstants;
import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBElement;
import javax.xml.bind.Marshaller;
import javax.xml.namespace.QName;
import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Stream;

/**
 * @author Mohamed BenRejeb {@literal <mohamed.ben-rejeb at rte-france.com>}
 */
@Service
public class RaoIPreProcessService {
    private static final Logger LOGGER = LoggerFactory.getLogger(RaoIPreProcessService.class);
    private static final String S_HOURLY_RAO_RESULTS_S = "%s/hourly_rao_results/%s";
    private static final String XIIDM_EXPORT_FORMAT = "XIIDM";
    private static final String XIIDM_EXTENSION = ".xiidm";
    private static final String UCT_EXTENSION = ".uct";
    private static final String JSON_CRAC_PROVIDER = "Json";
    private static final String FLOW_BASED_CRAC_PROVIDER = "FlowBasedConstraintDocument";

    private final MinioAdapter minioAdapter;
    private final RaoParametersService raoParametersService;
    private static final DateTimeFormatter HOURLY_NAME_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMdd'_'HHmm").withZone(ZoneId.of("UTC"));

    private static final String GENERAL_ERROR = "Error occurred while trying to import inputs at timestamp: %s. Origin cause : %s";
    private static final String ERR_CGM = "Please check the naming format of the CGMs. No match with: %s";
    private static final String ERR_REFPROG = "Please check the naming format of Refprog. No match with: %s";
    private static final String ERR_CRAC = "Please check the naming format of the Merged CB. No match with: %s";
    private static final String ERR_GLSK = "Please check the naming format of the Real GLSK. No match with: %s";
    private static final String ERR_VIRTUAL_HUBS = "Please check the naming format of the virtual hubs. No match with: %s";
    private static final String ERR_REQUEST = "Please check the naming format of the RAO request. No match with: %s";
    private static final String ERR_CONSISTENCY = "Package not consistent.";

    @Value("${rao-integration.async-time-out}")
    private long raoTimeOut;

    public RaoIPreProcessService(MinioAdapter minioAdapter, RaoParametersService raoParametersService) {
        this.minioAdapter = minioAdapter;
        this.raoParametersService = raoParametersService;
    }

    public void initializeTaskFromAutomatedLaunch(RaoIntegrationTask raoIntegrationTask, String destinationKey,
                                                  Path raoRequestTmpPath,
                                                  Path cracPath,
                                                  Path cgmsUnzippedFolderPath,
                                                  Path virtualHubsTmpPath,
                                                  String refProgUrl,
                                                  String realGlskUrl) {
        splitRaoRequest(raoIntegrationTask, raoRequestTmpPath, cgmsUnzippedFolderPath, refProgUrl, realGlskUrl, virtualHubsTmpPath, cracPath, destinationKey, false);
    }

    public void initializeTaskFromZipArchive(RaoIntegrationTask raoIntegrationTask, MultipartFile inputFilesArchive) {
        unzipInputFileInTmp(inputFilesArchive, Paths.get(raoIntegrationTask.getTmpInputsPath())); //NOSONAR
        String destinationKey = String.valueOf(raoIntegrationTask.getTaskId());
        Path tempInputPath = Paths.get(raoIntegrationTask.getTmpInputsPath()); //NOSONAR
        Path tempCgmInputPath = Paths.get(raoIntegrationTask.getTmpCgmInputsPath()); //NOSONAR
        try {
            Path raoRequestTmpPath = findFileFromPath(tempInputPath, InputsNamingRules.RAO_REQUEST_FILE_NAME, String.format(ERR_REQUEST, InputsNamingRules.RAO_REQUEST_FILE_NAME));
            Path refProgTmpPath = findFileFromPath(tempInputPath, InputsNamingRules.REF_PROG_FILE_NAME, String.format(ERR_REFPROG, InputsNamingRules.REF_PROG_FILE_NAME));
            Path cracFileTmpPath = findFileFromPath(tempInputPath, InputsNamingRules.CRAC_FILE_NAME, String.format(ERR_CRAC, InputsNamingRules.CRAC_FILE_NAME));
            Path realGlskTmpPath = findFileFromPath(tempInputPath, InputsNamingRules.REAL_GLSK_FILE_NAME, String.format(ERR_GLSK, InputsNamingRules.REAL_GLSK_FILE_NAME));
            Path virtualHubsTmpPath = findFileFromPath(tempInputPath, InputsNamingRules.VIRTUAL_HUBS_FILE_NAME, String.format(ERR_VIRTUAL_HUBS, InputsNamingRules.VIRTUAL_HUBS_FILE_NAME));

            try (FileInputStream raoRequestInputStream = new FileInputStream(raoRequestTmpPath.toString()); //NOSONAR
                 FileInputStream refProgInputStream = new FileInputStream(refProgTmpPath.toString()); //NOSONAR
                 FileInputStream cracInputStream = new FileInputStream(cracFileTmpPath.toString()); //NOSONAR
                 FileInputStream glskInputStream = new FileInputStream(realGlskTmpPath.toString()); //NOSONAR
                 FileInputStream virtualHubsInputStream = new FileInputStream(virtualHubsTmpPath.toString())) { //NOSONAR

                // rao request F302
                String raoRequestFileName = raoRequestTmpPath.getFileName().toString();
                String raoRequestDestinationPath = String.format(InputsNamingRules.S_INPUTS_S, destinationKey, raoRequestFileName);
                minioAdapter.uploadFile(raoRequestDestinationPath, raoRequestInputStream);
                raoIntegrationTask.setRaoRequestFileName(raoRequestFileName);

                // ref prog F120
                String refProgFileName = refProgTmpPath.getFileName().toString();
                String refProgDestinationPath = String.format(InputsNamingRules.S_INPUTS_S, destinationKey, refProgFileName);
                minioAdapter.uploadFile(refProgDestinationPath, refProgInputStream);
                String refprogFileUrl = minioAdapter.generatePreSignedUrl(refProgDestinationPath);

                // crac file F301
                String cracFileName = cracFileTmpPath.getFileName().toString();
                String cracDestinationPath = String.format(InputsNamingRules.S_INPUTS_S, destinationKey, cracFileName);
                minioAdapter.uploadFile(cracDestinationPath, cracInputStream);
                String cracXmlFileUrl = minioAdapter.generatePreSignedUrl(cracDestinationPath);
                raoIntegrationTask.setInputCracXmlFileUrl(cracXmlFileUrl);

                // glsk file F319
                String realGlskFileName = realGlskTmpPath.getFileName().toString();
                String realGlskDestinationPath = String.format(InputsNamingRules.S_INPUTS_S, destinationKey, realGlskFileName);
                minioAdapter.uploadFile(realGlskDestinationPath, glskInputStream);
                String realGlskFileUrl = minioAdapter.generatePreSignedUrl(realGlskDestinationPath);

                // virtual hubs F327
                String virtualHubsFileName = virtualHubsTmpPath.getFileName().toString();
                String virtualHubsDestinationPath = String.format(InputsNamingRules.S_INPUTS_S, destinationKey, virtualHubsFileName);
                minioAdapter.uploadFile(virtualHubsDestinationPath, virtualHubsInputStream);

                //cgm zip F119
                Path cgmsZippedFolderPath = findFileFromPath(tempInputPath, InputsNamingRules.CGM_ZIP_FILE_NAME, String.format(ERR_CGM, InputsNamingRules.CGM_ZIP_FILE_NAME));
                Path cgmsUnZippedTempFolderPath = tempCgmInputPath;
                ZipUtil.unzipFile(cgmsZippedFolderPath, cgmsUnZippedTempFolderPath);
                ZipUtil.deletePath(cgmsZippedFolderPath);

                // cgm header
                Path cgmHeaderTmpPath = findFileFromPath(tempCgmInputPath, InputsNamingRules.CGM_XML_HEADER_NAME, String.format(ERR_CGM, InputsNamingRules.CGM_XML_HEADER_NAME));
                try (FileInputStream cgmHeaderInputStream = new FileInputStream(cgmHeaderTmpPath.toString())) { //NOSONAR
                    String cgmHeaderFileName = cgmHeaderTmpPath.getFileName().toString();
                    String cgmHeaderDestinationPath = String.format(InputsNamingRules.S_INPUTS_NETWORKS_S, destinationKey, cgmHeaderFileName);
                    minioAdapter.uploadFile(cgmHeaderDestinationPath, cgmHeaderInputStream);
                }
                splitRaoRequest(raoIntegrationTask, raoRequestTmpPath, cgmsUnZippedTempFolderPath, refprogFileUrl, realGlskFileUrl, virtualHubsTmpPath, cracFileTmpPath, destinationKey, true);
            }
        } catch (RaoIntegrationException e) {
            raoIntegrationTask.setTaskStatus(TaskStatus.ERROR);
            splitRaoRequestWhenError(raoIntegrationTask, tempInputPath, e.getMessage());
        } catch (Exception e) {
            throw new RaoIntegrationException(e.getMessage());
        } finally {
            ZipUtil.deletePath(tempInputPath);
        }
    }

    private int getVersionNumberFromRaoRequest(RequestMessage raoRequestMessage) {
        return raoRequestMessage.getHeader().getRevision().intValue() < 10 ? raoRequestMessage.getHeader().getRevision().intValue() : 0; //take revision number inside rao request for version
    }

    private void splitRaoRequestWhenError(RaoIntegrationTask raoIntegrationTask, Path tempInputPath, String exceptionMessage) {
        LOGGER.error("Could not run RAO task {}: {}", raoIntegrationTask.getTaskId(), exceptionMessage);
        RequestMessage raoRequestMessage = JaxbUtil.unmarshalFile(RequestMessage.class, findFileFromPath(tempInputPath, InputsNamingRules.RAO_REQUEST_FILE_NAME, String.format(ERR_REQUEST, InputsNamingRules.RAO_REQUEST_FILE_NAME)));
        raoIntegrationTask.setCorrelationId(raoRequestMessage.getHeader().getCorrelationID());
        raoIntegrationTask.setTimeInterval(raoRequestMessage.getPayload().getRequestItems().getTimeInterval());
        Set<HourlyRaoRequest> raoRequestList = new HashSet<>();
        Set<HourlyRaoResult> raoResultsList = new HashSet<>();
        raoRequestMessage.getPayload().getRequestItems().getRequestItem().forEach(requestItem -> {
            Instant instant = Interval.parse(requestItem.getTimeInterval()).getStart();
            HourlyRaoRequest raoRequest = new HourlyRaoRequest(instant.toString(), null, null, null, null, null, null);
            raoRequestList.add(raoRequest);
            HourlyRaoResult raoResult = new HourlyRaoResult();
            raoResult.setInstant(instant.toString());
            raoResult.setStatus(TaskStatus.ERROR);
            raoResult.setErrorCode("");
            raoResult.setErrorMessage(exceptionMessage);
            raoResultsList.add(raoResult);
        });
        raoIntegrationTask.setHourlyRaoRequests(raoRequestList);
        raoIntegrationTask.setHourlyRaoResults(raoResultsList);
    }

    private void splitRaoRequest(RaoIntegrationTask raoIntegrationTask, Path raoRequestTempPath, Path cgmsUnZippedTempFolderPath, String refprogFileUrl, String realGlskFileUrl, Path virtualHubsTmpPath, Path cracFileTmpPath, String destinationKey, boolean isManualRun) {
        RequestMessage raoRequestMessage = JaxbUtil.unmarshalFile(RequestMessage.class, raoRequestTempPath);
        raoIntegrationTask.setVersion(1);

        String raoParametersFileUrl = raoParametersService.uploadJsonRaoParameters(raoRequestMessage, destinationKey);
        raoIntegrationTask.setCorrelationId(raoRequestMessage.getHeader().getCorrelationID());
        raoIntegrationTask.setTimeInterval(raoRequestMessage.getPayload().getRequestItems().getTimeInterval());
        ResponseMessage cgmXmlHeaderMessage = JaxbUtil.unmarshalFile(ResponseMessage.class, findFileFromPath(cgmsUnZippedTempFolderPath, InputsNamingRules.CGM_XML_HEADER_NAME, String.format(ERR_CGM, InputsNamingRules.RAO_REQUEST_FILE_NAME)));
        checkTimeIntervalsCoherence(raoRequestMessage.getPayload().getRequestItems().getTimeInterval(), cgmXmlHeaderMessage.getPayload().getResponseItems().getTimeInterval());

        sendRaoRequestAcknowledgment(raoIntegrationTask, destinationKey, raoRequestMessage, isManualRun);

        Set<HourlyRaoRequest> raoRequestList = new HashSet<>();
        Set<HourlyRaoResult> raoResultsList = new HashSet<>();
        raoRequestMessage.getPayload().getRequestItems().getRequestItem().forEach(requestItem -> {
            Instant utcInstant = Interval.parse(requestItem.getTimeInterval()).getStart();
            try {
                String cgmFileName = getCgmFileNameByInstantFromCgmXmlHeader(cgmXmlHeaderMessage, utcInstant);
                Network network = convertNetworkToIidm(cgmsUnZippedTempFolderPath, cgmFileName, virtualHubsTmpPath);
                String networkFileUrl = uploadIidmNetwork(destinationKey, cgmsUnZippedTempFolderPath, network, cgmFileName, utcInstant);
                String jsonCracFileUrl = uploadJsonCrac(raoIntegrationTask, destinationKey, utcInstant, cracFileTmpPath, network);
                String destinationPath = generateResultDestinationPath(destinationKey, utcInstant);
                Instant targetEndInstant = Instant.now().plusMillis(raoTimeOut);

                HourlyRaoRequest raoRequest = new HourlyRaoRequest(utcInstant.toString(), networkFileUrl, jsonCracFileUrl, refprogFileUrl, realGlskFileUrl, raoParametersFileUrl, destinationPath, targetEndInstant);
                raoRequestList.add(raoRequest);
            } catch (Exception e) {
                HourlyRaoRequest raoRequest = new HourlyRaoRequest(utcInstant.toString(), null, null, null, null, null, null);
                raoRequestList.add(raoRequest);
                String errorMessage = String.format(GENERAL_ERROR, utcInstant, e.getMessage());
                LOGGER.error(errorMessage);
                HourlyRaoResult raoResult = new HourlyRaoResult();
                raoResult.setInstant(utcInstant.toString());
                raoResult.setStatus(TaskStatus.ERROR);
                raoResult.setErrorCode("");
                raoResult.setErrorMessage(errorMessage);
                raoResultsList.add(raoResult);
            }
        });
        raoIntegrationTask.setHourlyRaoRequests(raoRequestList);
        raoIntegrationTask.setHourlyRaoResults(raoResultsList);
    }

    private void checkTimeIntervalsCoherence(String raoRequestTimeInterval, String cgmXmlHeaderTimeInterval) {
        if (!Interval.parse(raoRequestTimeInterval).equals(Interval.parse(cgmXmlHeaderTimeInterval))) {
            throw new RaoIntegrationException(ERR_CONSISTENCY);
        }
    }

    private String generateResultDestinationPath(String destinationKey, Instant instant) {
        String hourlyFolderName = HOURLY_NAME_FORMATTER.format(Instant.parse(instant.toString()));
        return String.format(S_HOURLY_RAO_RESULTS_S, destinationKey, hourlyFolderName);
    }

    private Network convertNetworkToIidm(Path tempInputPath, String cgmFileName, Path virtualHubsTmpPath) {
        Path cgmTmpPath = findFileFromPath(tempInputPath, cgmFileName, String.format(ERR_CGM, cgmFileName));
        Network network = CoreNetworkImporterWrapper.loadNetwork(cgmTmpPath);
        addVirtualHubsExtensionToNetwork(network, virtualHubsTmpPath);
        return network;
    }

    private String uploadIidmNetwork(String destinationKey, Path tempInputPath, Network network, String cgmFileName, Instant utcInstant) throws IOException {
        Path cgmTmpPath = findFileFromPath(tempInputPath, cgmFileName, "");
        String iidmFileName = cgmFileName.replaceAll("(?i)" + UCT_EXTENSION, XIIDM_EXTENSION);
        Path iidmTmpPath = Paths.get(cgmTmpPath.toString().replace(cgmFileName, iidmFileName)); //NOSONAR
        Exporters.export(XIIDM_EXPORT_FORMAT, network, null, iidmTmpPath);
        String iidmNetworkDestinationPath = String.format(InputsNamingRules.S_INPUTS_NETWORKS_S, destinationKey, HOURLY_NAME_FORMATTER.format(utcInstant).concat(InputsNamingRules.IIDM_EXTENSION));
        try (FileInputStream iidmNetworkInputStream = new FileInputStream(iidmTmpPath.toString())) { //NOSONAR
            minioAdapter.uploadFile(iidmNetworkDestinationPath, iidmNetworkInputStream);
        }
        return minioAdapter.generatePreSignedUrl(iidmNetworkDestinationPath);
    }

    private void addVirtualHubsExtensionToNetwork(Network network, Path virtualHubsTmpPath) {
        try {
            VirtualHubsConfiguration virtualHubsConfiguration = XmlVirtualHubsConfiguration.importConfiguration(new FileInputStream(virtualHubsTmpPath.toFile()));
            VirtualHubAssigner virtualHubAssigner = new VirtualHubAssigner(virtualHubsConfiguration.getVirtualHubs());
            virtualHubAssigner.addVirtualLoads(network);
            LOGGER.info("Virtual hubs configuration found. Virtual loads are added to network '{}'", network.getNameOrId());
        } catch (FileNotFoundException e) {
            throw new RaoIntegrationException("virtual hubs temporary path not found", e);
        }
    }

    private String uploadJsonCrac(RaoIntegrationTask raoIntegrationTask, String destinationKey, Instant utcInstant, Path cracFileTmpPath, Network network) {
        try (InputStream cracIs = new BufferedInputStream(new FileInputStream(cracFileTmpPath.toFile()));
             ByteArrayOutputStream cracByteArrayOutputStream = new ByteArrayOutputStream()) {

            NativeCrac nativeCrac = NativeCracImporters.findImporter(FLOW_BASED_CRAC_PROVIDER).importNativeCrac(cracIs);
            CracCreationContext cracCreationContext = CracCreators.createCrac(nativeCrac, network, OffsetDateTime.parse(utcInstant.toString()));
            CracExporters.exportCrac(cracCreationContext.getCrac(), JSON_CRAC_PROVIDER, cracByteArrayOutputStream);
            exportCracCreationReportInTmpOutput(raoIntegrationTask, utcInstant.toString(), cracCreationContext.getCreationReport());
            String jsonCracFilePath = String.format(InputsNamingRules.S_INPUTS_CRACS_S, destinationKey, HOURLY_NAME_FORMATTER.format(utcInstant).concat(InputsNamingRules.JSON_EXTENSION));

            try (InputStream is = new ByteArrayInputStream(cracByteArrayOutputStream.toByteArray())) {
                minioAdapter.uploadFile(jsonCracFilePath, is);
            }
            cracIs.close();
            cracByteArrayOutputStream.close();
            return minioAdapter.generatePreSignedUrl(jsonCracFilePath);
        } catch (Exception e) {
            throw new RaoIntegrationException(String.format("Exception occurred while importing CRAC file: %s. Cause: %s", cracFileTmpPath.getFileName().toString(), e.getMessage()));
        }
    }

    private Path findFileFromPath(Path tempInputPath, String fileName, String exceptionMessage) {
        try (Stream<Path> pathStream = Files.find(tempInputPath, 2, (path, basicFileAttributes) -> path.toFile().getName().matches(fileName))) {
            return pathStream.findFirst()
                    .orElseThrow(() -> new RaoIntegrationException(exceptionMessage));
        } catch (IOException e) {
            throw new RaoIntegrationException(fileName + " not found in input archive");
        }
    }

    String getCgmFileNameByInstantFromCgmXmlHeader(ResponseMessage cgmXmlHeaderMessage, Instant instant) {
        ResponseItem instantResponseItem = cgmXmlHeaderMessage.getPayload().getResponseItems().getResponseItem().stream()
                .filter(responseItem -> Interval.parse(responseItem.getTimeInterval()).contains(instant))
                .findFirst().orElseThrow(() -> new RaoIntegrationException("cannot find instant " + instant + " in cgm xml header time intervals"));
        return instantResponseItem.getFiles().getFile().get(0).getUrl().substring(11);
    }

    private void unzipInputFileInTmp(MultipartFile archive, Path archiveTempPath) {
        try {
            Path inputsArchivePath = transferArchiveInTempDirectory(archive, archiveTempPath);
            ZipUtil.unzipFile(inputsArchivePath, archiveTempPath);
            ZipUtil.deletePath(inputsArchivePath);
        } catch (IOException e) {
            throw new RaoIntegrationException("IO exception occurred during unzipping archive: " + archive.getOriginalFilename() + " in tmp directory");
        }
    }

    private Path transferArchiveInTempDirectory(MultipartFile multipartFile, Path path) throws IOException {
        Path inputPath = Paths.get(path.toString(), "inputs"); //NOSONAR

        if (inputPath.normalize().startsWith(path)) {
            multipartFile.transferTo(inputPath);
            return inputPath;
        } else {
            throw new IOException("Entry is outside of the target directory");
        }
    }

    private void sendRaoRequestAcknowledgment(RaoIntegrationTask raoIntegrationTask, String destinationKey, RequestMessage receivedRequestMessage, boolean isManualRun) {
        ResponseMessage responseMessage = buildRaoRequestAckResponseMessage(raoIntegrationTask, receivedRequestMessage);
        exportRaoRequestAcknowledgment(responseMessage, raoIntegrationTask, destinationKey, isManualRun);
    }

    private ResponseMessage buildRaoRequestAckResponseMessage(RaoIntegrationTask raoIntegrationTask, RequestMessage receivedRequestMessage) {
        ResponseMessage responseMessage = new ResponseMessage();
        responseMessage.setHeader(new Header());
        responseMessage.getHeader().setVerb("reply");
        responseMessage.getHeader().setNoun(receivedRequestMessage.getHeader().getNoun());
        responseMessage.getHeader().setRevision(receivedRequestMessage.getHeader().getRevision());
        responseMessage.getHeader().setContext(receivedRequestMessage.getHeader().getContext());
        responseMessage.getHeader().setTimestamp(receivedRequestMessage.getHeader().getTimestamp());
        responseMessage.getHeader().setSource(RaoIXmlResponseGenerator.SENDER_ID);
        responseMessage.getHeader().setMessageID(String.format("%s-%s-F302-ACK", RaoIXmlResponseGenerator.SENDER_ID, IntervalUtil.getFormattedBusinessDay(raoIntegrationTask.getTimeInterval())));
        responseMessage.getHeader().setCorrelationID(receivedRequestMessage.getHeader().getCorrelationID());
        Reply reply = new Reply();
        reply.setResult("OK");
        responseMessage.setReply(reply);
        return responseMessage;
    }

    private void exportRaoRequestAcknowledgment(ResponseMessage responseMessage, RaoIntegrationTask raoIntegrationTask, String destinationKey, boolean isManualRun) {
        String outputsDestinationKey = destinationKey.replace("RAO_WORKING_DIR/", "RAO_OUTPUTS_DIR/");

        byte[] xml = marshallMessageAndSetJaxbProperties(responseMessage);

        String raoRequestAckFileName = OutputFileNameUtil.generateRaoRequestAckFileName(raoIntegrationTask);
        String destinationPath = OutputFileNameUtil.generateOutputsDestinationPath(outputsDestinationKey, raoRequestAckFileName);
        try (InputStream xmlIs = new ByteArrayInputStream(xml)) {
            minioAdapter.uploadFile(destinationPath, xmlIs);
            if (!isManualRun) {
                minioAdapter.copyObject(destinationPath, raoRequestAckFileName, minioAdapter.getDefaultBucket(), minioAdapter.getOutputsBucket());
            }
        } catch (IOException e) {
            throw new RaoIntegrationException(String.format("Exception occurred while uploading rao request ACK file of task %s", raoIntegrationTask.getTaskId()));
        }
        raoIntegrationTask.getDailyOutputs().setRaoRequestAckPath(destinationPath);
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
            throw new RaoIntegrationException("Exception occurred during RAO Request ACK export.", e);
        }
    }

    private void exportCracCreationReportInTmpOutput(RaoIntegrationTask raoIntegrationTask, String instant, CracCreationReport cracCreationReport) throws IOException {
        LOGGER.info("RAO integration task: '{}', writing CRAC creation report for timestamp: '{}'", raoIntegrationTask.getTaskId(), instant);
        String fileName = OutputFileNameUtil.generateCracCreationReportFileName(instant, raoIntegrationTask);
        File targetFile = new File(raoIntegrationTask.getDailyOutputs().getLogsTmpOutputPath(), fileName); //NOSONAR
        try (FileOutputStream outputStream = new FileOutputStream(targetFile)) {
            byte[] strToBytes = cracCreationReport.toString().getBytes();
            outputStream.write(strToBytes);
        }
    }
}
