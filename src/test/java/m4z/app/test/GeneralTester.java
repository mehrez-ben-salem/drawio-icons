package m4z.app.test;

import m4z.app.config.ConfigManager;
import m4z.app.etl.drawio.IconsExtractor;
import m4z.app.etl.plantuml.IconToSpriteTransformer;
import m4z.app.etl.svg.DefaultOptimizer;
import m4z.app.etl.svg.NanoOptimizer;
import m4z.app.etl.svg.SVGOptimizer;

import java.io.IOException;

public class GeneralTester {
    public static void main(String[] args) throws IOException {
        testIconsExtractor();
        //testSVGOptimizer();
        //testNanoOptimizer();
        testDefaultOptimizer();
        testIconToSpriteTransformer();
    }

    public static void testIconsExtractor() throws IOException {
        IconsExtractor iconsExtractor = new IconsExtractor(ConfigManager.getConfig());
        iconsExtractor.process();
    }

    public static void testIconToSpriteTransformer() {
        IconToSpriteTransformer transformer = new IconToSpriteTransformer(ConfigManager.getConfig());
        transformer.process();
    }

    public static void testDefaultOptimizer() {
        DefaultOptimizer optimizer = new DefaultOptimizer(ConfigManager.getConfig());
        optimizer.process();
    }

    public static void testSVGOptimizer() {
        SVGOptimizer optimizer = new SVGOptimizer(ConfigManager.getConfig());
        optimizer.process();
    }

    public static void testNanoOptimizer() {
        NanoOptimizer optimizer = new NanoOptimizer(ConfigManager.getConfig());
        optimizer.process();
    }
}
