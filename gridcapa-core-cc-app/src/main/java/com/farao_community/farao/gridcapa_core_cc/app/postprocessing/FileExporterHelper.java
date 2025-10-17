/*
 * Copyright (c) 2024, RTE (http://www.rte-france.com)
 *  This Source Code Form is subject to the terms of the Mozilla Public
 *  License, v. 2.0. If a copy of the MPL was not distributed with this
 *  file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.farao_community.farao.gridcapa_core_cc.app.postprocessing;

import com.farao_community.farao.gridcapa_core_cc.api.exception.CoreCCInternalException;
import com.farao_community.farao.gridcapa_core_cc.api.exception.CoreCCInvalidDataException;
import com.farao_community.farao.gridcapa_core_cc.api.resource.CoreCCMetadata;
import com.farao_community.farao.gridcapa_core_cc.api.resource.HourlyRaoRequest;
import com.farao_community.farao.gridcapa_core_cc.api.resource.HourlyRaoResult;
import com.farao_community.farao.gridcapa_core_cc.api.resource.InternalCoreCCRequest;
import com.farao_community.farao.gridcapa_core_cc.api.util.IntervalUtil;
import com.farao_community.farao.gridcapa_core_cc.app.domain.CoreCCTaskParameters;
import com.farao_community.farao.gridcapa_core_cc.app.services.FileImporter;
import com.farao_community.farao.gridcapa_core_cc.app.util.NamingRules;
import com.farao_community.farao.minio_adapter.starter.MinioAdapter;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.powsybl.commons.datasource.MemDataSource;
import com.powsybl.iidm.network.Bus;
import com.powsybl.iidm.network.Generator;
import com.powsybl.iidm.network.Load;
import com.powsybl.iidm.network.Network;
import com.powsybl.iidm.network.Substation;
import com.powsybl.openrao.data.crac.api.Crac;
import com.powsybl.openrao.data.crac.io.fbconstraint.FbConstraintCreationContext;
import com.powsybl.openrao.data.raoresult.api.RaoResult;
import com.powsybl.openrao.data.raoresult.io.cne.core.CoreCneExporter;
import com.powsybl.openrao.raoapi.json.JsonRaoParameters;
import com.powsybl.openrao.raoapi.parameters.MnecParameters;
import com.powsybl.openrao.raoapi.parameters.RaoParameters;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.time.OffsetDateTime;
import java.util.HashSet;
import java.util.Optional;
import java.util.Properties;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Predicate;

import static com.farao_community.farao.gridcapa_core_cc.app.postprocessing.CneProperties.DOCUMENT_ID;
import static com.farao_community.farao.gridcapa_core_cc.app.postprocessing.CneProperties.MNEC_ACCEPTABLE_MARGIN_DIMINUTION;
import static com.farao_community.farao.gridcapa_core_cc.app.postprocessing.CneProperties.PROCESS_TYPE;
import static com.farao_community.farao.gridcapa_core_cc.app.postprocessing.CneProperties.RECEIVER_ID;
import static com.farao_community.farao.gridcapa_core_cc.app.postprocessing.CneProperties.RECEIVER_ROLE;
import static com.farao_community.farao.gridcapa_core_cc.app.postprocessing.CneProperties.RELATIVE_POSITIVE_MARGINS;
import static com.farao_community.farao.gridcapa_core_cc.app.postprocessing.CneProperties.REVISION_NUMBER;
import static com.farao_community.farao.gridcapa_core_cc.app.postprocessing.CneProperties.SENDER_ID;
import static com.farao_community.farao.gridcapa_core_cc.app.postprocessing.CneProperties.SENDER_ROLE;
import static com.farao_community.farao.gridcapa_core_cc.app.postprocessing.CneProperties.TIME_INTERVAL;
import static com.farao_community.farao.gridcapa_core_cc.app.postprocessing.CneProperties.WITH_LOOP_FLOWS;
import static com.farao_community.farao.gridcapa_core_cc.app.util.NamingRules.XML_RESPONSE_GENERATOR_RECEIVER_ID;
import static com.farao_community.farao.gridcapa_core_cc.app.util.NamingRules.XML_RESPONSE_GENERATOR_SENDER_ID;
import static com.farao_community.farao.gridcapa_core_cc.app.util.NamingRules.generateMetadataFileName;
import static com.farao_community.farao.gridcapa_core_cc.app.util.NamingRules.generateRaoResultFileName;
import static java.lang.String.valueOf;

@Service
public class FileExporterHelper {

    private final MinioAdapter minioAdapter;
    private final FileImporter fileImporter;
    private final RegularOrDcCgmNetworkResolver regularOrDcCgmNetworkResolver;
    private static final Logger LOGGER = LoggerFactory.getLogger(FileExporterHelper.class);

    private static final String ALEGRO_GEN_BE = "XLI_OB1B_generator";
    private static final String ALEGRO_GEN_DE = "XLI_OB1A_generator";
    private static final String DOMAIN_ID = "10Y1001C--00059P";
    private static final String CORE_CC = "CORE_CC";

    public FileExporterHelper(final MinioAdapter minioAdapter,
                              final FileImporter fileImporter,
                              final RegularOrDcCgmNetworkResolver regularOrDcCgmNetworkResolver) {
        this.minioAdapter = minioAdapter;
        this.fileImporter = fileImporter;
        this.regularOrDcCgmNetworkResolver = regularOrDcCgmNetworkResolver;
    }

    private static String buildFilePath(final String destination,
                                        final String filename) {
        return destination + "/" + filename;
    }

    public void exportNetworkToMinio(final InternalCoreCCRequest coreCCRequest) {
        final HourlyRaoResult hourlyRaoResult = coreCCRequest.getHourlyRaoResult();
        LOGGER.info("Core CC task: '{}', exporting uct network with pra for timestamp: '{}'",
                    coreCCRequest.getId(),
                    hourlyRaoResult.getRaoRequestInstant());

        //get input network
        final CoreCCTaskParameters parameters = new CoreCCTaskParameters(coreCCRequest.getParameters());
        final Network network = regularOrDcCgmNetworkResolver.resolve(parameters.isUseDcCgmInput(), coreCCRequest);
        final MemDataSource memDataSource = new MemDataSource();

        // work around until the problem of "Too many loads connected to this bus" is corrected
        removeVirtualLoadsFromNetwork(network);
        // work around until the problem of "Too many generators connected to this bus" is corrected
        removeAlegroVirtualGeneratorsFromNetwork(network);
        // work around until fictitious loads and generators are not created in groovy script anymore
        removeFictitiousGeneratorsFromNetwork(network);
        removeFictitiousLoadsFromNetwork(network);
        network.write("UCTE", new Properties(), memDataSource);
        final String networkNewFileName = NamingRules.generateUctFileName(hourlyRaoResult.getRaoRequestInstant(),
                                                                          coreCCRequest.getVersion());

        try (final InputStream is = memDataSource.newInputStream("", "uct")) {
            final String networkWithPraFilePath = buildFilePath(coreCCRequest.getHourlyRaoRequest().getResultsDestination(), networkNewFileName);
            minioAdapter.uploadOutputForTimestamp(networkWithPraFilePath, is, CORE_CC, "CGM_OUT", coreCCRequest.getTimestamp());
        } catch (final Exception e) {
            throw new CoreCCInternalException("Network with PRA could not be uploaded to minio", e);
        }
    }

    private void removeVirtualLoadsFromNetwork(final Network network) {
        network.getSubstationStream()
                .flatMap(Substation::getVoltageLevelStream)
                .flatMap(level -> level.getBusBreakerView().getBusStream())
                .flatMap(Bus::getLoadStream)
                .map(Load::getNameOrId)
                .filter(id -> id.contains("_virtualLoad"))
                .map(network::getLoad)
                .forEach(Load::remove);
    }

    private void removeAlegroVirtualGeneratorsFromNetwork(final Network network) {
        Optional.ofNullable(network.getGenerator(ALEGRO_GEN_BE)).ifPresent(Generator::remove);
        Optional.ofNullable(network.getGenerator(ALEGRO_GEN_DE)).ifPresent(Generator::remove);
    }

    private void removeFictitiousGeneratorsFromNetwork(final Network network) {
        network.getGeneratorStream()
                .filter(Generator::isFictitious)
                .filter(distinctByProperty(Generator::getId))
                .forEach(Generator::remove);
    }

    private void removeFictitiousLoadsFromNetwork(final Network network) {
        network.getLoadStream()
                .filter(Load::isFictitious)
                .filter(distinctByProperty(Load::getId))
                .forEach(Load::remove);
    }

    /**
     * Predicate used to eliminate duplicates by property
     *
     * @param propertyGetter getter of the property
     * @param <T>            class being filtered
     * @param <R>            class of property
     * @return <T> T
     */
    private static <T, R> Predicate<T> distinctByProperty(Function<? super T, R> propertyGetter) {
        final Set<R> seen = new HashSet<>();
        return t -> seen.add(propertyGetter.apply(t));
    }

    public void exportCneToMinio(final InternalCoreCCRequest coreCCRequest) throws IOException {
        final HourlyRaoResult hourlyRaoResult = coreCCRequest.getHourlyRaoResult();
        final String requestInstant = hourlyRaoResult.getRaoRequestInstant();
        LOGGER.info("Core CC task: '{}', creating CNE Result for timestamp: '{}'", coreCCRequest.getId(), requestInstant);
        //create CNE with input from inputNetwork, outputCracJson and inputCraxXml
        final HourlyRaoRequest hourlyRaoRequest = coreCCRequest.getHourlyRaoRequest();

        //get input network
        final String networkFileUrl = hourlyRaoRequest.getNetworkFileUrl();
        final Network network;
        try (final InputStream networkInputStream = minioAdapter.getFile(networkFileUrl)) {
            network = Network.read(Path.of(networkFileUrl).getFileName().toString(), networkInputStream);
        } catch (final Exception e) {
            throw new CoreCCInternalException("Network file could not be read", e);
        }

        //import input crac xml file and get FbConstraintCreationContext
        final String cracXmlFileUrl = coreCCRequest.getCbcora().getUrl();
        final FbConstraintCreationContext fbConstraintCreationContext = fileImporter.importCrac(cracXmlFileUrl,
                                                                                                OffsetDateTime.parse(requestInstant),
                                                                                                network);
        if (!fbConstraintCreationContext.isCreationSuccessful()) {
            throw new CoreCCInvalidDataException("Crac creation context failed for timestamp: " + requestInstant);
        }
        //get crac from hourly inputs
        final Crac cracJson = fileImporter.importCracFromHourlyRaoRequest(coreCCRequest, network);

        //get raoResult from result
        final RaoResult raoResult = fileImporter.importRaoResult(hourlyRaoResult.getRaoResultFileUrl(), cracJson);

        //get raoParams from input
        final RaoParameters raoParameters;
        try (final InputStream raoParametersInputStream = minioAdapter.getFile(hourlyRaoRequest.getRaoParametersFileUrl())) {
            raoParameters = JsonRaoParameters.read(raoParametersInputStream);
        } catch (final Exception e) {
            throw new CoreCCInternalException("Rao parameters file could not be read", e);
        }

        //export CNE
        final String cneNewFileName = NamingRules.generateCneFileName(requestInstant, coreCCRequest);

        try (final ByteArrayOutputStream outputStreamCne = new ByteArrayOutputStream()) {
            final String cneFilePath = buildFilePath(hourlyRaoRequest.getResultsDestination(), cneNewFileName);
            final CoreCneExporter cneExporter = new CoreCneExporter();
            final Properties properties = getCneExporterProperties(coreCCRequest, raoParameters);
            cneExporter.exportData(raoResult, fbConstraintCreationContext, properties, outputStreamCne);
            minioAdapter.uploadOutputForTimestamp(cneFilePath,
                                                  new ByteArrayInputStream(outputStreamCne.toByteArray()),
                                                  CORE_CC,
                                                  "CNE",
                                                  coreCCRequest.getTimestamp());
        }
    }

    public void exportRaoResultToMinio(final InternalCoreCCRequest coreCCRequest) {
        final HourlyRaoResult hourlyRaoResult = coreCCRequest.getHourlyRaoResult();
        final HourlyRaoRequest hourlyRaoRequest = coreCCRequest.getHourlyRaoRequest();
        final OffsetDateTime coreCCRequestTimestamp = coreCCRequest.getTimestamp();
        final String raoResultFilePath = buildFilePath(hourlyRaoRequest.getResultsDestination(),
                                                       generateRaoResultFileName(hourlyRaoResult.getRaoRequestInstant()));
        minioAdapter.uploadOutputForTimestamp(raoResultFilePath,
                                              fileImporter.importFileUrlAsInputStream(hourlyRaoResult.getRaoResultFileUrl()),
                                              CORE_CC,
                                              "RAO_RESULT",
                                              coreCCRequestTimestamp);
    }

    private Properties getCneExporterProperties(final InternalCoreCCRequest coreCCRequest,
                                                final RaoParameters raoParameters) {
        final Properties properties = new Properties();
        properties.setProperty(RELATIVE_POSITIVE_MARGINS.getPrefixedKey(),
                               valueOf(raoParameters.getObjectiveFunctionParameters().getType().relativePositiveMargins()));
        properties.setProperty(WITH_LOOP_FLOWS.getPrefixedKey(),
                               valueOf(raoParameters.getLoopFlowParameters().isPresent()));
        //If no value exists in raoParameters, we use default value
        final MnecParameters mnecParameters = raoParameters.getMnecParameters().orElseGet(MnecParameters::new);
        properties.setProperty(MNEC_ACCEPTABLE_MARGIN_DIMINUTION.getPrefixedKey(),
                               valueOf(mnecParameters.getAcceptableMarginDecrease()));
        properties.setProperty(DOCUMENT_ID.getPrefixedKey(), generateCneMRID(coreCCRequest));
        properties.setProperty(REVISION_NUMBER.getPrefixedKey(), valueOf(coreCCRequest.getVersion()));
        properties.setProperty(CneProperties.DOMAIN_ID.getPrefixedKey(), DOMAIN_ID);
        properties.setProperty(PROCESS_TYPE.getPrefixedKey(), "A48");
        properties.setProperty(SENDER_ID.getPrefixedKey(), XML_RESPONSE_GENERATOR_SENDER_ID);
        properties.setProperty(SENDER_ROLE.getPrefixedKey(), "A44");
        properties.setProperty(RECEIVER_ID.getPrefixedKey(), XML_RESPONSE_GENERATOR_RECEIVER_ID);
        properties.setProperty(RECEIVER_ROLE.getPrefixedKey(), "A36");
        properties.setProperty(TIME_INTERVAL.getPrefixedKey(), coreCCRequest.getTimeInterval());
        return properties;
    }

    private String generateCneMRID(final InternalCoreCCRequest coreCCRequest) {
        return String.format("%s-%s-F299v%s", XML_RESPONSE_GENERATOR_SENDER_ID, IntervalUtil.getBrusselsFormattedBusinessDayFromUtc(coreCCRequest.getTimestamp()), coreCCRequest.getVersion());
    }

    public void exportMetadataToMinio(final InternalCoreCCRequest coreCCRequest) {
        final HourlyRaoResult hourlyRaoResult = coreCCRequest.getHourlyRaoResult();
        LOGGER.info("Core CC task: '{}', creating Metadata result for timestamp: '{}'", coreCCRequest.getId(), hourlyRaoResult.getRaoRequestInstant());
        final HourlyRaoRequest hourlyRaoRequest = coreCCRequest.getHourlyRaoRequest();
        final String metaDataFileName = generateMetadataFileName(hourlyRaoResult.getRaoRequestInstant(), coreCCRequest);

        try (final ByteArrayOutputStream outputStreamMetaData = new ByteArrayOutputStream()) {
            final String metaDataFilePath = buildFilePath(hourlyRaoRequest.getResultsDestination(), metaDataFileName);
            final CoreCCMetadata metadata = new CoreCCMetadata(coreCCRequest.getRaoRequest().getFilename(),
                                                               coreCCRequest.getRequestReceivedInstant().toString(),
                                                               hourlyRaoResult.getRaoRequestInstant(),
                                                               hourlyRaoResult.getComputationStartInstant().toString(),
                                                               hourlyRaoResult.getComputationEndInstant().toString(),
                                                               coreCCRequest.getTimeInterval(),
                                                               coreCCRequest.getCorrelationId(),
                                                               hourlyRaoResult.getStatus().toString(),
                                                               hourlyRaoResult.getErrorCodeString(),
                                                               hourlyRaoResult.getErrorMessage(),
                                                               coreCCRequest.getVersion());
            new ObjectMapper().writeValue(outputStreamMetaData, metadata);
            minioAdapter.uploadOutputForTimestamp(metaDataFilePath,
                                                  new ByteArrayInputStream(outputStreamMetaData.toByteArray()),
                                                  CORE_CC,
                                                  "METADATA",
                                                  coreCCRequest.getTimestamp());
        } catch (final Exception e) {
            throw new CoreCCInternalException("Metadata could not be uploaded to minio", e);
        }
    }

    public void exportMetadataToMinioWhenPreProcessingFailed(final InternalCoreCCRequest coreCCRequest) {
        final OffsetDateTime requestTime = coreCCRequest.getTimestamp();

        LOGGER.info("Core CC task: '{}', creating Metadata result when preProcessing failed (for coreCCRequest with timestamp: '{}')",
                    coreCCRequest.getId(), requestTime);

        final HourlyRaoRequest hourlyRaoRequest = coreCCRequest.getHourlyRaoRequest();

        final String metaDataFileName = generateMetadataFileName(requestTime.toInstant().toString(), coreCCRequest);

        try (final ByteArrayOutputStream outputStreamMetaData = new ByteArrayOutputStream()) {
            final String metaDataFilePath = buildFilePath(hourlyRaoRequest.getResultsDestination(), metaDataFileName);
            final HourlyRaoResult hourlyRaoResult = coreCCRequest.getHourlyRaoResult();
            final CoreCCMetadata metadata = new CoreCCMetadata(coreCCRequest.getRaoRequest().getFilename(),
                                                               coreCCRequest.getRequestReceivedInstant().toString(),
                                                               hourlyRaoResult.getRaoRequestInstant(),
                                                               null,
                                                               null,
                                                               coreCCRequest.getTimeInterval(),
                                                               coreCCRequest.getCorrelationId(),
                                                               hourlyRaoResult.getStatus().toString(),
                                                               hourlyRaoResult.getErrorCodeString(),
                                                               hourlyRaoResult.getErrorMessage(),
                                                               coreCCRequest.getVersion());
            new ObjectMapper().writeValue(outputStreamMetaData, metadata);
            minioAdapter.uploadOutputForTimestamp(metaDataFilePath,
                                                  new ByteArrayInputStream(outputStreamMetaData.toByteArray()),
                                                  CORE_CC,
                                                  "METADATA",
                                                  requestTime);
        } catch (final Exception e) {
            throw new CoreCCInternalException("Metadata could not be uploaded to minio", e);
        }
    }
}
