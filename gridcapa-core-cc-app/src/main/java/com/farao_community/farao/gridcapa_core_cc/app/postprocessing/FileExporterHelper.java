package com.farao_community.farao.gridcapa_core_cc.app.postprocessing;

import com.powsybl.openrao.data.cneexportercommons.CneExporterParameters;
import com.powsybl.openrao.data.corecneexporter.CoreCneExporter;
import com.powsybl.openrao.data.cracapi.Crac;
import com.powsybl.openrao.data.craccreation.creator.fbconstraint.craccreator.FbConstraintCreationContext;
import com.powsybl.openrao.data.cracioapi.CracImporters;
import com.powsybl.openrao.data.raoresultapi.RaoResult;
import com.farao_community.farao.gridcapa_core_cc.api.exception.CoreCCInternalException;
import com.farao_community.farao.gridcapa_core_cc.api.exception.CoreCCInvalidDataException;
import com.farao_community.farao.gridcapa_core_cc.api.resource.CoreCCMetadata;
import com.farao_community.farao.gridcapa_core_cc.api.resource.HourlyRaoRequest;
import com.farao_community.farao.gridcapa_core_cc.api.resource.HourlyRaoResult;
import com.farao_community.farao.gridcapa_core_cc.api.resource.InternalCoreCCRequest;
import com.farao_community.farao.gridcapa_core_cc.api.util.IntervalUtil;
import com.farao_community.farao.gridcapa_core_cc.app.services.FileImporter;
import com.farao_community.farao.gridcapa_core_cc.app.util.NamingRules;
import com.farao_community.farao.minio_adapter.starter.MinioAdapter;
import com.powsybl.openrao.raoapi.json.JsonRaoParameters;
import com.powsybl.openrao.raoapi.parameters.RaoParameters;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.powsybl.commons.datasource.MemDataSource;
import com.powsybl.iidm.network.Generator;
import com.powsybl.iidm.network.Load;
import com.powsybl.iidm.network.Network;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.time.OffsetDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class FileExporterHelper {

    private final MinioAdapter minioAdapter;
    private final FileImporter fileImporter;
    private static final Logger LOGGER = LoggerFactory.getLogger(FileExporterHelper.class);

    private static final String ALEGRO_GEN_BE = "XLI_OB1B_generator";
    private static final String ALEGRO_GEN_DE = "XLI_OB1A_generator";
    private static final String DOMAIN_ID = "10Y1001C--00059P";
    private static final String CORE_CC = "CORE_CC";

    public FileExporterHelper(MinioAdapter minioAdapter, FileImporter fileImporter) {
        this.minioAdapter = minioAdapter;
        this.fileImporter = fileImporter;
    }

    public void exportNetworkToMinio(InternalCoreCCRequest coreCCRequest) throws IOException {
        HourlyRaoResult hourlyRaoResult = coreCCRequest.getHourlyRaoResult();
        LOGGER.info("Core CC task: '{}', exporting uct network with pra for timestamp: '{}'", coreCCRequest.getId(), hourlyRaoResult.getRaoRequestInstant());

        //get input network
        Network network = fileImporter.importNetworkFromUrl(hourlyRaoResult.getNetworkWithPraUrl());
        MemDataSource memDataSource = new MemDataSource();

        // work around until the problem of "Too many loads connected to this bus" is corrected
        removeVirtualLoadsFromNetwork(network);
        // work around until the problem of "Too many generators connected to this bus" is corrected
        removeAlegroVirtualGeneratorsFromNetwork(network);
        // work around until fictitious loads and generators are not created in groovy script anymore
        removeFictitiousGeneratorsFromNetwork(network);
        removeFictitiousLoadsFromNetwork(network);
        network.write("UCTE", new Properties(), memDataSource);
        String networkNewFileName = NamingRules.generateUctFileName(hourlyRaoResult.getRaoRequestInstant(), coreCCRequest.getVersion());

        try (InputStream is = memDataSource.newInputStream("", "uct")) {
            String networkWithPraFilePath = coreCCRequest.getHourlyRaoRequest().getResultsDestination() + "/" + networkNewFileName;
            minioAdapter.uploadOutputForTimestamp(networkWithPraFilePath, is, CORE_CC, "CGM_OUT", coreCCRequest.getTimestamp());
        } catch (Exception e) {
            throw new CoreCCInternalException("Network with PRA could not be uploaded to minio", e);
        }
    }

    private void removeVirtualLoadsFromNetwork(Network network) {
        List<String> virtualLoadsList = new ArrayList<>();
        network.getSubstationStream().forEach(substation -> substation.getVoltageLevels()
            .forEach(voltageLevel -> voltageLevel.getBusBreakerView().getBuses()
                .forEach(bus -> bus.getLoadStream().filter(busLoad -> busLoad.getNameOrId().contains("_virtualLoad")).forEach(virtualLoad -> virtualLoadsList.add(virtualLoad.getNameOrId()))
                )));
        virtualLoadsList.forEach(virtualLoad -> network.getLoad(virtualLoad).remove());
    }

    private void removeAlegroVirtualGeneratorsFromNetwork(Network network) {
        Optional.ofNullable(network.getGenerator(ALEGRO_GEN_BE)).ifPresent(Generator::remove);
        Optional.ofNullable(network.getGenerator(ALEGRO_GEN_DE)).ifPresent(Generator::remove);
    }

    private void removeFictitiousGeneratorsFromNetwork(Network network) {
        Set<String> generatorsToRemove = network.getGeneratorStream().filter(Generator::isFictitious).map(Generator::getId).collect(Collectors.toSet());
        generatorsToRemove.forEach(id -> network.getGenerator(id).remove());
    }

    private void removeFictitiousLoadsFromNetwork(Network network) {
        Set<String> loadsToRemove = network.getLoadStream().filter(Load::isFictitious).map(Load::getId).collect(Collectors.toSet());
        loadsToRemove.forEach(id -> network.getLoad(id).remove());
    }

    Crac importCracFromHourlyRaoRequest(InternalCoreCCRequest coreCCRequest) {
        HourlyRaoRequest hourlyRaoRequest = coreCCRequest.getHourlyRaoRequest();
        String cracFileUrl = hourlyRaoRequest.getCracFileUrl();
        try (InputStream cracFileInputStream = minioAdapter.getFile(cracFileUrl)) {
            return CracImporters.importCrac(Path.of(cracFileUrl).getFileName().toString(), cracFileInputStream);
        } catch (Exception e) {
            throw new CoreCCInternalException(String.format("Exception occurred while importing CRAC file: %s", Path.of(cracFileUrl).getFileName().toString()), e);
        }
    }

    public void exportCneToMinio(InternalCoreCCRequest coreCCRequest) throws IOException {
        HourlyRaoResult hourlyRaoResult = coreCCRequest.getHourlyRaoResult();
        LOGGER.info("Core CC task: '{}', creating CNE Result for timestamp: '{}'", coreCCRequest.getId(), hourlyRaoResult.getRaoRequestInstant());
        //create CNE with input from inputNetwork, outputCracJson and inputCraxXml
        HourlyRaoRequest hourlyRaoRequest = coreCCRequest.getHourlyRaoRequest();

        //get input network
        String networkFileUrl = hourlyRaoRequest.getNetworkFileUrl();
        Network network;
        try (InputStream networkInputStream = minioAdapter.getFile(networkFileUrl)) {
            network = Network.read(Path.of(networkFileUrl).getFileName().toString(), networkInputStream);
        } catch (Exception e) {
            throw new CoreCCInternalException("Network file could not be read", e);
        }

        //import input crac xml file and get FbConstraintCreationContext
        String cracXmlFileUrl = coreCCRequest.getCbcora().getUrl();
        FbConstraintCreationContext fbConstraintCreationContext;
        fbConstraintCreationContext = fileImporter.importCrac(cracXmlFileUrl, OffsetDateTime.parse(hourlyRaoResult.getRaoRequestInstant()), network);
        if (!fbConstraintCreationContext.isCreationSuccessful()) {
            throw new CoreCCInvalidDataException("Crac creation context failed for timestamp: " + hourlyRaoResult.getRaoRequestInstant());
        }
        //get crac from hourly inputs
        Crac cracJson = importCracFromHourlyRaoRequest(coreCCRequest);

        //get raoResult from result
        RaoResult raoResult = fileImporter.importRaoResult(hourlyRaoResult.getRaoResultFileUrl(), cracJson);

        //get raoParams from input
        RaoParameters raoParameters;
        try (InputStream raoParametersInputStream = minioAdapter.getFile(hourlyRaoRequest.getRaoParametersFileUrl())) {
            raoParameters = JsonRaoParameters.read(raoParametersInputStream);
        } catch (Exception e) {
            throw new CoreCCInternalException("Rao parameters file could not be read", e);
        }

        //export CNE
        String cneNewFileName = NamingRules.generateCneFileName(hourlyRaoResult.getRaoRequestInstant(), coreCCRequest);

        try (ByteArrayOutputStream outputStreamCne = new ByteArrayOutputStream()) {
            String cneFilePath = hourlyRaoRequest.getResultsDestination() + "/" + cneNewFileName;
            CoreCneExporter cneExporter = new CoreCneExporter();
            CneExporterParameters cneExporterParameters = getCneExporterParameters(coreCCRequest);
            cneExporter.exportCne(cracJson, network, fbConstraintCreationContext, raoResult, raoParameters, cneExporterParameters, outputStreamCne);
            minioAdapter.uploadOutputForTimestamp(cneFilePath, new ByteArrayInputStream(outputStreamCne.toByteArray()), CORE_CC, "CNE", coreCCRequest.getTimestamp());
        }
    }

    public void exportRaoResultToMinio(InternalCoreCCRequest coreCCRequest) {
        HourlyRaoResult hourlyRaoResult = coreCCRequest.getHourlyRaoResult();
        HourlyRaoRequest hourlyRaoRequest = coreCCRequest.getHourlyRaoRequest();

        String raoResultFilePath = hourlyRaoRequest.getResultsDestination() + "/" + "raoResult.json";
        minioAdapter.uploadOutputForTimestamp(raoResultFilePath, fileImporter.importFileUrlAsInputStream(hourlyRaoResult.getRaoResultFileUrl()), CORE_CC, "RAO_RESULT", coreCCRequest.getTimestamp());
    }

    private CneExporterParameters getCneExporterParameters(InternalCoreCCRequest coreCCRequest) {
        return new CneExporterParameters(
            generateCneMRID(coreCCRequest),
            coreCCRequest.getVersion(),
            DOMAIN_ID,
            CneExporterParameters.ProcessType.DAY_AHEAD_CC,
            NamingRules.XML_RESPONSE_GENERATOR_SENDER_ID,
            CneExporterParameters.RoleType.REGIONAL_SECURITY_COORDINATOR,
            NamingRules.XML_RESPONSE_GENERATOR_RECEIVER_ID,
            CneExporterParameters.RoleType.CAPACITY_COORDINATOR,
            coreCCRequest.getTimeInterval()
        );
    }

    private String generateCneMRID(InternalCoreCCRequest coreCCRequest) {
        return String.format("%s-%s-F299v%s", NamingRules.XML_RESPONSE_GENERATOR_SENDER_ID, IntervalUtil.getBrusselsFormattedBusinessDayFromUtc(coreCCRequest.getTimestamp()), coreCCRequest.getVersion());
    }

    public void exportMetadataToMinio(InternalCoreCCRequest coreCCRequest) throws IOException {
        HourlyRaoResult hourlyRaoResult = coreCCRequest.getHourlyRaoResult();
        LOGGER.info("Core CC task: '{}', creating Metadata result for timestamp: '{}'", coreCCRequest.getId(), hourlyRaoResult.getRaoRequestInstant());
        HourlyRaoRequest hourlyRaoRequest = coreCCRequest.getHourlyRaoRequest();
        String metaDataFileName = NamingRules.generateMetadataFileName(hourlyRaoResult.getRaoRequestInstant(), coreCCRequest);

        try (ByteArrayOutputStream outputStreamMetaData = new ByteArrayOutputStream()) {
            String metaDataFilePath = hourlyRaoRequest.getResultsDestination() + "/" + metaDataFileName;
            CoreCCMetadata metadata = new CoreCCMetadata(coreCCRequest.getRaoRequest().getFilename(),
                coreCCRequest.getRequestReceivedInstant().toString(),
                coreCCRequest.getHourlyRaoResult().getRaoRequestInstant(),
                coreCCRequest.getHourlyRaoResult().getComputationStartInstant().toString(),
                coreCCRequest.getHourlyRaoResult().getComputationEndInstant().toString(),
                coreCCRequest.getTimeInterval(),
                coreCCRequest.getCorrelationId(),
                coreCCRequest.getHourlyRaoResult().getStatus().toString(),
                coreCCRequest.getHourlyRaoResult().getErrorCodeString(),
                coreCCRequest.getHourlyRaoResult().getErrorMessage(),
                coreCCRequest.getVersion());
            new ObjectMapper().writeValue(outputStreamMetaData, metadata);
            minioAdapter.uploadOutputForTimestamp(metaDataFilePath, new ByteArrayInputStream(outputStreamMetaData.toByteArray()), CORE_CC, "METADATA", coreCCRequest.getTimestamp());
        } catch (Exception e) {
            throw new CoreCCInternalException("Metadata could not be uploaded to minio", e);
        }
    }

    public void exportMetadataToMinioWhenPreProcessingFailed(InternalCoreCCRequest coreCCRequest) throws IOException {
        LOGGER.info("Core CC task: '{}', creating Metadata result when preProcessing failed (for coreCCRequest with timestamp: '{}')", coreCCRequest.getId(), coreCCRequest.getTimestamp());
        HourlyRaoRequest hourlyRaoRequest = coreCCRequest.getHourlyRaoRequest();

        String metaDataFileName = NamingRules.generateMetadataFileName(coreCCRequest.getTimestamp().toInstant().toString(), coreCCRequest);

        try (ByteArrayOutputStream outputStreamMetaData = new ByteArrayOutputStream()) {
            String metaDataFilePath = hourlyRaoRequest.getResultsDestination() + "/" + metaDataFileName;
            CoreCCMetadata metadata = new CoreCCMetadata(coreCCRequest.getRaoRequest().getFilename(),
                coreCCRequest.getRequestReceivedInstant().toString(),
                coreCCRequest.getHourlyRaoResult().getRaoRequestInstant(),
                null,
                null,
                coreCCRequest.getTimeInterval(),
                coreCCRequest.getCorrelationId(),
                coreCCRequest.getHourlyRaoResult().getStatus().toString(),
                coreCCRequest.getHourlyRaoResult().getErrorCodeString(),
                coreCCRequest.getHourlyRaoResult().getErrorMessage(),
                coreCCRequest.getVersion());
            new ObjectMapper().writeValue(outputStreamMetaData, metadata);
            minioAdapter.uploadOutputForTimestamp(metaDataFilePath, new ByteArrayInputStream(outputStreamMetaData.toByteArray()), CORE_CC, "METADATA", coreCCRequest.getTimestamp());
        } catch (Exception e) {
            throw new CoreCCInternalException("Metadata could not be uploaded to minio", e);
        }
    }
}
