package com.farao_community.farao.gridcapa_core_cc.app.postprocessing;

import com.farao_community.farao.data.cne_exporter_commons.CneExporterParameters;
import com.farao_community.farao.data.core_cne_exporter.CoreCneExporter;
import com.farao_community.farao.data.crac_api.Crac;
import com.farao_community.farao.data.crac_creation.creator.fb_constraint.crac_creator.FbConstraintCreationContext;
import com.farao_community.farao.data.crac_io_api.CracImporters;
import com.farao_community.farao.data.rao_result_api.RaoResult;
import com.farao_community.farao.data.rao_result_json.RaoResultImporter;
import com.farao_community.farao.gridcapa_core_cc.api.exception.CoreCCInternalException;
import com.farao_community.farao.gridcapa_core_cc.api.resource.InternalCoreCCRequest;
import com.farao_community.farao.gridcapa_core_cc.api.resource.HourlyRaoRequest;
import com.farao_community.farao.gridcapa_core_cc.api.resource.HourlyRaoResult;
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

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class FileExporterHelper {

    private final MinioAdapter minioAdapter;

    private static final Logger LOGGER = LoggerFactory.getLogger(FileExporterHelper.class);

    private static final String ALEGRO_GEN_BE = "XLI_OB1B_generator";
    private static final String ALEGRO_GEN_DE = "XLI_OB1A_generator";
    private static final String DOMAIN_ID = "10Y1001C--00059P";

    public FileExporterHelper(MinioAdapter minioAdapter) {
        this.minioAdapter = minioAdapter;
    }

    public void exportNetworkInTmpOutput(InternalCoreCCRequest coreCCRequest, HourlyRaoResult hourlyRaoResult) throws IOException {
        LOGGER.info("Core CC task: '{}', exporting uct network with pra for timestamp: '{}'", coreCCRequest.getId(), hourlyRaoResult.getInstant());

        Network network;
        try (InputStream cgmInputStream = minioAdapter.getFile(hourlyRaoResult.getNetworkWithPraUrl())) {
            network = Network.read(Path.of(hourlyRaoResult.getNetworkWithPraUrl()).getFileName().toString(), cgmInputStream);
        }
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
        File targetFile = new File(coreCCRequest.getDailyOutputs().getNetworkTmpOutputsPath(), networkNewFileName); //NOSONAR

        try (InputStream is = memDataSource.newInputStream("", "uct")) {
            Files.copy(is, targetFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
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

    Crac importCracFromHourlyRaoRequest(InternalCoreCCRequest coreCCRequest, HourlyRaoResult raoResult) {
        HourlyRaoRequest hourlyRaoRequest = coreCCRequest.getHourlyRequestFromResponse(raoResult);
        String cracFileUrl = hourlyRaoRequest.getCracFileUrl();
        try (InputStream cracFileInputStream = minioAdapter.getFile(cracFileUrl)) {
            return CracImporters.importCrac(Path.of(cracFileUrl).getFileName().toString(), cracFileInputStream);
        } catch (Exception e) {
            throw new CoreCCInternalException(String.format("Exception occurred while importing CRAC file: %s. Cause: %s", Path.of(cracFileUrl).getFileName().toString(), e.getMessage()));
        }
    }

    public void exportCneInTmpOutput(InternalCoreCCRequest coreCCRequest, HourlyRaoResult hourlyRaoResult) throws IOException {
        LOGGER.info("Core CC task: '{}', creating CNE Result for timestamp: '{}'", coreCCRequest.getId(), hourlyRaoResult.getInstant());
        //create CNE with input from inputNetwork, outputCracJson and inputCraxXml
        HourlyRaoRequest hourlyRaoRequest = coreCCRequest.getHourlyRaoRequests().stream().filter(request -> request.getInstant().equals(hourlyRaoResult.getInstant()))
                .findFirst().orElseThrow(() -> new CoreCCInternalException(String.format("Exception occurred while creating CNE file for timestamp %s. Cause: no rao result.", hourlyRaoResult.getInstant())));

        //get input network
        String networkFileUrl = hourlyRaoRequest.getNetworkFileUrl();
        Network network;
        LOGGER.info("NetworkFileUrl: {}", networkFileUrl);
        try (InputStream networkInputStream = minioAdapter.getFile(networkFileUrl)) {
            network = Network.read(Path.of(networkFileUrl).getFileName().toString(), networkInputStream);
        }

        //import input crac xml file and get FbConstraintCreationContext
        String cracXmlFileUrl = coreCCRequest.getCbcora().getUrl();
        FbConstraintCreationContext fbConstraintCreationContext;
        try (InputStream cracInputStream = minioAdapter.getFile(cracXmlFileUrl)) {
            fbConstraintCreationContext = CracHelper.importCracXmlGetFbInfoWithNetwork(hourlyRaoResult.getInstant(), network, cracInputStream);
        }

        //get crac from hourly inputs
        Crac cracJson = importCracFromHourlyRaoRequest(coreCCRequest, hourlyRaoResult);

        //get raoResult from result
        RaoResult raoResult;
        try (InputStream raoResultInputStream = minioAdapter.getFile(hourlyRaoResult.getRaoResultFileUrl())) {
            RaoResultImporter raoResultImporter = new RaoResultImporter();
            raoResult = raoResultImporter.importRaoResult(raoResultInputStream, cracJson);
        }
        //get raoParams from input
        RaoParameters raoParameters;
        try (InputStream raoParametersInputStream = minioAdapter.getFile(hourlyRaoRequest.getRaoParametersFileUrl())) {
            raoParameters = JsonRaoParameters.read(raoParametersInputStream);
        }

        //export CNE
        String cneNewFileName = OutputFileNameUtil.generateCneFileName(hourlyRaoResult.getInstant(), coreCCRequest);
        File targetFile = new File(coreCCRequest.getDailyOutputs().getCneTmpOutputsPath(), cneNewFileName); //NOSONAR

        try (FileOutputStream outputStreamCne = new FileOutputStream(targetFile)) {
            CoreCneExporter cneExporter = new CoreCneExporter();
            CneExporterParameters cneExporterParameters = getCneExporterParameters(coreCCRequest);
            cneExporter.exportCne(cracJson, network, fbConstraintCreationContext, raoResult, raoParameters, cneExporterParameters, outputStreamCne);

            //remember mrid f299 for f305 rao response payload
            hourlyRaoResult.setCneResultDocumentId(cneExporterParameters.getDocumentId());
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
