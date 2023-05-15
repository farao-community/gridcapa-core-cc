package com.farao_community.farao.gridcapa_core_cc.app.postprocessing;

import com.farao_community.farao.data.cne_exporter_commons.CneExporterParameters;
import com.farao_community.farao.data.core_cne_exporter.CoreCneExporter;
import com.farao_community.farao.data.crac_api.Crac;
import com.farao_community.farao.data.crac_creation.creator.fb_constraint.crac_creator.FbConstraintCreationContext;
import com.farao_community.farao.data.crac_io_api.CracImporters;
import com.farao_community.farao.data.rao_result_api.RaoResult;
import com.farao_community.farao.gridcapa_core_cc.api.exception.CoreCCInternalException;
import com.farao_community.farao.gridcapa_core_cc.api.exception.CoreCCInvalidDataException;
import com.farao_community.farao.gridcapa_core_cc.api.resource.InternalCoreCCRequest;
import com.farao_community.farao.gridcapa_core_cc.api.resource.HourlyRaoRequest;
import com.farao_community.farao.gridcapa_core_cc.api.resource.HourlyRaoResult;
import com.farao_community.farao.gridcapa_core_cc.app.services.FileImporter;
import com.farao_community.farao.gridcapa_core_cc.app.util.IntervalUtil;
import com.farao_community.farao.minio_adapter.starter.MinioAdapter;
import com.farao_community.farao.rao_api.json.JsonRaoParameters;
import com.farao_community.farao.rao_api.parameters.RaoParameters;
import com.powsybl.commons.datasource.MemDataSource;
import com.powsybl.iidm.network.Generator;
import com.powsybl.iidm.network.Load;
import com.powsybl.iidm.network.Network;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.*;
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

    public FileExporterHelper(MinioAdapter minioAdapter, FileImporter fileImporter) {
        this.minioAdapter = minioAdapter;
        this.fileImporter = fileImporter;
    }

    public void exportNetworkToMinio(InternalCoreCCRequest coreCCRequest) throws IOException {
        HourlyRaoResult hourlyRaoResult = coreCCRequest.getHourlyRaoResult();
        LOGGER.info("Core CC task: '{}', exporting uct network with pra for timestamp: '{}'", coreCCRequest.getId(), hourlyRaoResult.getInstant());

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
        String networkNewFileName = OutputFileNameUtil.generateUctFileName(hourlyRaoResult.getInstant(), coreCCRequest.getVersion());

        try (InputStream is = memDataSource.newInputStream("", "uct")) {
            String networkWithPraFilePath = coreCCRequest.getHourlyRaoRequest().getResultsDestination() + "/" + networkNewFileName;
            minioAdapter.uploadOutput(networkWithPraFilePath, is, "CORE_CC", "CGM", coreCCRequest.getTimestamp().toString());
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
            throw new CoreCCInternalException(String.format("Exception occurred while importing CRAC file: %s. Cause: %s", Path.of(cracFileUrl).getFileName().toString(), e.getMessage()));
        }
    }

    public void exportCneToMinio(InternalCoreCCRequest coreCCRequest) throws IOException {
        HourlyRaoResult hourlyRaoResult = coreCCRequest.getHourlyRaoResult();
        LOGGER.info("Core CC task: '{}', creating CNE Result for timestamp: '{}'", coreCCRequest.getId(), hourlyRaoResult.getInstant());
        //create CNE with input from inputNetwork, outputCracJson and inputCraxXml
        HourlyRaoRequest hourlyRaoRequest = coreCCRequest.getHourlyRaoRequest();

        //get input network
        String networkFileUrl = hourlyRaoRequest.getNetworkFileUrl();
        Network network;
        try (InputStream networkInputStream = minioAdapter.getFile(networkFileUrl)) {
            network = Network.read(Path.of(networkFileUrl).getFileName().toString(), networkInputStream);
        }

        //import input crac xml file and get FbConstraintCreationContext
        String cracXmlFileUrl = coreCCRequest.getCbcora().getUrl();
        FbConstraintCreationContext fbConstraintCreationContext;
        fbConstraintCreationContext = fileImporter.importCrac(cracXmlFileUrl, OffsetDateTime.parse(hourlyRaoResult.getInstant()), network);
        if (!fbConstraintCreationContext.isCreationSuccessful()) {
            throw new CoreCCInvalidDataException("Crac creation context failed for timestamp: " + hourlyRaoResult.getInstant());
        }
        //get crac from hourly inputs
        Crac cracJson = importCracFromHourlyRaoRequest(coreCCRequest);

        //get raoResult from result
        RaoResult raoResult = fileImporter.importRaoResult(hourlyRaoResult.getRaoResultFileUrl(), cracJson);

        //get raoParams from input
        RaoParameters raoParameters;
        try (InputStream raoParametersInputStream = minioAdapter.getFile(hourlyRaoRequest.getRaoParametersFileUrl())) {
            raoParameters = JsonRaoParameters.read(raoParametersInputStream);
        }

        //export CNE
        String cneNewFileName = OutputFileNameUtil.generateCneFileName(hourlyRaoResult.getInstant(), coreCCRequest);

        try (ByteArrayOutputStream outputStreamCne = new ByteArrayOutputStream()) {
            String cneFilePath = hourlyRaoRequest.getResultsDestination() + "/" + cneNewFileName;
            CoreCneExporter cneExporter = new CoreCneExporter();
            CneExporterParameters cneExporterParameters = getCneExporterParameters(coreCCRequest);
            cneExporter.exportCne(cracJson, network, fbConstraintCreationContext, raoResult, raoParameters, cneExporterParameters, outputStreamCne);
            minioAdapter.uploadOutput(cneFilePath, new ByteArrayInputStream(outputStreamCne.toByteArray()), "CORE_CC", "CNE", coreCCRequest.getTimestamp().toString());
        }
    }

    private CneExporterParameters getCneExporterParameters(InternalCoreCCRequest coreCCRequest) {
        return new CneExporterParameters(
                generateCneMRID(coreCCRequest),
                coreCCRequest.getVersion(),
                DOMAIN_ID,
                CneExporterParameters.ProcessType.DAY_AHEAD_CC,
                CoreCCXmlResponseGenerator.SENDER_ID,
                CneExporterParameters.RoleType.REGIONAL_SECURITY_COORDINATOR,
                CoreCCXmlResponseGenerator.RECEIVER_ID,
                CneExporterParameters.RoleType.CAPACITY_COORDINATOR,
                coreCCRequest.getTimeInterval()
        );
    }

    private String generateCneMRID(InternalCoreCCRequest coreCCRequest) {
        return String.format("%s-%s-F299v%s", CoreCCXmlResponseGenerator.SENDER_ID, IntervalUtil.getFormattedBusinessDay(coreCCRequest.getTimestamp()), coreCCRequest.getVersion());
    }

    public void exportMetadataFile(InternalCoreCCRequest coreCCRequest, String targetMinioFolder, boolean isManualRun) {
        try {
            new CoreCCMetadataGenerator(minioAdapter).exportMetadataFile(coreCCRequest, targetMinioFolder, isManualRun);
        } catch (Exception e) {
            LOGGER.error("Could not generate metadata file for rao task {}: {}", coreCCRequest.getId(), e.getMessage());
        }
    }
}
