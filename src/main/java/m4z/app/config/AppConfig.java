package m4z.app.config;

import java.util.Objects;

public record AppConfig(App app) {
    public record App(
            String name,
            String version,
            Proxy proxy,
            Processor extractor,
            Processor optimizer,
            Processor transformer
    ) {
        public App {
            Objects.requireNonNull(name, "App name cannot be null");
            Objects.requireNonNull(version, "App version cannot be null");
        }

        public record Proxy(
                boolean active,
                String host,
                Integer port
        ) {
            public Proxy {
                if (active) {
                    Objects.requireNonNull(host, "Proxy host cannot be null when active");
                    Objects.requireNonNull(port, "Proxy port cannot be null when active");
                }
            }
        }

        public record Input(
                String path,
                Filter filter
        ) {
            public Input {
                //Objects.requireNonNull(path, "Path cannot be null");
            }
        }

        public record Filter(
                boolean active,
                String regex
        ) {
            public Filter {
                if (active) {
                    Objects.requireNonNull(regex, "Regex cannot be null when filter is active");
                }
            }
        }

        public record Processor(
                String type,
                Input input,
                String output
        ) {
            public Processor {
                //Objects.requireNonNull(type, "Type cannot be null");
                //Objects.requireNonNull(output, "Output path cannot be null");
            }
        }

    }

}

