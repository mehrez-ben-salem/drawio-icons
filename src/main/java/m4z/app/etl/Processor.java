package m4z.app.etl;

import m4z.app.config.AppConfig;

public abstract class Processor {
    final private AppConfig config;

    public Processor(AppConfig config) {
        this.config = config;
    }

    public AppConfig getConfig() {
        return config;
    }

    public abstract void process();
}
