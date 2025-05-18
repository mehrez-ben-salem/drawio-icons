package m4z.app.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;

import java.io.IOException;
import java.nio.file.Path;

public class ConfigLoader {
    public static AppConfig load(Path yaml) {
        try {
            String content = VariableResolver.getInstance().resolveVariables(yaml);
            ObjectMapper yamlMapper = new ObjectMapper(new YAMLFactory());
            return yamlMapper.readValue(content, AppConfig.class);
        } catch (IOException e) {
            throw new ConfigException(e);
        }
    }

    public static AppConfig load(String configPath) {
        return load(Path.of(configPath));
        //return load(Path.of(AppConfig.class.getClassLoader().getResource(configPath).getPath()));
    }

    public static class ConfigException extends RuntimeException {
        public ConfigException(Throwable cause) {
            super(cause);
        }
    }

}    // Register the deserializer
