package com.farao_community.farao.gridcapa_core_cc.app;

import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

@Configuration
public class ApplicationStartupConfig implements ApplicationListener<ApplicationReadyEvent> {

    private final Environment springEnvironment;

    private static String taskTempOutputsDir;

    public ApplicationStartupConfig(Environment springEnvironment) {
        this.springEnvironment = springEnvironment;
    }

    @Override
    public void onApplicationEvent(final ApplicationReadyEvent event) {
        taskTempOutputsDir = springEnvironment.getProperty("core-cc-runner.filesystem.tmp-output-directory");
    }

    public static String getTaskTempOutputsDir() {
        return taskTempOutputsDir != null ? taskTempOutputsDir : "/tmp/tmp-outputs"; // workaround for unit tests
    }
}
