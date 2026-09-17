/*
 * Copyright (c) 2023, RTE (http://www.rte-france.com)
 *  This Source Code Form is subject to the terms of the Mozilla Public
 *  License, v. 2.0. If a copy of the MPL was not distributed with this
 *  file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package com.farao_community.farao.gridcapa_core_cc.app.services;

import com.farao_community.farao.gridcapa_core_cc.api.exception.CoreCCInternalException;
import com.farao_community.farao.gridcapa_core_cc.api.exception.CoreCCInvalidDataException;
import com.farao_community.farao.gridcapa_core_cc.api.resource.CoreCCFileResource;
import com.farao_community.farao.gridcapa_core_cc.app.entities.CgmsAndXmlHeader;
import com.farao_community.farao.gridcapa_core_cc.app.inputs.rao_request.RequestMessage;
import com.farao_community.farao.gridcapa_core_cc.app.inputs.rao_response.ResponseMessage;
import com.farao_community.farao.gridcapa_core_cc.app.util.JaxbUtil;
import com.farao_community.farao.gridcapa_core_cc.app.util.NamingRules;
import com.farao_community.farao.gridcapa_core_cc.app.util.ZipUtil;
import com.farao_community.farao.minio_adapter.starter.MinioAdapter;
import com.powsybl.commons.report.ReportNode;
import com.powsybl.glsk.api.GlskDocument;
import com.powsybl.glsk.api.io.GlskDocumentImporters;
import com.powsybl.glsk.commons.ZonalData;
import com.powsybl.iidm.network.Network;
import com.powsybl.openrao.data.crac.api.Crac;
import com.powsybl.openrao.data.crac.api.parameters.CracCreationParameters;
import com.powsybl.openrao.data.crac.api.parameters.JsonCracCreationParameters;
import com.powsybl.openrao.data.crac.io.fbconstraint.FbConstraintCreationContext;
import com.powsybl.openrao.data.crac.io.fbconstraint.FbConstraintImporter;
import com.powsybl.openrao.data.crac.io.fbconstraint.parameters.FbConstraintCracCreationParameters;
import com.powsybl.openrao.data.raoresult.api.RaoResult;
import com.powsybl.openrao.data.refprog.referenceprogram.ReferenceProgram;
import com.powsybl.openrao.data.refprog.refprogxmlimporter.RefProgImporter;
import com.powsybl.openrao.raoapi.json.JsonRaoParameters;
import com.powsybl.openrao.raoapi.parameters.RaoParameters;
import com.powsybl.openrao.virtualhubs.VirtualHubsConfiguration;
import com.powsybl.openrao.virtualhubs.xml.XmlVirtualHubsConfiguration;
import com.powsybl.sensitivity.SensitivityVariableSet;
import org.apache.commons.io.FilenameUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.FileAttribute;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Set;

/**
 * @author Amira Kahya {@literal <amira.kahya at rte-france.com>}
 * @author Ameni Walha {@literal <ameni.walha at rte-france.com>}
 */
@Service
public class FileImporter {
    public static final String CRAC_CREATION_PARAMETERS_JSON = "/crac/cracCreationParameters.json";
    private static final Logger LOGGER = LoggerFactory.getLogger(FileImporter.class);

    private final MinioAdapter minioAdapter;
    private final UrlValidationService urlValidationService;

    public FileImporter(final MinioAdapter minioAdapter, final UrlValidationService urlValidationService) {
        this.minioAdapter = minioAdapter;
        this.urlValidationService = urlValidationService;
    }

    public Network importNetwork(final String networkUrl) {
        try (final InputStream networkInputStream = minioAdapter.getFile(networkUrl)) {
            return Network.read(getFilenameFromUrl(networkUrl), networkInputStream);
        } catch (final IOException e) {
            throw new CoreCCInvalidDataException(String.format("Cannot download network file from URL '%s'", networkUrl), e);
        }
    }

    public ReferenceProgram importReferenceProgram(final CoreCCFileResource refProgFile, final OffsetDateTime timestamp) {
        try (final InputStream refProgStream = urlValidationService.openUrlStream(refProgFile.getUrl())) {
            return RefProgImporter.importRefProg(refProgStream, timestamp);
        } catch (final IOException e) {
            throw new CoreCCInvalidDataException(String.format("Cannot download reference program file from URL '%s'", refProgFile.getUrl()), e);
        }
    }

    public ZonalData<SensitivityVariableSet> importGlsk(final String glskUrl, final OffsetDateTime timestamp, final Network network) {
        try (final InputStream glskFileInputStream = urlValidationService.openUrlStream(glskUrl)) {
            final GlskDocument ucteGlskProvider = GlskDocumentImporters.importGlsk(glskFileInputStream);
            return ucteGlskProvider.getZonalGlsks(network, timestamp.toInstant());
        } catch (Exception e) {
            throw new CoreCCInvalidDataException(String.format("Cannot download GLSK file from URL '%s'", glskUrl), e);
        }
    }

    public RaoParameters importRaoParameters(final String raoParametersUrl) {
        try (final InputStream raoParametersInputStream = minioAdapter.getFile(raoParametersUrl)) {
            return JsonRaoParameters.read(raoParametersInputStream, ReportNode.NO_OP);
        } catch (final Exception e) {
            throw new CoreCCInternalException(String.format("Cannot download RaoParameters file from URL '%s'", raoParametersUrl), e);
        }
    }

    public RaoResult importRaoResult(final String raoResultUrl, final Crac crac) {
        try (final InputStream raoResultStream = urlValidationService.openUrlStream(raoResultUrl)) {
            return RaoResult.read(raoResultStream, crac);
        } catch (final IOException e) {
            throw new CoreCCInvalidDataException(String.format("Cannot download RaoResult file from URL '%s'", raoResultUrl), e);
        }
    }

    public FbConstraintCreationContext importCbcora(final String cbcoraUrl,
                                                    final OffsetDateTime targetProcessDateTime,
                                                    final Network network) {
        final CracCreationParameters cracCreationParameters = getCimCracCreationParameters();
        cracCreationParameters.addExtension(FbConstraintCracCreationParameters.class, new FbConstraintCracCreationParameters());
        cracCreationParameters.getExtension(FbConstraintCracCreationParameters.class).setTimestamp(targetProcessDateTime);
        try (final InputStream cracInputStream = urlValidationService.openUrlStream(cbcoraUrl)) {
            return (FbConstraintCreationContext) new FbConstraintImporter().importData(cracInputStream, cracCreationParameters, network);
        } catch (final Exception e) {
            throw new CoreCCInvalidDataException(String.format("Cannot download cbcora file from URL '%s'", cbcoraUrl), e);
        }
    }

    CracCreationParameters getCimCracCreationParameters() {
        LOGGER.info("Importing Crac Creation Parameters file: {}", CRAC_CREATION_PARAMETERS_JSON);
        return JsonCracCreationParameters.read(getClass().getResourceAsStream(CRAC_CREATION_PARAMETERS_JSON));
    }

    public Crac importCrac(final String cracFileUrl, final Network network) {
        try (final InputStream cracFileInputStream = minioAdapter.getFile(cracFileUrl)) {
            return Crac.read(getFilenameFromUrl(cracFileUrl), cracFileInputStream, network);
        } catch (final Exception e) {
            throw new CoreCCInternalException(String.format("Exception occurred while importing CRAC file: %s", cracFileUrl), e);
        }
    }

    public RequestMessage importRaoRequest(final String raoRequestFileUrl) {
        try (final InputStream raoRequestInputStream = urlValidationService.openUrlStream(raoRequestFileUrl)) {
            return JaxbUtil.unmarshalContent(RequestMessage.class, raoRequestInputStream);
        } catch (final Exception e) {
            throw new CoreCCInvalidDataException(String.format("Cannot download rao request file from URL '%s'", raoRequestFileUrl), e);
        }
    }

    public CgmsAndXmlHeader importCgmsZip(final CoreCCFileResource cgmsZimFileResource) {
        try (final InputStream cgmsZipInputStream = urlValidationService.openUrlStream(cgmsZimFileResource.getUrl())) {
            LOGGER.info("Import of cgms zip from {} file ", cgmsZimFileResource.getFilename());

            // Setting permissions
            final FileAttribute<Set<PosixFilePermission>> attr = PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rwx------"));
            final String tmpInputsPath = Files.createTempDirectory("gridcapa-core-cc-temp-dir", attr).toString();
            final Path tmpCgmInputsPath = Files.createDirectories(Paths.get(tmpInputsPath + File.separator + "cgm"), attr);
            final List<Path> unzippedPaths = ZipUtil.unzipInputStream(cgmsZipInputStream, tmpCgmInputsPath);
            final Path xmlHeaderPath = unzippedPaths.stream().filter(p -> p.toFile().getName().matches(NamingRules.CGM_XML_HEADER_NAME))
                    .findFirst().orElseThrow(() -> new CoreCCInvalidDataException("CGM zip does not contain XML header"));
            final ResponseMessage xmlHeader = JaxbUtil.unmarshalFile(ResponseMessage.class, xmlHeaderPath);
            final List<Path> networkPaths = unzippedPaths.stream().filter(p -> p.toFile().getName().matches(NamingRules.CGM_FILE_NAME)).toList();
            return new CgmsAndXmlHeader(xmlHeader, networkPaths);
        } catch (final Exception e) {
            throw new CoreCCInvalidDataException(String.format("Cannot download CGM file from URL '%s'", cgmsZimFileResource.getUrl()), e);
        }
    }

    public VirtualHubsConfiguration importVirtualHubs(final CoreCCFileResource virtualHubsFileResource) {
        try (final InputStream virtualHubsInputStream = urlValidationService.openUrlStream(virtualHubsFileResource.getUrl())) {
            LOGGER.info("Import of virtual hubs from {} file ", virtualHubsFileResource.getFilename());
            return XmlVirtualHubsConfiguration.importConfiguration(virtualHubsInputStream);
        } catch (final Exception e) {
            throw new CoreCCInvalidDataException(String.format("Cannot download VirtualHubs file from URL '%s'", virtualHubsFileResource.getUrl()), e);
        }
    }

    private static String getFilenameFromUrl(String url) {
        try {
            return FilenameUtils.getName(new URI(url).toURL().getPath());
        } catch (final MalformedURLException | URISyntaxException | IllegalArgumentException e) {
            return FilenameUtils.getName(url);
        }
    }
}
