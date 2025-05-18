package m4z.app.etl.svg;

import m4z.app.config.AppConfig;
import m4z.app.etl.Processor;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.logging.Level;
import java.util.logging.Logger;

public class SVGOptimizer extends Processor {
    private static final Logger logger = Logger.getLogger(SVGOptimizer.class.getName());
    public static String SVGO_URL = "https://optimize.svgomg.net/";

    public SVGOptimizer(AppConfig config) {
        super(config);

    }

    @Override
    public void process() {
        Path source = Paths.get(getConfig().app().optimizer().input().path());
        Path target = Paths.get(getConfig().app().optimizer().output());
        optimize(source, target);
    }

    protected void optimize(Path source, Path target) {
        ProcessBuilder processBuilder = new ProcessBuilder();
        processBuilder.command("svgo", "-rf", source.toFile().getAbsolutePath(), "-o", target.toFile().getAbsolutePath());

        try {
            Process process = processBuilder.start();
            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            String line;
            while ((line = reader.readLine()) != null) {
                logger.info(line);
            }

            int exitCode = process.waitFor();
            logger.info("Exited with code: " + exitCode);

        } catch (IOException | InterruptedException e) {
            logger.log(Level.SEVERE, "optimizeUsingNodeJSModule", e);
        }
    }
}
