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
import com.farao_community.farao.gridcapa_core_cc.api.resource.HourlyRaoRequest;
import com.farao_community.farao.gridcapa_core_cc.api.resource.InternalCoreCCRequest;
import com.farao_community.farao.gridcapa_core_cc.app.entities.CgmsAndXmlHeader;
import com.farao_community.farao.gridcapa_core_cc.app.inputs.rao_request.RequestMessage;
import com.farao_community.farao.gridcapa_core_cc.app.inputs.rao_response.ResponseMessage;
import com.farao_community.farao.gridcapa_core_cc.app.util.JaxbUtil;
import com.farao_community.farao.gridcapa_core_cc.app.util.ZipUtil;
import com.farao_community.farao.minio_adapter.starter.MinioAdapter;
import com.powsybl.glsk.api.GlskDocument;
import com.powsybl.glsk.api.io.GlskDocumentImporters;
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
import com.powsybl.openrao.virtualhubs.VirtualHubsConfiguration;
import com.powsybl.openrao.virtualhubs.xml.XmlVirtualHubsConfiguration;
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

import static com.farao_community.farao.gridcapa_core_cc.app.util.NamingRules.CGM_FILE_NAME;
import static com.farao_community.farao.gridcapa_core_cc.app.util.NamingRules.CGM_XML_HEADER_NAME;

/**
 * @author Amira Kahya {@literal <amira.kahya at rte-france.com>}
 * @author Ameni Walha {@literal <ameni.walha at rte-france.com>}
 */
@Service
public class FileImporter {

    public static final String CRAC_CREATION_PARAMETERS_JSON = "/crac/cracCreationParameters.json";
    public static final String CANNOT_DOWNLOAD_RAO_REQUEST_FILE_FROM_URL = "Cannot download rao request file from URL '%s'";
    private final UrlValidationService urlValidationService;
    private final MinioAdapter minioAdapter;
    private static final Logger LOGGER = LoggerFactory.getLogger(FileImporter.class);

    public FileImporter(final UrlValidationService urlValidationService,
                        final MinioAdapter minioAdapter) {
        this.urlValidationService = urlValidationService;
        this.minioAdapter = minioAdapter;
    }

    public Network importNetwork(final CoreCCFileResource cgmFile) {
        try (final InputStream networkStream = urlValidationService.openUrlStream(cgmFile.getUrl())) {
            return NetworkHandler.loadNetwork(cgmFile.getFilename(),
                                              networkStream);
        } catch (final IOException e) {
            throw new CoreCCInvalidDataException(String.format("Cannot download network file from URL '%s'", cgmFile.getUrl()), e);
        }
    }

    public Network importNetworkFromUrl(final String cgmUrl) {
        return Network.read(getFilenameFromUrl(cgmUrl),
                            urlValidationService.openUrlStream(cgmUrl));
    }

    public GlskDocument importGlskFile(final CoreCCFileResource glskFileResource) {
        try (final InputStream glskStream = urlValidationService.openUrlStream(glskFileResource.getUrl())) {
            return GlskDocumentImporters.importGlsk(glskStream);
        } catch (final IOException e) {
            throw new CoreCCInvalidDataException(String.format("Cannot download GLSK file from URL '%s'", glskFileResource.getUrl()), e);
        }
    }

    public ReferenceProgram importReferenceProgram(final CoreCCFileResource refProgFile,
                                                   final OffsetDateTime timestamp) {
        try (final InputStream refProgStream = urlValidationService.openUrlStream(refProgFile.getUrl())) {
            return RefProgImporter.importRefProg(refProgStream, timestamp);
        } catch (final IOException e) {
            throw new CoreCCInvalidDataException(String.format("Cannot download reference program file from URL '%s'", refProgFile.getUrl()), e);
        }
    }

    public RaoResult importRaoResult(final String raoResultUrl,
                                     final Crac crac) {
        try (final InputStream raoResultStream = urlValidationService.openUrlStream(raoResultUrl)) {
            return RaoResult.read(raoResultStream, crac);
        } catch (final IOException e) {
            throw new CoreCCInvalidDataException(String.format("Cannot download RaoResult file from URL '%s'", raoResultUrl), e);
        }
    }

    public FbConstraintCreationContext importCrac(final String cbcoraUrl,
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

    public InputStream importFileUrlAsInputStream(final String fileUrl) {
        return urlValidationService.openUrlStream(fileUrl);
    }

    public RequestMessage importRaoRequest(final CoreCCFileResource raoRequestFileResource) {
        try (final InputStream raoRequestInputStream = urlValidationService.openUrlStream(raoRequestFileResource.getUrl())) {
            return JaxbUtil.unmarshalContent(RequestMessage.class, raoRequestInputStream);
        } catch (final Exception e) {
            throw new CoreCCInvalidDataException(String.format(CANNOT_DOWNLOAD_RAO_REQUEST_FILE_FROM_URL, raoRequestFileResource.getUrl()), e);
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
            final Path xmlHeaderPath = unzippedPaths.stream()
                    .filter(p -> p.toFile().getName().matches(CGM_XML_HEADER_NAME))
                    .findFirst()
                    .orElseThrow(() -> new CoreCCInvalidDataException("CGM zip does not contain XML header"));
            final ResponseMessage xmlHeader = JaxbUtil.unmarshalFile(ResponseMessage.class, xmlHeaderPath);
            final List<Path> networkPaths = unzippedPaths.stream()
                    .filter(p -> p.toFile().getName().matches(CGM_FILE_NAME))
                    .toList();
            return new CgmsAndXmlHeader(xmlHeader, networkPaths);
        } catch (final Exception e) {
            throw new CoreCCInvalidDataException(String.format(CANNOT_DOWNLOAD_RAO_REQUEST_FILE_FROM_URL, cgmsZimFileResource.getUrl()), e);
        }
    }

    CracCreationParameters getCimCracCreationParameters() {
        LOGGER.info("Importing Crac Creation Parameters file: {}", CRAC_CREATION_PARAMETERS_JSON);
        return JsonCracCreationParameters.read(getClass().getResourceAsStream(CRAC_CREATION_PARAMETERS_JSON));
    }

    public VirtualHubsConfiguration importVirtualHubs(final CoreCCFileResource virtualHubsFileResource) {
        try (final InputStream virtualHubsInputStream = urlValidationService.openUrlStream(virtualHubsFileResource.getUrl())) {
            LOGGER.info("Import of virtual hubs from {} file ", virtualHubsFileResource.getFilename());
            return XmlVirtualHubsConfiguration.importConfiguration(virtualHubsInputStream);
        } catch (final Exception e) {
            throw new CoreCCInvalidDataException(String.format(CANNOT_DOWNLOAD_RAO_REQUEST_FILE_FROM_URL, virtualHubsFileResource.getUrl()), e);
        }
    }

    public Crac importCracFromHourlyRaoRequest(final InternalCoreCCRequest coreCCRequest,
                                               final Network network) {
        final HourlyRaoRequest hourlyRaoRequest = coreCCRequest.getHourlyRaoRequest();
        final String cracFileUrl = hourlyRaoRequest.getCracFileUrl();
        final Path path = Path.of(cracFileUrl);
        try (final InputStream cracFileInputStream = minioAdapter.getFile(cracFileUrl)) {
            return Crac.read(path.getFileName().toString(), cracFileInputStream, network);
        } catch (final Exception e) {
            throw new CoreCCInternalException(String.format("Exception occurred while importing CRAC file: %s", path.getFileName().toString()), e);
        }
    }

    private String getFilenameFromUrl(final String url) {
        try {
            return FilenameUtils.getName(new URI(url).toURL().getPath());
        } catch (final MalformedURLException | URISyntaxException | IllegalArgumentException e) {
            throw new CoreCCInvalidDataException(String.format("URL is invalid: %s", url), e);
        }
    }
}
