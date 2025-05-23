/*
 * Copyright (c) 2023, RTE (http://www.rte-france.com)
 *  This Source Code Form is subject to the terms of the Mozilla Public
 *  License, v. 2.0. If a copy of the MPL was not distributed with this
 *  file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package com.farao_community.farao.gridcapa_core_cc.app.services;

import com.farao_community.farao.gridcapa_core_cc.api.exception.CoreCCInvalidDataException;
import com.farao_community.farao.gridcapa_core_cc.api.resource.CoreCCFileResource;
import com.farao_community.farao.gridcapa_core_cc.app.entities.CgmsAndXmlHeader;
import com.farao_community.farao.gridcapa_core_cc.app.inputs.rao_request.RequestMessage;
import com.farao_community.farao.gridcapa_core_cc.app.inputs.rao_response.ResponseMessage;
import com.farao_community.farao.gridcapa_core_cc.app.util.JaxbUtil;
import com.farao_community.farao.gridcapa_core_cc.app.util.NamingRules;
import com.farao_community.farao.gridcapa_core_cc.app.util.ZipUtil;
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

/**
 * @author Amira Kahya {@literal <amira.kahya at rte-france.com>}
 * @author Ameni Walha {@literal <ameni.walha at rte-france.com>}
 */
@Service
public class FileImporter {

    public static final String CRAC_CREATION_PARAMETERS_JSON = "/crac/cracCreationParameters.json";
    public static final String CANNOT_DOWNLOAD_RAO_REQUEST_FILE_FROM_URL = "Cannot download rao request file from URL '%s'";
    private final UrlValidationService urlValidationService;
    private static final Logger LOGGER = LoggerFactory.getLogger(FileImporter.class);

    public FileImporter(UrlValidationService urlValidationService) {
        this.urlValidationService = urlValidationService;
    }

    public Network importNetwork(CoreCCFileResource cgmFile) {
        try (InputStream networkStream = urlValidationService.openUrlStream(cgmFile.getUrl())) {
            return NetworkHandler.loadNetwork(cgmFile.getFilename(), networkStream);
        } catch (IOException e) {
            throw new CoreCCInvalidDataException(String.format("Cannot download network file from URL '%s'", cgmFile.getUrl()), e);
        }
    }

    public Network importNetworkFromUrl(String cgmUrl) {
        return Network.read(getFilenameFromUrl(cgmUrl), urlValidationService.openUrlStream(cgmUrl));
    }

    public GlskDocument importGlskFile(CoreCCFileResource glskFileResource) {
        try (InputStream glskStream = urlValidationService.openUrlStream(glskFileResource.getUrl())) {
            return GlskDocumentImporters.importGlsk(glskStream);
        } catch (IOException e) {
            throw new CoreCCInvalidDataException(String.format("Cannot download GLSK file from URL '%s'", glskFileResource.getUrl()), e);
        }
    }

    public ReferenceProgram importReferenceProgram(CoreCCFileResource refProgFile, OffsetDateTime timestamp) {
        try (InputStream refProgStream = urlValidationService.openUrlStream(refProgFile.getUrl())) {
            return RefProgImporter.importRefProg(refProgStream, timestamp);
        } catch (IOException e) {
            throw new CoreCCInvalidDataException(String.format("Cannot download reference program file from URL '%s'", refProgFile.getUrl()), e);
        }
    }

    public RaoResult importRaoResult(String raoResultUrl, Crac crac) {
        try (InputStream raoResultStream = urlValidationService.openUrlStream(raoResultUrl)) {
            return RaoResult.read(raoResultStream, crac);
        } catch (IOException e) {
            throw new CoreCCInvalidDataException(String.format("Cannot download RaoResult file from URL '%s'", raoResultUrl), e);
        }
    }

    public FbConstraintCreationContext importCrac(String cbcoraUrl, OffsetDateTime targetProcessDateTime, Network network) {
        CracCreationParameters cracCreationParameters = getCimCracCreationParameters();
        cracCreationParameters.addExtension(FbConstraintCracCreationParameters.class, new FbConstraintCracCreationParameters());
        cracCreationParameters.getExtension(FbConstraintCracCreationParameters.class).setTimestamp(targetProcessDateTime);
        try (InputStream cracInputStream = urlValidationService.openUrlStream(cbcoraUrl)) {
            return (FbConstraintCreationContext) new FbConstraintImporter().importData(cracInputStream, cracCreationParameters, network);
        } catch (Exception e) {
            throw new CoreCCInvalidDataException(String.format("Cannot download cbcora file from URL '%s'", cbcoraUrl), e);
        }
    }

    public InputStream importFileUrlAsInputStream(String fileUrl) {
        return urlValidationService.openUrlStream(fileUrl);
    }

    public RequestMessage importRaoRequest(CoreCCFileResource raoRequestFileResource) {
        try (InputStream raoRequestInputStream = urlValidationService.openUrlStream(raoRequestFileResource.getUrl())) {
            return JaxbUtil.unmarshalContent(RequestMessage.class, raoRequestInputStream);
        } catch (Exception e) {
            throw new CoreCCInvalidDataException(String.format(CANNOT_DOWNLOAD_RAO_REQUEST_FILE_FROM_URL, raoRequestFileResource.getUrl()), e);
        }
    }

    public CgmsAndXmlHeader importCgmsZip(CoreCCFileResource cgmsZimFileResource) {
        try (InputStream cgmsZipInputStream = urlValidationService.openUrlStream(cgmsZimFileResource.getUrl())) {
            LOGGER.info("Import of cgms zip from {} file ", cgmsZimFileResource.getFilename());

            // Setting permissions
            FileAttribute<Set<PosixFilePermission>> attr = PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rwx------"));
            String tmpInputsPath = Files.createTempDirectory("gridcapa-core-cc-temp-dir", attr).toString();
            Path tmpCgmInputsPath = Files.createDirectories(Paths.get(tmpInputsPath + File.separator + "cgm"), attr);
            List<Path> unzippedPaths = ZipUtil.unzipInputStream(cgmsZipInputStream, tmpCgmInputsPath);
            Path xmlHeaderPath = unzippedPaths.stream().filter(p -> p.toFile().getName().matches(NamingRules.CGM_XML_HEADER_NAME))
                    .findFirst().orElseThrow(() -> new CoreCCInvalidDataException("CGM zip does not contain XML header"));
            ResponseMessage xmlHeader = JaxbUtil.unmarshalFile(ResponseMessage.class, xmlHeaderPath);
            List<Path> networkPaths = unzippedPaths.stream().filter(p -> p.toFile().getName().matches(NamingRules.CGM_FILE_NAME)).toList();
            return new CgmsAndXmlHeader(xmlHeader, networkPaths);
        } catch (Exception e) {
            throw new CoreCCInvalidDataException(String.format(CANNOT_DOWNLOAD_RAO_REQUEST_FILE_FROM_URL, cgmsZimFileResource.getUrl()), e);
        }
    }

    CracCreationParameters getCimCracCreationParameters() {
        LOGGER.info("Importing Crac Creation Parameters file: {}", CRAC_CREATION_PARAMETERS_JSON);
        return JsonCracCreationParameters.read(getClass().getResourceAsStream(CRAC_CREATION_PARAMETERS_JSON));
    }

    public VirtualHubsConfiguration importVirtualHubs(CoreCCFileResource virtualHubsFileResource) {
        try (InputStream virtualHubsInputStream = urlValidationService.openUrlStream(virtualHubsFileResource.getUrl())) {
            LOGGER.info("Import of virtual hubs from {} file ", virtualHubsFileResource.getFilename());
            return XmlVirtualHubsConfiguration.importConfiguration(virtualHubsInputStream);
        } catch (Exception e) {
            throw new CoreCCInvalidDataException(String.format(CANNOT_DOWNLOAD_RAO_REQUEST_FILE_FROM_URL, virtualHubsFileResource.getUrl()), e);
        }
    }

    String getFilenameFromUrl(String url) {
        try {
            return FilenameUtils.getName(new URI(url).toURL().getPath());
        } catch (MalformedURLException | URISyntaxException | IllegalArgumentException e) {
            throw new CoreCCInvalidDataException(String.format("URL is invalid: %s", url), e);
        }
    }
}
