package com.farao_community.farao.gridcapa_core_cc.app.messaging;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.ConstructorBinding;

@ConstructorBinding
@ConfigurationProperties(prefix = "core-cc")
public class CoreCCInputsProperties {

    private final FilenamesProperties filenames;

    public CoreCCInputsProperties(FilenamesProperties filenames) {
        this.filenames = filenames;
    }

    public FilenamesProperties getFilenames() {
        return filenames;
    }

    public static class FilenamesProperties {
        private final String request;
        private final String cgms;
        private final String crac;
        private final String glsk;
        private final String refprog;
        private final String virtualhubs;

        public FilenamesProperties(String request, String cgms, String glsk, String refprog, String crac, String virtualhubs) {
            this.request = request;
            this.cgms = cgms;
            this.glsk = glsk;
            this.refprog = refprog;
            this.crac = crac;
            this.virtualhubs = virtualhubs;
        }

        public String getRequest() {
            return request;
        }

        public String getCgms() {
            return cgms;
        }

        public String getGlsk() {
            return glsk;
        }

        public String getRefprog() {
            return refprog;
        }

        public String getCrac() {
            return crac;
        }

        public String getVirtualhubs() {
            return virtualhubs;
        }
    }
}
