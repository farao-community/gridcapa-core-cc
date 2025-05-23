/*
 * Copyright (c) 2023, RTE (http://www.rte-france.com)
 *  This Source Code Form is subject to the terms of the Mozilla Public
 *  License, v. 2.0. If a copy of the MPL was not distributed with this
 *  file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.farao_community.farao.gridcapa_core_cc.app.preprocessing;

import com.farao_community.farao.gridcapa_core_cc.app.inputs.rao_request.Header;
import com.farao_community.farao.gridcapa_core_cc.app.inputs.rao_request.Property;
import com.farao_community.farao.gridcapa_core_cc.app.inputs.rao_request.RequestMessage;
import com.farao_community.farao.gridcapa_core_cc.app.util.JaxbUtil;
import com.powsybl.iidm.network.Country;
import com.powsybl.openrao.raoapi.ZoneToZonePtdfDefinition;
import com.powsybl.openrao.raoapi.parameters.LoopFlowParameters;
import com.powsybl.openrao.raoapi.parameters.RaoParameters;
import com.powsybl.openrao.raoapi.parameters.RelativeMarginsParameters;
import com.powsybl.openrao.raoapi.parameters.extensions.OpenRaoSearchTreeParameters;
import com.powsybl.openrao.raoapi.parameters.extensions.SearchTreeRaoLoopFlowParameters;
import com.powsybl.openrao.raoapi.parameters.extensions.SearchTreeRaoRelativeMarginsParameters;
import com.powsybl.openrao.virtualhubs.BorderDirection;
import com.powsybl.openrao.virtualhubs.MarketArea;
import com.powsybl.openrao.virtualhubs.VirtualHub;
import com.powsybl.openrao.virtualhubs.VirtualHubsConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * @author Mohamed BenRejeb {@literal <mohamed.ben-rejeb at rte-france.com>}
 * @author Thomas Bouquet {@literal <thomas.bouquet at rte-france.com>}
 * @author Vincent Bochet {@literal <vincent.bochet at rte-france.com>}
 */
@SpringBootTest
class RaoParametersServiceTest {

    private static final String FR = "FR";
    private static final String BE = "BE";
    private static final String ES = "ES";
    private static final String SK = "SK";
    private static final String EIC_FR = "10YFR-RTE------C";
    private static final String EIC_BE = "10YBE----------2";
    private static final String EIC_ES = "10YES-REE------0";
    private static final String EIC_SK = "10YSK-SEPS-----K";
    private static final String NODE_NAME_FR = "nodeNameFr";
    private static final String NODE_NAME_SK = "nodeNameSk";

    @Autowired
    private RaoParametersService raoParametersService;

    @Test
    void checkRaoParametersFilledCorrectly() {
        RequestMessage raoRequestMessage = JaxbUtil.unmarshalFile(RequestMessage.class, Paths.get(getClass().getResource("/RaoIntegrationRequest.xml").getPath()));
        assertEquals("2020-03-29T23:00:00Z/2020-03-30T00:00:00Z", raoRequestMessage.getPayload().getRequestItems().getTimeInterval());
        RaoParameters raoParameters = raoParametersService.createRaoParametersFromRequest(raoRequestMessage, buildVirtualHubsConfiguration());

        assertEquals(10.0, raoParameters.getRangeActionsOptimizationParameters().getPstRAMinImpactThreshold());

        List<String> expectedLoopFlowConstraintCountries = Arrays.asList("CZ", "SK", "FR", "PL", "RO", "DE", "SI", "NL", "HR", "HU", "AT");
        List<String> actualLoopFlowConstraintCountries = raoParameters.getLoopFlowParameters().get().getCountries().stream().map(Country::toString).toList();
        assertTrue(expectedLoopFlowConstraintCountries.size() == actualLoopFlowConstraintCountries.size()
                   && expectedLoopFlowConstraintCountries.containsAll(actualLoopFlowConstraintCountries)
                   && actualLoopFlowConstraintCountries.containsAll(expectedLoopFlowConstraintCountries));

        assertEquals(10.0, raoParameters.getTopoOptimizationParameters().getAbsoluteMinImpactThreshold());
    }

    private VirtualHubsConfiguration buildVirtualHubsConfiguration() {
        return new VirtualHubsConfiguration();
    }

    @Test
    void pstPenaltyCostTest() {
        RequestMessage requestMessage = new RequestMessage();
        Header header = new Header();
        requestMessage.setHeader(header);
        Property property = new Property();
        header.getProperty().add(property);
        property.setName("PST_RA_MIN_IMPACT");
        property.setValue("42.0");
        RaoParameters raoParameters = new RaoParameters();

        RaoParametersService.setPstPenaltyCost(requestMessage, raoParameters);

        assertNotNull(raoParameters.getRangeActionsOptimizationParameters());
        assertEquals(42.0, raoParameters.getRangeActionsOptimizationParameters().getPstRAMinImpactThreshold());
    }

    @Test
    void pstPenaltyCostNoValueTest() {
        RequestMessage requestMessage = new RequestMessage();
        Header header = new Header();
        requestMessage.setHeader(header);
        RaoParameters raoParameters = new RaoParameters();

        RaoParametersService.setPstPenaltyCost(requestMessage, raoParameters);

        assertNotNull(raoParameters.getRangeActionsOptimizationParameters());
        assertEquals(0.0, raoParameters.getRangeActionsOptimizationParameters().getPstRAMinImpactThreshold());
    }

    @Test
    void absoluteMinimumImpactThresholdTest() {
        RequestMessage requestMessage = new RequestMessage();
        Header header = new Header();
        requestMessage.setHeader(header);
        Property property = new Property();
        header.getProperty().add(property);
        property.setName("TOPO_RA_MIN_IMPACT");
        property.setValue("42.0");
        RaoParameters raoParameters = new RaoParameters();

        RaoParametersService.setAbsoluteMinimumImpactThreshold(requestMessage, raoParameters);

        assertNotNull(raoParameters.getTopoOptimizationParameters());
        assertEquals(42.0, raoParameters.getTopoOptimizationParameters().getAbsoluteMinImpactThreshold());
    }

    @Test
    void absoluteMinimumImpactThresholdNoValueTest() {
        RequestMessage requestMessage = new RequestMessage();
        Header header = new Header();
        requestMessage.setHeader(header);
        RaoParameters raoParameters = new RaoParameters();

        RaoParametersService.setAbsoluteMinimumImpactThreshold(requestMessage, raoParameters);

        assertNotNull(raoParameters.getTopoOptimizationParameters());
        assertEquals(0.0, raoParameters.getTopoOptimizationParameters().getAbsoluteMinImpactThreshold());
    }

    @Test
    void loopFlowCountriesWithExtensionTest() {
        RequestMessage requestMessage = new RequestMessage();
        Header header = new Header();
        requestMessage.setHeader(header);
        Property property = new Property();
        header.getProperty().add(property);
        property.setName("LF_CONSTRAINT_FR");
        property.setValue("true");
        RaoParameters raoParameters = new RaoParameters();

        final OpenRaoSearchTreeParameters openRaoSearchTreeParameters = new OpenRaoSearchTreeParameters();
        raoParameters.addExtension(OpenRaoSearchTreeParameters.class, openRaoSearchTreeParameters);
        final SearchTreeRaoLoopFlowParameters loopFlowParametersExtension = new SearchTreeRaoLoopFlowParameters();
        loopFlowParametersExtension.setConstraintAdjustmentCoefficient(35.0);
        openRaoSearchTreeParameters.setLoopFlowParameters(loopFlowParametersExtension);

        RaoParametersService.setLoopFlowCountries(requestMessage, raoParameters);

        final OpenRaoSearchTreeParameters retrievedExtension = raoParameters.getExtension(OpenRaoSearchTreeParameters.class);
        assertEquals(35.0, retrievedExtension.getLoopFlowParameters().get().getConstraintAdjustmentCoefficient());
        final LoopFlowParameters retrievedLoopFlowParameters = raoParameters.getLoopFlowParameters().get();
        assertNotNull(retrievedLoopFlowParameters.getCountries());
        assertTrue(retrievedLoopFlowParameters.getCountries().contains(Country.FR));
    }

    @Test
    void loopFlowCountriesWithoutExtensionTest() {
        RequestMessage requestMessage = new RequestMessage();
        Header header = new Header();
        requestMessage.setHeader(header);
        Property property = new Property();
        header.getProperty().add(property);
        property.setName("LF_CONSTRAINT_FR");
        property.setValue("true");
        RaoParameters raoParameters = new RaoParameters();
        final OpenRaoSearchTreeParameters openRaoSearchTreeParameters = new OpenRaoSearchTreeParameters();
        raoParameters.addExtension(OpenRaoSearchTreeParameters.class, openRaoSearchTreeParameters);
        final SearchTreeRaoLoopFlowParameters loopFlowParametersExtension = new SearchTreeRaoLoopFlowParameters();
        openRaoSearchTreeParameters.setLoopFlowParameters(loopFlowParametersExtension);

        RaoParametersService.setLoopFlowCountries(requestMessage, raoParameters);

        final OpenRaoSearchTreeParameters retrievedExtension = raoParameters.getExtension(OpenRaoSearchTreeParameters.class);
        assertEquals(0.0, retrievedExtension.getLoopFlowParameters().get().getConstraintAdjustmentCoefficient());
        final LoopFlowParameters retrievedLoopFlowParameters = raoParameters.getLoopFlowParameters().get();
        assertFalse(retrievedLoopFlowParameters.getCountries().isEmpty());
        assertTrue(retrievedLoopFlowParameters.getCountries().contains(Country.FR));
    }

    @Test
    void loopFlowCountriesGermanyTest() {
        RequestMessage requestMessage = new RequestMessage();
        Header header = new Header();
        requestMessage.setHeader(header);
        Property propertyD2 = new Property();
        Property propertyD4 = new Property();
        Property propertyD7 = new Property();
        Property propertyD8 = new Property();
        propertyD2.setName("LF_CONSTRAINT_D2");
        propertyD4.setName("LF_CONSTRAINT_D4");
        propertyD7.setName("LF_CONSTRAINT_D7");
        propertyD8.setName("LF_CONSTRAINT_D8");
        propertyD2.setValue("true");
        propertyD4.setValue("true");
        propertyD7.setValue("true");
        propertyD8.setValue("true");
        header.getProperty().add(propertyD2);
        header.getProperty().add(propertyD4);
        header.getProperty().add(propertyD7);
        header.getProperty().add(propertyD8);
        RaoParameters raoParameters = new RaoParameters();

        RaoParametersService.setLoopFlowCountries(requestMessage, raoParameters);

        final LoopFlowParameters retrievedLoopFlowParameters = raoParameters.getLoopFlowParameters().get();
        assertEquals(1, retrievedLoopFlowParameters.getCountries().size());
        assertTrue(retrievedLoopFlowParameters.getCountries().contains(Country.DE));
    }

    @Test
    void loopFlowCountriesWithBadCountryTest() {
        RequestMessage requestMessage = new RequestMessage();
        Header header = new Header();
        requestMessage.setHeader(header);
        Property property = new Property();
        property.setName("LF_CONSTRAINT_test");
        property.setValue("true");
        header.getProperty().add(property);
        RaoParameters raoParameters = new RaoParameters();

        assertThrows(
                IllegalArgumentException.class,
                () -> RaoParametersService.setLoopFlowCountries(requestMessage, raoParameters)
        );
    }

    @Test
    void loopFlowCountriesFalseTest() {
        RequestMessage requestMessage = new RequestMessage();
        Header header = new Header();
        requestMessage.setHeader(header);
        Property property = new Property();
        header.getProperty().add(property);
        property.setName("LF_CONSTRAINT_FR");
        property.setValue("false");
        RaoParameters raoParameters = new RaoParameters();

        RaoParametersService.setLoopFlowCountries(requestMessage, raoParameters);

        final LoopFlowParameters retrievedLoopFlowParameters = raoParameters.getLoopFlowParameters().get();
        assertTrue(retrievedLoopFlowParameters.getCountries().isEmpty());
    }

    @Test
    void ptdfBoundariesWithExtensionTest() {
        final VirtualHubsConfiguration virtualHubsConfiguration = new VirtualHubsConfiguration();
        final RaoParameters raoParameters = new RaoParameters();
        final OpenRaoSearchTreeParameters openRaoSearchTreeParameters = new OpenRaoSearchTreeParameters();
        raoParameters.addExtension(OpenRaoSearchTreeParameters.class, openRaoSearchTreeParameters);
        final SearchTreeRaoRelativeMarginsParameters relativeMarginsParametersExtension = new SearchTreeRaoRelativeMarginsParameters();
        relativeMarginsParametersExtension.setPtdfSumLowerBound(76.0);
        openRaoSearchTreeParameters.setRelativeMarginsParameters(relativeMarginsParametersExtension);

        RaoParametersService.setPtdfBoundaries(virtualHubsConfiguration, raoParameters);

        assertEquals(76.0, raoParameters.getExtension(OpenRaoSearchTreeParameters.class).getRelativeMarginsParameters().get().getPtdfSumLowerBound());
    }

    @Test
    void ptdfBoundariesWithoutExtensionTest() {
        VirtualHubsConfiguration virtualHubsConfiguration = new VirtualHubsConfiguration();
        RaoParameters raoParameters = new RaoParameters();
        final OpenRaoSearchTreeParameters openRaoSearchTreeParameters = new OpenRaoSearchTreeParameters();
        raoParameters.addExtension(OpenRaoSearchTreeParameters.class, openRaoSearchTreeParameters);
        final SearchTreeRaoRelativeMarginsParameters relativeMarginsParametersExtension = new SearchTreeRaoRelativeMarginsParameters();
        openRaoSearchTreeParameters.setRelativeMarginsParameters(relativeMarginsParametersExtension);

        RaoParametersService.setPtdfBoundaries(virtualHubsConfiguration, raoParameters);

        assertTrue(raoParameters.getExtension(OpenRaoSearchTreeParameters.class).getRelativeMarginsParameters().get().getPtdfSumLowerBound() < 1.0);
    }

    @Test
    void ptdfBoundariesFromBorderDirectionsTest() {
        VirtualHubsConfiguration virtualHubsConfiguration = new VirtualHubsConfiguration();
        virtualHubsConfiguration.addMarketArea(new MarketArea(FR, EIC_FR, true, false));
        virtualHubsConfiguration.addMarketArea(new MarketArea(BE, EIC_BE, true, false));
        virtualHubsConfiguration.addMarketArea(new MarketArea(ES, EIC_ES, false, false));
        virtualHubsConfiguration.addMarketArea(new MarketArea(SK, EIC_SK, true, false));
        virtualHubsConfiguration.addBorderDirection(new BorderDirection(FR, ES, false));
        virtualHubsConfiguration.addBorderDirection(new BorderDirection(FR, SK, false));
        virtualHubsConfiguration.addBorderDirection(new BorderDirection(BE, SK, false));
        virtualHubsConfiguration.addBorderDirection(new BorderDirection(SK, BE, false));
        RaoParameters raoParameters = new RaoParameters();

        RaoParametersService.setPtdfBoundaries(virtualHubsConfiguration, raoParameters);

        RelativeMarginsParameters relativeMarginsParameters = raoParameters.getRelativeMarginsParameters().get();
        assertNotNull(relativeMarginsParameters.getPtdfBoundaries());
        assertEquals(2, relativeMarginsParameters.getPtdfBoundaries().size());
        List<String> ptdfBoundariesStrings = relativeMarginsParameters.getPtdfBoundaries().stream().map(ZoneToZonePtdfDefinition::toString).toList();
        assertTrue(ptdfBoundariesStrings.contains("{10YFR-RTE------C}-{10YSK-SEPS-----K}"));
        assertTrue(ptdfBoundariesStrings.contains("{10YBE----------2}-{10YSK-SEPS-----K}"));
    }

    @Test
    void ptdfBoundariesFromVirtualHubsTest() {
        VirtualHubsConfiguration virtualHubsConfiguration = new VirtualHubsConfiguration();
        MarketArea marketAreaFr = new MarketArea(FR, EIC_FR, true, false);
        MarketArea marketAreaBe = new MarketArea(BE, EIC_BE, true, false);
        MarketArea marketAreaEs = new MarketArea(ES, EIC_ES, false, false);
        MarketArea marketAreaSk = new MarketArea(SK, EIC_SK, true, false);
        virtualHubsConfiguration.addMarketArea(marketAreaFr);
        virtualHubsConfiguration.addMarketArea(marketAreaBe);
        virtualHubsConfiguration.addMarketArea(marketAreaEs);
        virtualHubsConfiguration.addMarketArea(marketAreaSk);
        virtualHubsConfiguration.addVirtualHub(new VirtualHub("FR1", "FR1-XXXXXXXXXXXX", true, false, NODE_NAME_FR, marketAreaFr, "BE1"));
        virtualHubsConfiguration.addVirtualHub(new VirtualHub("BE1", "BE1-XXXXXXXXXXXX", true, false, NODE_NAME_SK, marketAreaBe, "FR1"));
        virtualHubsConfiguration.addVirtualHub(new VirtualHub("BE2", "BE2-XXXXXXXXXXXX", true, false, NODE_NAME_SK, marketAreaBe, null));
        virtualHubsConfiguration.addVirtualHub(new VirtualHub("ES1", "ES1-XXXXXXXXXXXX", true, false, NODE_NAME_SK, marketAreaEs, null));
        virtualHubsConfiguration.addVirtualHub(new VirtualHub("SK1", "SK1-XXXXXXXXXXXX", false, false, NODE_NAME_SK, marketAreaSk, null));
        RaoParameters raoParameters = new RaoParameters();

        RaoParametersService.setPtdfBoundaries(virtualHubsConfiguration, raoParameters);

        RelativeMarginsParameters relativeMarginsParameters = raoParameters.getRelativeMarginsParameters().get();
        assertNotNull(relativeMarginsParameters.getPtdfBoundaries());
        assertEquals(3, relativeMarginsParameters.getPtdfBoundaries().size());
        List<String> ptdfBoundariesStrings = relativeMarginsParameters.getPtdfBoundaries().stream().map(ZoneToZonePtdfDefinition::toString).toList();
        assertTrue(ptdfBoundariesStrings.contains("{10YBE----------2}-{BE2-XXXXXXXXXXXX}"));
        assertTrue(ptdfBoundariesStrings.contains("{10YES-REE------0}-{ES1-XXXXXXXXXXXX}"));
        assertTrue(ptdfBoundariesStrings.contains("{10YFR-RTE------C}-{FR1-XXXXXXXXXXXX}-{10YBE----------2}+{BE1-XXXXXXXXXXXX}"));
    }

    @Test
    void ptdfBoundariesConcatTest() {
        VirtualHubsConfiguration virtualHubsConfiguration = new VirtualHubsConfiguration();
        MarketArea marketAreaFr = new MarketArea(FR, EIC_FR, true, false);
        MarketArea marketAreaBe = new MarketArea(BE, EIC_BE, false, true);
        MarketArea marketAreaSk = new MarketArea(SK, EIC_SK, true, false);
        virtualHubsConfiguration.addMarketArea(marketAreaFr);
        virtualHubsConfiguration.addMarketArea(marketAreaBe);
        virtualHubsConfiguration.addMarketArea(marketAreaSk);
        virtualHubsConfiguration.addBorderDirection(new BorderDirection(FR, SK, false));
        virtualHubsConfiguration.addVirtualHub(new VirtualHub("FR1", "FR1-XXXXXXXXXXXX", true, false, NODE_NAME_FR, marketAreaFr, "BE1"));
        virtualHubsConfiguration.addVirtualHub(new VirtualHub("BE1", "BE1-XXXXXXXXXXXX", false, true, NODE_NAME_SK, marketAreaBe, "FR1"));
        RaoParameters raoParameters = new RaoParameters();

        RaoParametersService.setPtdfBoundaries(virtualHubsConfiguration, raoParameters);

        RelativeMarginsParameters relativeMarginsParameters = raoParameters.getRelativeMarginsParameters().get();
        assertNotNull(relativeMarginsParameters.getPtdfBoundaries());
        assertEquals(2, relativeMarginsParameters.getPtdfBoundaries().size());
        List<String> ptdfBoundariesStrings = relativeMarginsParameters.getPtdfBoundaries().stream().map(ZoneToZonePtdfDefinition::toString).toList();
        assertTrue(ptdfBoundariesStrings.contains("{10YFR-RTE------C}-{10YSK-SEPS-----K}"));
        assertTrue(ptdfBoundariesStrings.contains("{10YFR-RTE------C}-{FR1-XXXXXXXXXXXX}-{10YBE----------2}+{BE1-XXXXXXXXXXXX}"));
    }

    @Test
    void testEiCode() {
        VirtualHubsConfiguration virtualHubsConfiguration = new VirtualHubsConfiguration();
        MarketArea marketAreaDk = new MarketArea("DK1", "DK1-XXXXXXXXXXXX", false, true);
        MarketArea marketAreaDe = new MarketArea("DE", "DE-XXXXXXXXXXXXX", true, false);
        virtualHubsConfiguration.addMarketArea(marketAreaDk);
        virtualHubsConfiguration.addMarketArea(marketAreaDe);
        virtualHubsConfiguration.addBorderDirection(new BorderDirection("DK1", "DE", true));
        RaoParameters raoParameters = new RaoParameters();

        RaoParametersService.setPtdfBoundaries(virtualHubsConfiguration, raoParameters);

        RelativeMarginsParameters relativeMarginsParameters = raoParameters.getRelativeMarginsParameters().get();
        assertNotNull(relativeMarginsParameters.getPtdfBoundaries());
        assertEquals(1, relativeMarginsParameters.getPtdfBoundaries().size());
        List<String> ptdfBoundariesStrings = relativeMarginsParameters.getPtdfBoundaries().stream().map(ZoneToZonePtdfDefinition::toString).toList();
        assertTrue(ptdfBoundariesStrings.contains("{DE-XXXXXXXXXXXXX}-{DK1-XXXXXXXXXXXX}"));
    }
}
