/*
 * Copyright (c) 2021, RTE (http://www.rte-france.com)
 */
package com.farao_community.farao.gridcapa_core_cc.app.postprocessing;

import com.farao_community.farao.gridcapa_core_cc.api.exception.CoreCCInternalException;
import com.farao_community.farao.gridcapa_core_cc.api.resource.CoreCCRequest;
import com.farao_community.farao.gridcapa_core_cc.api.resource.HourlyRaoResult;
import com.farao_community.farao.gridcapa_core_cc.app.constants.OutputsNamingRules;
import com.farao_community.farao.gridcapa_core_cc.app.outputs.rao_response.*;
import com.farao_community.farao.gridcapa_core_cc.app.util.IntervalUtil;
import com.farao_community.farao.minio_adapter.starter.MinioAdapter;
import org.springframework.stereotype.Service;
import org.threeten.extra.Interval;

import javax.xml.XMLConstants;
import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBElement;
import javax.xml.bind.Marshaller;
import javax.xml.datatype.DatatypeConfigurationException;
import javax.xml.datatype.DatatypeFactory;
import javax.xml.namespace.QName;
import java.io.*;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;

/**
 * @author Pengbo Wang {@literal <pengbo.wang at rte-international.com>}
 * @author Mohamed Ben Rejeb {@literal <mohamed.ben-rejeb at rte-france.com>}
 */
@Service
public class CoreCCXmlResponseGenerator {

    private final MinioAdapter minioAdapter;
    public static final String OPTIMIZED_CGM = "OPTIMIZED_CGM";
    public static final String OPTIMIZED_CB = "OPTIMIZED_CB";
    public static final String RAO_REPORT = "RAO_REPORT";
    public static final String CGM = "CGM";
    public static final String FILENAME = "fileName://";
    public static final String DOCUMENT_IDENTIFICATION = "documentIdentification://";
    public static final String SENDER_ID = "22XCORESO------S";
    public static final String RECEIVER_ID = "17XTSO-CS------W";

    public CoreCCXmlResponseGenerator(MinioAdapter minioAdapter) {
        this.minioAdapter = minioAdapter;
    }

    public String generateRaoResponse(CoreCCRequest coreCCRequest, String targetMinioFolder) {
        try {
            ResponseMessageType responseMessage = new ResponseMessageType();
            generateRaoResponseHeader(coreCCRequest, responseMessage);
            generateRaoResponsePayLoad(coreCCRequest, responseMessage);
            return exportCoreCCResponse(coreCCRequest, responseMessage, targetMinioFolder);
        } catch (Exception e) {
            throw new CoreCCInternalException("Error occurred during Core CC Response file creation", e);
        }
    }

    public void generateCgmXmlHeaderFile(CoreCCRequest coreCCRequest, String cgmsTempDirPath) {
        try {
            ResponseMessageType responseMessage = new ResponseMessageType();
            generateCgmXmlHeaderFileHeader(coreCCRequest, responseMessage);
            generateCgmXmlHeaderFilePayLoad(coreCCRequest, responseMessage);
            exportCgmXmlHeaderFile(responseMessage, cgmsTempDirPath);
        } catch (Exception e) {
            throw new CoreCCInternalException("Error occurred during CGM_XML_HEADER creation", e);
        }
    }

    void generateRaoResponseHeader(CoreCCRequest coreCCRequest, ResponseMessageType responseMessage) throws DatatypeConfigurationException {
        HeaderType header = new HeaderType();
        header.setVerb("created");
        header.setNoun("OptimizedRemedialActions");
        header.setRevision(String.valueOf(coreCCRequest.getVersion()));
        header.setContext("PRODUCTION");
        header.setTimestamp(DatatypeFactory.newInstance().newXMLGregorianCalendar(Instant.now().toString()));
        header.setSource(SENDER_ID);
        header.setAsyncReplyFlag(false);
        header.setAckRequired(false);
        header.setReplyAddress(RECEIVER_ID);
        header.setMessageID(String.format("%s-%s-F305", SENDER_ID, IntervalUtil.getFormattedBusinessDay(coreCCRequest.getTimestamp())));
        header.setCorrelationID(coreCCRequest.getCorrelationId());
        responseMessage.setHeader(header);
    }

    void generateCgmXmlHeaderFileHeader(CoreCCRequest coreCCRequest, ResponseMessageType responseMessage) throws DatatypeConfigurationException {
        HeaderType header = new HeaderType();
        header.setVerb("created");
        header.setNoun("OptimizedCommonGridModel");
        header.setContext("PRODUCTION");
        header.setRevision(String.valueOf(coreCCRequest.getVersion()));
        header.setSource(SENDER_ID);
        header.setReplyAddress(RECEIVER_ID);
        header.setTimestamp(DatatypeFactory.newInstance().newXMLGregorianCalendar(Instant.now().toString()));
        header.setCorrelationID(coreCCRequest.getCorrelationId());

        //need to save this MessageID and reuse in rao response
        String outputCgmXmlHeaderMessageId = String.format("%s-%s-F304v%s", SENDER_ID, IntervalUtil.getFormattedBusinessDay(coreCCRequest.getTimestamp()), coreCCRequest.getVersion());
        header.setMessageID(outputCgmXmlHeaderMessageId);
        coreCCRequest.getDailyOutputs().setOutputCgmXmlHeaderMessageId(outputCgmXmlHeaderMessageId);

        responseMessage.setHeader(header);
    }

    private String formatInterval(Interval interval) {
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy'-'MM'-'dd'T'HH':'mm'Z'").withZone(ZoneId.from(ZoneOffset.UTC));
        return String.format("%s/%s", formatter.format(interval.getStart()), formatter.format(interval.getEnd()));
    }

    void generateRaoResponsePayLoad(CoreCCRequest coreCCRequest, ResponseMessageType responseMessage) {
        ResponseItems responseItems = new ResponseItems();
        responseItems.setTimeInterval(coreCCRequest.getTimeInterval());
        coreCCRequest.getHourlyRaoResults().stream().sorted(Comparator.comparing(HourlyRaoResult::getInstant))
            .forEach(hourlyRaoResult -> {
                ResponseItem responseItem = new ResponseItem();
                //set time interval
                Instant instant = Instant.parse(hourlyRaoResult.getInstant());
                Interval interval = Interval.of(instant, instant.plus(1, ChronoUnit.HOURS));
                responseItem.setTimeInterval(formatInterval(interval));

                if (hourlyRaoResult.getStatus().equals(HourlyRaoResult.Status.FAILURE)) {
                    fillFailedHours(hourlyRaoResult, responseItem);
                } else if (hourlyRaoResult.getStatus().equals(HourlyRaoResult.Status.RUNNING)) {
                    fillRunningHours(responseItem);
                } else {
                    //set file
                    com.farao_community.farao.gridcapa_core_cc.app.outputs.rao_response.Files files = new com.farao_community.farao.gridcapa_core_cc.app.outputs.rao_response.Files();
                    com.farao_community.farao.gridcapa_core_cc.app.outputs.rao_response.File file = new com.farao_community.farao.gridcapa_core_cc.app.outputs.rao_response.File();

                    file.setCode(OPTIMIZED_CGM);
                    file.setUrl(DOCUMENT_IDENTIFICATION + coreCCRequest.getDailyOutputs().getOutputCgmXmlHeaderMessageId()); //MessageID of the CGM F304 zip (from header file)
                    files.getFile().add(file);

                    com.farao_community.farao.gridcapa_core_cc.app.outputs.rao_response.File file1 = new com.farao_community.farao.gridcapa_core_cc.app.outputs.rao_response.File();
                    file1.setCode(OPTIMIZED_CB);
                    file1.setUrl(DOCUMENT_IDENTIFICATION + coreCCRequest.getDailyOutputs().getOutputFlowBasedConstraintDocumentMessageId()); //MessageID of the f303
                    files.getFile().add(file1);

                    com.farao_community.farao.gridcapa_core_cc.app.outputs.rao_response.File file2 = new com.farao_community.farao.gridcapa_core_cc.app.outputs.rao_response.File();
                    file2.setCode(RAO_REPORT);
                    file2.setUrl(DOCUMENT_IDENTIFICATION + hourlyRaoResult.getCneResultDocumentId());
                    files.getFile().add(file2);

                    responseItem.setFiles(files);
                }
                responseItems.getResponseItem().add(responseItem);
            });
        PayloadType payload = new PayloadType();
        payload.setResponseItems(responseItems);
        responseMessage.setPayload(payload);
    }

    void generateCgmXmlHeaderFilePayLoad(CoreCCRequest coreCCRequest, ResponseMessageType responseMessage) {
        ResponseItems responseItems = new ResponseItems();
        responseItems.setTimeInterval(coreCCRequest.getTimeInterval());

        String[] timeInterval = coreCCRequest.getTimeInterval().split("/");
        Instant start = parseInstantWithoutSeconds(timeInterval[0]);
        Instant end = parseInstantWithoutSeconds(timeInterval[1]);
        for (Instant instant = start; instant.isBefore(end); instant = instant.plus(1, ChronoUnit.HOURS)) {
            final Instant instantFinal = instant;
            HourlyRaoResult hourlyRaoResult = coreCCRequest.getHourlyRaoResults().stream().filter(result -> result.getInstant().equals(instantFinal.toString())).findAny().orElse(null);
            ResponseItem responseItem = new ResponseItem();
            //set time interval
            Interval interval = Interval.of(instant, instant.plus(1, ChronoUnit.HOURS));
            responseItem.setTimeInterval(formatInterval(interval));

            if (hourlyRaoResult == null) {
                // If there's no result object then there was no request object
                fillMissingCgmInput(responseItem);
            } else if (hourlyRaoResult.getStatus().equals(HourlyRaoResult.Status.SUCCESS)) {
                //set file
                com.farao_community.farao.gridcapa_core_cc.app.outputs.rao_response.Files files = new com.farao_community.farao.gridcapa_core_cc.app.outputs.rao_response.Files();

                com.farao_community.farao.gridcapa_core_cc.app.outputs.rao_response.File file = new com.farao_community.farao.gridcapa_core_cc.app.outputs.rao_response.File();
                file.setCode(CGM);
                file.setUrl(FILENAME + OutputFileNameUtil.generateUctFileName(instant.toString(), coreCCRequest.getVersion()));
                files.getFile().add(file);
                responseItem.setFiles(files);
            }
            responseItems.getResponseItem().add(responseItem);
        }
        PayloadType payload = new PayloadType();
        payload.setResponseItems(responseItems);
        responseMessage.setPayload(payload);
    }

    private Instant parseInstantWithoutSeconds(String instant) {
        return Instant.parse(instant.replace(":00Z", ":00:00Z"));
    }

    private void fillMissingCgmInput(ResponseItem responseItem) {
        ErrorType error = new ErrorType();
        error.setCode("NOT_AVAILABLE");
        error.setReason("UCT file is not available for this time interval");
        responseItem.setError(error);
    }

    private void fillFailedHours(HourlyRaoResult hourlyRaoResult, ResponseItem responseItem) {
        ErrorType error = new ErrorType();
        error.setCode(hourlyRaoResult.getErrorCodeString());
        error.setLevel("FATAL");
        error.setReason(hourlyRaoResult.getErrorMessage());
        responseItem.setError(error);
    }

    private void fillRunningHours(ResponseItem responseItem) {
        ErrorType error = new ErrorType();
        error.setCode(HourlyRaoResult.ErrorCode.RUNNING.getCode());
        error.setLevel("INFORM");
        error.setReason("Running not finished yet");
        responseItem.setError(error);
    }

    private String exportCoreCCResponse(CoreCCRequest coreCCRequest, ResponseMessageType responseMessage, String targetMinioFolder) {
        byte[] responseMessageBytes = marshallMessageAndSetJaxbProperties(responseMessage);
        String coreCCResponseFileName = OutputFileNameUtil.generateCoreCCResponseFileName(coreCCRequest);
        String coreCCResponseDestinationPath = OutputFileNameUtil.generateOutputsDestinationPath(targetMinioFolder, coreCCResponseFileName);

        try (InputStream raoResponseIs = new ByteArrayInputStream(responseMessageBytes)) {
            minioAdapter.uploadOutput(coreCCResponseDestinationPath, raoResponseIs);
        } catch (IOException e) {
            throw new CoreCCInternalException(String.format("Exception occurred while uploading RAO response of task %s", coreCCRequest.getId()));
        }
        coreCCRequest.getDailyOutputs().setCoreCCResponsePath(coreCCResponseDestinationPath);
        return minioAdapter.generatePreSignedUrl(coreCCResponseDestinationPath);
    }

    private void exportCgmXmlHeaderFile(ResponseMessageType responseMessage, String cgmsArchiveTempPath) {
        try {
            byte[] responseMessageBytes = marshallMessageAndSetJaxbProperties(responseMessage);
            File targetFile = new File(cgmsArchiveTempPath, OutputsNamingRules.CGM_XML_HEADER_FILENAME); //NOSONAR

            try (InputStream raoResponseIs = new ByteArrayInputStream(responseMessageBytes)) {
                Files.copy(raoResponseIs, targetFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
            }

        } catch (IOException e) {
            throw new CoreCCInternalException("Exception occurred during CGM_XML_HEADER Response export.", e);
        }
    }

    private byte[] marshallMessageAndSetJaxbProperties(ResponseMessageType responseMessage) {
        try {
            StringWriter stringWriter = new StringWriter();
            JAXBContext jaxbContext = JAXBContext.newInstance(ResponseMessageType.class);
            Marshaller jaxbMarshaller = jaxbContext.createMarshaller();
            jaxbMarshaller.setProperty(Marshaller.JAXB_FORMATTED_OUTPUT, true);
            String eventMessage = "EventMessage";
            QName qName = new QName(XMLConstants.W3C_XML_SCHEMA_INSTANCE_NS_URI, eventMessage);
            JAXBElement<ResponseMessageType> root = new JAXBElement<>(qName, ResponseMessageType.class, responseMessage);
            jaxbMarshaller.marshal(root, stringWriter);
            return stringWriter.toString()
                .replace("xsi:EventMessage", "EventMessage")
                .replace("<EventMessage", "<EventMessage xmlns:xsd=\"http://www.w3.org/2001/XMLSchema\"")
                .replace("<ResponseItems", "<ResponseItems xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\" xmlns:xsd=\"http://www.w3.org/2001/XMLSchema\" xmlns=\"http://unicorn.com/Response/response-payload\"")
                .getBytes();
        } catch (Exception e) {
            throw new CoreCCInternalException("Exception occurred during RAO Response export.", e);
        }
    }
}
