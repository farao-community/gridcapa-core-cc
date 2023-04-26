/*
 * Copyright (c) 2021, RTE (http://www.rte-france.com)
 *  This Source Code Form is subject to the terms of the Mozilla Public
 *  License, v. 2.0. If a copy of the MPL was not distributed with this
 *  file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package com.farao_community.farao.gridcapa_core_cc.app.services;

import com.farao_community.farao.gridcapa_core_cc.app.study_point.StudyPointsImporter;
import com.farao_community.farao.gridcapa_core_cc.api.exception.CoreCCInvalidDataException;
import com.farao_community.farao.gridcapa_core_cc.api.resource.CoreCCFileResource;
import com.farao_community.farao.data.crac_creation.creator.api.parameters.CracCreationParameters;
import com.farao_community.farao.data.crac_creation.creator.fb_constraint.FbConstraint;
import com.farao_community.farao.data.crac_creation.creator.fb_constraint.crac_creator.FbConstraintCracCreator;
import com.farao_community.farao.data.crac_creation.creator.fb_constraint.crac_creator.FbConstraintCreationContext;
import com.farao_community.farao.data.crac_creation.creator.fb_constraint.importer.FbConstraintImporter;
import com.farao_community.farao.data.refprog.reference_program.ReferenceProgram;
import com.farao_community.farao.data.refprog.refprog_xml_importer.RefProgImporter;
import com.farao_community.farao.gridcapa_core_cc.app.study_point.StudyPoint;
import com.farao_community.farao.gridcapa_core_cc.app.util.ZipUtil;
import com.farao_community.farao.minio_adapter.starter.MinioAdapter;
import com.powsybl.glsk.api.GlskDocument;
import com.powsybl.glsk.api.io.GlskDocumentImporters;
import com.powsybl.iidm.network.Network;
import org.apache.commons.io.FilenameUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * @author Ameni Walha {@literal <ameni.walha at rte-france.com>}
 */
@Service
public class FileImporter {
    private final UrlValidationService urlValidationService;
    private static final Logger LOGGER = LoggerFactory.getLogger(FileImporter.class);

    public FileImporter(UrlValidationService urlValidationService) {
        this.urlValidationService = urlValidationService;
    }

    public Network importNetwork(CoreCCFileResource cgmFile) {
        InputStream networkStream = urlValidationService.openUrlStream(cgmFile.getUrl());
        return NetworkHandler.loadNetwork(cgmFile.getFilename(), networkStream);
    }

    public Network importNetworkFromUrl(String cgmUrl) {
        return Network.read(getFilenameFromUrl(cgmUrl), urlValidationService.openUrlStream(cgmUrl));
    }

    public GlskDocument importGlskFile(CoreCCFileResource glskFileResource) {
        try (InputStream glskStream = urlValidationService.openUrlStream(glskFileResource.getUrl())) {
            LOGGER.info("Import of Glsk file {} ", glskFileResource.getFilename());
            return GlskDocumentImporters.importGlsk(glskStream);
        } catch (IOException e) {
            throw new CoreCCInvalidDataException(String.format("Cannot download reference program file from URL '%s'", glskFileResource.getUrl()), e);
        }
    }

    public ReferenceProgram importReferenceProgram(CoreCCFileResource refProgFile, OffsetDateTime timestamp) {
        try (InputStream refProgStream = urlValidationService.openUrlStream(refProgFile.getUrl())) {
            return RefProgImporter.importRefProg(refProgStream, timestamp);
        } catch (IOException e) {
            throw new CoreCCInvalidDataException(String.format("Cannot download GLSK file from URL '%s'", refProgFile.getUrl()), e);
        }
    }

    public List<StudyPoint> importStudyPoints(CoreCCFileResource studyPointsFileResource, OffsetDateTime timestamp) {
        try (InputStream studyPointsStream = urlValidationService.openUrlStream(studyPointsFileResource.getUrl())) {
            LOGGER.info("Import of study points from {} file for timestamp {} ", studyPointsFileResource.getFilename(), timestamp);
            return StudyPointsImporter.importStudyPoints(studyPointsStream, timestamp);
        } catch (Exception e) {
            throw new CoreCCInvalidDataException(String.format("Cannot download study points file from URL '%s'", studyPointsFileResource.getUrl()), e);
        }
    }

    public FbConstraintCreationContext importCrac(String cbcoraUrl, OffsetDateTime targetProcessDateTime, Network network) {
        CracCreationParameters cracCreationParameters = new CracCreationParameters();
        cracCreationParameters.setDefaultMonitoredLineSide(CracCreationParameters.MonitoredLineSide.MONITOR_LINES_ON_LEFT_SIDE);
        try (InputStream cracInputStream = urlValidationService.openUrlStream(cbcoraUrl)) {
            FbConstraint nativeCrac = new FbConstraintImporter().importNativeCrac(cracInputStream);
            return new FbConstraintCracCreator().createCrac(nativeCrac, network, targetProcessDateTime, cracCreationParameters);
        } catch (Exception e) {
            throw new CoreCCInvalidDataException(String.format("Cannot download cbcora file from URL '%s'", cbcoraUrl), e);
        }
    }

    String getFilenameFromUrl(String url) {
        try {
            return FilenameUtils.getName(new URL(url).getPath());
        } catch (MalformedURLException e) {
            throw new CoreCCInvalidDataException(String.format("URL is invalid: %s", url));
        }
    }
}
