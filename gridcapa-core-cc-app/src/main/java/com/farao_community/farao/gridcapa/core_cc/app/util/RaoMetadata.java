/*
 * Copyright (c) 2022, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package com.farao_community.farao.gridcapa.core_cc.app.util;

/**
 * @author Mohamed BenRejeb {@literal <mohamed.ben-rejeb at rte-france.com>}
 */
public class RaoMetadata {
    public enum Indicator {
        RAO_REQUESTS_RECEIVED("RAO requests received", 1), // per BD
        RAO_REQUEST_RECEPTION_TIME("RAO request reception time", 2), // per BD
        RAO_OUTPUTS_SENT("RAO outputs sent", 3), // per BD
        RAO_OUTPUTS_SENDING_TIME("RAO outputs sending time", 4), // per BD
        RAO_RESULTS_PROVIDED("RAO results provided", 5), // per TS
        RAO_COMPUTATION_STATUS("RAO computation status", 6), // per BD + per TS
        RAO_START_TIME("RAO computation start", 7), // per BD + per TS
        RAO_END_TIME("RAO computation end", 8), // per BD + per TS
        RAO_COMPUTATION_TIME("RAO computation time (minutes)", 9); // per BD + per TS

        private String csvLabel;
        private int order;
        Indicator(String csvLabel, int order) {
            this.csvLabel = csvLabel;
            this.order = order;
        }

        public String getCsvLabel() {
            return this.csvLabel;
        }

        public int getOrder() {
            return order;
        }
    }
}
