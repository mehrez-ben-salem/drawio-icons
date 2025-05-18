package m4z.app.config;

public class ConfigManager {
    private static AppConfig config;

    public static void initialize(String configPath) {
        config = ConfigLoader.load(configPath);
    }

    public static AppConfig getConfig() {
        if (config == null) {
            initialize("config/app-config.yaml");
            //throw new IllegalStateException("Configuration not initialized");
        }
        return config;
    }
}
