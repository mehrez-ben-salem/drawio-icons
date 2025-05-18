package m4z.app.config;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class VariableResolver {
    private static final VariableResolver instance = new VariableResolver();
    private final Pattern pattern = Pattern.compile("\\$\\{(?<key>[^:}]+)(?::-(?<default>[^}]+))?}");
    private final Map<String, String> variables;
    private final List<Function<String, String>> resolvers;

    private VariableResolver() {
        this.variables = new HashMap<>();
        this.resolvers = new ArrayList<>();

        // Default resolvers
        addResolver(this::resolveSystemProperty);
        addResolver(this::resolveEnvironmentVariable);
    }

    public static VariableResolver getInstance() {
        return instance;
    }

    public String resolveVariables(Path path) {
        String content = null;
        try {
            content = Files.readString(path);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        return resolve(content);
    }

    private void addVariable(String key, String value) {
        variables.put(key, value);
    }

    private void addResolver(Function<String, String> resolver) {
        resolvers.add(resolver);
    }

    private String resolve(String expression) {
        if (expression == null) return null;

        Matcher matcher = pattern.matcher(expression);
        StringBuilder result = new StringBuilder();

        while (matcher.find()) {
            String key = matcher.group("key");
            String defaultValue = matcher.group("default");
            String value = resolveKey(key);

            if (value == null) {
                value = defaultValue != null ? defaultValue : matcher.group();
            }

            matcher.appendReplacement(result, Matcher.quoteReplacement(value));
        }

        matcher.appendTail(result);
        return result.toString();
    }

    private String resolveKey(String key) {
        // Check direct properties first
        if (variables.containsKey(key)) {
            return variables.get(key);
        }

        // Try all resolvers
        for (Function<String, String> resolver : resolvers) {
            String value = resolver.apply(key);
            if (value != null) {
                variables.put(key, value);
                return value;
            }
        }

        return null;
    }

    private String resolveSystemProperty(String key) {
        return System.getProperty(key);
    }

    private String resolveEnvironmentVariable(String key) {
        return System.getenv(key);
    }
}