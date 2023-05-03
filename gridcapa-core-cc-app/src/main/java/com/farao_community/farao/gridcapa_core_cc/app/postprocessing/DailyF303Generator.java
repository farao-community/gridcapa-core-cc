/*
 * Copyright (c) 2021, RTE (http://www.rte-france.com)
 */
package com.farao_community.farao.gridcapa_core_cc.app.postprocessing;

import com.farao_community.farao.data.crac_creation.creator.fb_constraint.FbConstraint;
import com.farao_community.farao.data.crac_creation.creator.fb_constraint.importer.FbConstraintImporter;
import com.farao_community.farao.data.crac_creation.creator.fb_constraint.xsd.FlowBasedConstraintDocument;
import com.farao_community.farao.gridcapa_core_cc.api.exception.CoreCCInternalException;
import com.farao_community.farao.gridcapa_core_cc.api.resource.CoreCCRequest;
import com.farao_community.farao.gridcapa_core_cc.app.util.IntervalUtil;
import com.farao_community.farao.minio_adapter.starter.MinioAdapter;
import org.springframework.stereotype.Service;
import org.threeten.extra.Interval;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * @author Pengbo Wang {@literal <pengbo.wang at rte-international.com>}
 * @author Mohamed BenRejeb {@literal <mohamed.ben-rejeb at rte-france.com>}
 * @author Baptiste Seguinot {@literal <baptiste.seguinot at rte-france.com}
 */
@Service
public class DailyF303Generator {

    private final MinioAdapter minioAdapter;

    public DailyF303Generator(MinioAdapter minioAdapter) {
        this.minioAdapter = minioAdapter;
    }

    public FlowBasedConstraintDocument generate(CoreCCRequest coreCCRequest) {

        try (InputStream cracXmlInputStream = minioAdapter.getFile(coreCCRequest.getCbcora().getUrl())) {

            // get native CRAC
            FbConstraint nativeCrac = new FbConstraintImporter().importNativeCrac(cracXmlInputStream);

            // generate F303Info for each 24 hours of the initial CRAC
            Map<Integer, Interval> positionMap = IntervalUtil.getPositionsMap(nativeCrac.getDocument().getConstraintTimeInterval().getV());
            List<HourlyF303Info> hourlyF303Infos = new ArrayList<>();
            positionMap.values().forEach(interval -> hourlyF303Infos.add(new HourlyF303InfoGenerator(nativeCrac, interval, coreCCRequest, minioAdapter).generate()));

            // gather hourly info in one common document, cluster the elements that can be clusterized
            FlowBasedConstraintDocument flowBasedConstraintDocument = new DailyF303Clusterizer(hourlyF303Infos, nativeCrac).generateClusterizedDocument();

            // save this to fill in rao response
            coreCCRequest.getDailyOutputs().setOutputFlowBasedConstraintDocumentMessageId(flowBasedConstraintDocument.getDocumentIdentification().getV());
            return flowBasedConstraintDocument;
        } catch (Exception e) {
            throw new CoreCCInternalException("Exception occurred during F303 file creation", e);
        }
    }
}
