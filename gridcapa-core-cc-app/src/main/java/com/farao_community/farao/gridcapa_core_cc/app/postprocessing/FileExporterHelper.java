/*
 * Copyright (c) 2024, RTE (http://www.rte-france.com)
 *  This Source Code Form is subject to the terms of the Mozilla Public
 *  License, v. 2.0. If a copy of the MPL was not distributed with this
 *  file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.farao_community.farao.gridcapa_core_cc.app.postprocessing;

import com.farao_community.farao.gridcapa_core_cc.api.exception.CoreCCInternalException;
import com.farao_community.farao.gridcapa_core_cc.api.resource.CoreCCMetadata;
import com.farao_community.farao.gridcapa_core_cc.api.resource.HourlyRaoRequest;
import com.farao_community.farao.gridcapa_core_cc.api.resource.HourlyRaoResult;
import com.farao_community.farao.gridcapa_core_cc.api.resource.InternalCoreCCRequest;
import com.farao_community.farao.gridcapa_core_cc.api.util.IntervalUtil;
import com.farao_community.farao.gridcapa_core_cc.app.util.NamingRules;
import com.farao_community.farao.gridcapa_core_cc.app.util.NetworkUtil;
import com.farao_community.farao.minio_adapter.starter.MinioAdapter;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.powsybl.commons.datasource.MemDataSource;
import com.powsybl.iidm.network.Network;
import com.powsybl.iidm.network.VariantManager;
import com.powsybl.openrao.commons.Unit;
import com.powsybl.openrao.data.crac.api.State;
import com.powsybl.openrao.data.raoresult.api.RaoResult;
import com.powsybl.openrao.data.raoresult.io.cne.core.CoreCneExporter;
import com.powsybl.openrao.raoapi.parameters.MnecParameters;
import com.powsybl.openrao.raoapi.parameters.RaoParameters;
import com.powsybl.openrao.searchtreerao.commons.RaoUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.time.OffsetDateTime;
import java.util.Objects;
import java.util.Properties;

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
import static java.lang.String.valueOf;

@Service
public class FileExporterHelper {
    private static final Logger LOGGER = LoggerFactory.getLogger(FileExporterHelper.class);

    private static final String DOMAIN_ID = "10Y1001C--00059P";
    private static final String CORE_CC = "CORE_CC";

    private final MinioAdapter minioAdapter;

    public FileExporterHelper(final MinioAdapter minioAdapter) {
        this.minioAdapter = minioAdapter;
    }

    private static String buildFilePath(final String destination, final String filename) {
        return destination + "/" + filename;
    }

    public void exportNetworkToMinio(final CoreCCPostProcessingData postProcessingData) {
        final InternalCoreCCRequest coreCCRequest = postProcessingData.getRequest();
        LOGGER.info("Core CC task: '{}', exporting UCT network with PRA for timestamp: '{}'", coreCCRequest.getId(), postProcessingData.getRaoRequestInstant());

        // RAO was performed on DC network, but we want to export AC network with PRA.
        // Therefore, we need to apply on AC network the remedial actions that were activated at preventive state on DC network.
        final Network network = postProcessingData.getAcNetwork();
        final VariantManager networkVariantManager = network.getVariantManager();
        final String workingVariant = networkVariantManager.getWorkingVariantId();
        networkVariantManager.setWorkingVariant(postProcessingData.getInitialAcNetworkVariantId());
        final String networkWithPraVariant = "networkWithPra";
        networkVariantManager.cloneVariant(postProcessingData.getInitialAcNetworkVariantId(), networkWithPraVariant);
        final State preventiveState = postProcessingData.getCrac().getPreventiveState();

        NetworkUtil.applyActivatedRemedialActionsForState(network, postProcessingData.getRaoResult(), preventiveState);
        NetworkUtil.applyWorkaround(network); // TODO Remove this when problems will be solved

        final MemDataSource memDataSource = new MemDataSource();
        network.write("UCTE", new Properties(), memDataSource);

        try (final InputStream is = memDataSource.newInputStream("", "uct")) {
            final String networkNewFileName = NamingRules.generateUctFileName(postProcessingData.getRaoRequestInstant(), coreCCRequest.getVersion());
            final String networkWithPraFilePath = buildFilePath(postProcessingData.getResultsDestination(), networkNewFileName);
            minioAdapter.uploadOutputForTimestamp(networkWithPraFilePath, is, CORE_CC, "CGM_OUT", coreCCRequest.getTimestamp());
        } catch (final Exception e) {
            throw new CoreCCInternalException("Network with PRA could not be uploaded to minio", e);
        } finally {
            networkVariantManager.setWorkingVariant(workingVariant);
            networkVariantManager.removeVariant(networkWithPraVariant);
        }
    }

    public void exportCneToMinio(final CoreCCPostProcessingData postProcessingData) {
        final InternalCoreCCRequest coreCCRequest = postProcessingData.getRequest();
        LOGGER.info("Core CC task: '{}', exporting CNE result for timestamp: '{}'", coreCCRequest.getId(), postProcessingData.getRaoRequestInstant());

        try (final ByteArrayOutputStream outputStreamCne = new ByteArrayOutputStream()) {
            final CoreCneExporter cneExporter = new CoreCneExporter();
            final Properties properties = getCneExporterProperties(coreCCRequest, postProcessingData.getRaoParameters());
            cneExporter.exportData(
                postProcessingData.getRaoResult(),
                postProcessingData.getCracCreationContext(),
                properties,
                outputStreamCne
            );
            try (final ByteArrayInputStream inputStream = new ByteArrayInputStream(outputStreamCne.toByteArray())) {
                final String cneFilename = NamingRules.generateCneFileName(postProcessingData.getRaoRequestInstant(), coreCCRequest);
                final String cneFilePath = buildFilePath(postProcessingData.getResultsDestination(), cneFilename);
                minioAdapter.uploadOutputForTimestamp(cneFilePath, inputStream, CORE_CC, "CNE", coreCCRequest.getTimestamp());
            }
        } catch (final Exception e) {
            throw new CoreCCInternalException("CNE could not be uploaded to minio", e);
        }
    }

    private Properties getCneExporterProperties(final InternalCoreCCRequest coreCCRequest,
                                                final RaoParameters raoParameters) {
        final Properties properties = new Properties();
        properties.setProperty(
            RELATIVE_POSITIVE_MARGINS.getPrefixedKey(),
            valueOf(raoParameters.getObjectiveFunctionParameters().getType().relativePositiveMargins())
        );
        properties.setProperty(
            WITH_LOOP_FLOWS.getPrefixedKey(),
            valueOf(raoParameters.getLoopFlowParameters().isPresent())
        );
        //If no value exists in raoParameters, we use default value
        final MnecParameters mnecParameters = raoParameters.getMnecParameters().orElseGet(MnecParameters::new);
        properties.setProperty(
            MNEC_ACCEPTABLE_MARGIN_DIMINUTION.getPrefixedKey(),
            valueOf(mnecParameters.getAcceptableMarginDecrease())
        );
        properties.setProperty(DOCUMENT_ID.getPrefixedKey(), generateCneMRID(coreCCRequest));
        properties.setProperty(REVISION_NUMBER.getPrefixedKey(), valueOf(coreCCRequest.getVersion()));
        properties.setProperty(CneProperties.DOMAIN_ID.getPrefixedKey(), DOMAIN_ID);
        properties.setProperty(PROCESS_TYPE.getPrefixedKey(), "A48");
        properties.setProperty(SENDER_ID.getPrefixedKey(), NamingRules.XML_RESPONSE_GENERATOR_SENDER_ID);
        properties.setProperty(SENDER_ROLE.getPrefixedKey(), "A44");
        properties.setProperty(RECEIVER_ID.getPrefixedKey(), NamingRules.XML_RESPONSE_GENERATOR_RECEIVER_ID);
        properties.setProperty(RECEIVER_ROLE.getPrefixedKey(), "A36");
        properties.setProperty(TIME_INTERVAL.getPrefixedKey(), coreCCRequest.getTimeInterval());
        return properties;
    }

    private String generateCneMRID(final InternalCoreCCRequest coreCCRequest) {
        return String.format("%s-%s-F299v%s", NamingRules.XML_RESPONSE_GENERATOR_SENDER_ID, IntervalUtil.getBrusselsFormattedBusinessDayFromUtc(coreCCRequest.getTimestamp()), coreCCRequest.getVersion());
    }

    public void exportRaoResultToMinio(final CoreCCPostProcessingData postProcessingData) {
        final InternalCoreCCRequest coreCCRequest = postProcessingData.getRequest();
        LOGGER.info("Core CC task: '{}', exporting RAO result for timestamp: '{}'", coreCCRequest.getId(), postProcessingData.getRaoRequestInstant());

        try (final ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            final RaoResult raoResult = postProcessingData.getRaoResult();
            final Unit unit = RaoUtil.getFlowUnit(postProcessingData.getRaoParameters());

            raoResult.write("JSON", postProcessingData.getCrac(), generateJsonProperties(unit), outputStream);
            try (final ByteArrayInputStream inputStream = new ByteArrayInputStream(outputStream.toByteArray())) {
                final String resultsDestination = postProcessingData.getResultsDestination();
                final String raoResultFilename = NamingRules.generateRaoResultFileName(postProcessingData.getRaoRequestInstant());
                final String raoResultFilePath = buildFilePath(resultsDestination, raoResultFilename);
                final OffsetDateTime coreCCRequestTimestamp = coreCCRequest.getTimestamp();
                minioAdapter.uploadOutputForTimestamp(raoResultFilePath, inputStream, CORE_CC, "RAO_RESULT", coreCCRequestTimestamp);
            }
        } catch (final Exception e) {
            throw new CoreCCInternalException("RAO result could not be uploaded to minio", e);
        }
    }

    static Properties generateJsonProperties(final Unit unit) {
        final Properties properties = new Properties();
        final String propertiesPrefix = "rao-result.export.json.flows-in-";
        if (Unit.AMPERE == unit) {
            properties.setProperty(propertiesPrefix + "amperes", "true");
        } else {
            properties.setProperty(propertiesPrefix + "megawatts", "true");
        }
        return properties;
    }

    public void exportMetadataToMinio(final CoreCCPostProcessingData postProcessingData) {
        final InternalCoreCCRequest coreCCRequest = postProcessingData.getRequest();
        final String raoRequestInstant = postProcessingData.getRaoRequestInstant();
        LOGGER.info("Core CC task: '{}', exporting Metadata result for timestamp: '{}'", coreCCRequest.getId(), raoRequestInstant);

        buildAndExportMetadata(postProcessingData.getResultsDestination(), raoRequestInstant, raoRequestInstant, coreCCRequest);
    }

    public void exportMetadataToMinioWhenPreProcessingFailed(final InternalCoreCCRequest coreCCRequest) {
        final HourlyRaoRequest hourlyRaoRequest = coreCCRequest.getContinentalHourlyRaoRequest();
        final String raoRequestInstant = getRaoRequestInstantWhenPreProcessingFailed(coreCCRequest);
        final String coreCCRequestInstant = coreCCRequest.getTimestamp().toInstant().toString();
        LOGGER.info("Core CC task: '{}', creating Metadata result when preProcessing failed (for coreCCRequest with timestamp: '{}')", coreCCRequest.getId(), coreCCRequest.getTimestamp());

        buildAndExportMetadata(hourlyRaoRequest.getResultsDestination(), coreCCRequestInstant, raoRequestInstant, coreCCRequest);
    }

    private void buildAndExportMetadata(final String postProcessingData,
                                        final String filenameInstant,
                                        final String raoRequestInstant,
                                        final InternalCoreCCRequest coreCCRequest) {
        final String metadataFilename = NamingRules.generateMetadataFileName(filenameInstant, coreCCRequest.getVersion());

        try (final ByteArrayOutputStream outputStreamMetadata = new ByteArrayOutputStream()) {
            final String metaDataFilePath = buildFilePath(postProcessingData, metadataFilename);
            final CoreCCMetadata.Builder metadataBuilder = new CoreCCMetadata.Builder()
                .withRaoRequestInstant(raoRequestInstant);

            fillMetadataBuilderWithCommonData(metadataBuilder, coreCCRequest);
            fillMetadataBuilderWithHourlyRaoResults(coreCCRequest, metadataBuilder);

            new ObjectMapper().writeValue(outputStreamMetadata, metadataBuilder.build());
            minioAdapter.uploadOutputForTimestamp(metaDataFilePath, new ByteArrayInputStream(outputStreamMetadata.toByteArray()), CORE_CC, "METADATA", coreCCRequest.getTimestamp());
        } catch (final Exception e) {
            throw new CoreCCInternalException("Metadata could not be uploaded to minio", e);
        }
    }

    private static String getRaoRequestInstantWhenPreProcessingFailed(final InternalCoreCCRequest coreCCRequest) {
        String raoRequestInstant = null;
        if (coreCCRequest.getContinentalHourlyRaoResult() != null) {
            raoRequestInstant = coreCCRequest.getContinentalHourlyRaoResult().getRaoRequestInstant();
        }
        if (raoRequestInstant == null && coreCCRequest.getSemHourlyRaoResult() != null) {
            raoRequestInstant = coreCCRequest.getSemHourlyRaoResult().getRaoRequestInstant();
        }
        return raoRequestInstant;
    }

    private static void fillMetadataBuilderWithCommonData(final CoreCCMetadata.Builder metadataBuilder,
                                                          final InternalCoreCCRequest coreCCRequest) {
        metadataBuilder
            .withRaoRequestFileName(coreCCRequest.getRaoRequest().getFilename())
            .withRequestReceivedInstant(coreCCRequest.getRequestReceivedInstant().toString())
            .withTimeInterval(coreCCRequest.getTimeInterval())
            .withCorrelationId(coreCCRequest.getCorrelationId())
            .withVersion(coreCCRequest.getVersion());
    }

    private static void fillMetadataBuilderWithHourlyRaoResults(final InternalCoreCCRequest coreCCRequest,
                                                                final CoreCCMetadata.Builder metadataBuilder) {
        final HourlyRaoResult continentalHourlyRaoResult = coreCCRequest.getContinentalHourlyRaoResult();
        if (continentalHourlyRaoResult != null) {
            metadataBuilder.withContinentalComputationStartInstant(
                    Objects.toString(continentalHourlyRaoResult.getComputationStartInstant(), null)
                )
                .withContinentalComputationEndInstant(
                    Objects.toString(continentalHourlyRaoResult.getComputationEndInstant(), null)
                )
                .withContinentalComputationStatus(continentalHourlyRaoResult.getStatus().toString())
                .withContinentalComputationErrorCode(continentalHourlyRaoResult.getErrorCodeString())
                .withContinentalComputationErrorMessage(continentalHourlyRaoResult.getErrorMessage());
        }

        final HourlyRaoResult semHourlyRaoResult = coreCCRequest.getSemHourlyRaoResult();
        if (coreCCRequest.isSemActivated() && semHourlyRaoResult != null) {
            metadataBuilder.withSemComputationStartInstant(
                    Objects.toString(semHourlyRaoResult.getComputationStartInstant(), null)
                )
                .withSemComputationEndInstant(
                    Objects.toString(semHourlyRaoResult.getComputationEndInstant(), null)
                )
                .withSemComputationStatus(semHourlyRaoResult.getStatus().toString())
                .withSemComputationErrorCode(semHourlyRaoResult.getErrorCodeString())
                .withSemComputationErrorMessage(semHourlyRaoResult.getErrorMessage());
        }
    }
}
