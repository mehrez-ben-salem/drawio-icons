package m4z.app.etl.plantuml;

import m4z.app.config.AppConfig;
import m4z.app.etl.Processor;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.nodes.Node;
import org.jsoup.parser.Parser;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Set;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class IconToSpriteTransformer extends Processor {
    private static final Logger logger = Logger.getLogger(IconToSpriteTransformer.class.getName());
    private static final Set<String> unsupportedTag = Set.of("text", "tspan", "textPath", "font", "glyph", "missing-glyph"
            , "style", "linearGradient", "radialGradient", "pattern", "filter", "clipPath", "mask"
            , "script", "a", "animate", "animateTransform", "animateMotion"
            , "metadata", "title", "desc", "marker"
            , "symbol", "use", "foreignObject");

    public IconToSpriteTransformer(AppConfig appConfig) {
        super(appConfig);
    }

    @Override
    public void process() {
        Path icons = Paths.get(getConfig().app().transformer().input().path());
        Path sprites = Paths.get(getConfig().app().transformer().output());
        transform(icons, sprites);
    }

    public void transform(Path icons, Path sprites) {
        try (Stream<Path> pathStream = Files.walk(icons)) {
            pathStream.filter(Files::isDirectory).forEach(directory -> transform(directory, icons, sprites));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    protected void transform(Path directory, Path icons, Path sprites) {
        logger.fine("Start building sprite for directory " + directory.toFile().getAbsolutePath());
        try (Stream<Path> pathStream = Files.list(directory)) {
            String content = pathStream.filter(Files::isRegularFile)
                    .map(Path::toFile)
                    .filter(f -> f.getName().endsWith(".svg"))
                    .map(this::toInlineSprite)
                    .collect(Collectors.joining("\n"));

            if (!content.isEmpty()) {
                File pumlFile = getPumlFile(directory, icons, sprites);
                Files.createDirectories(pumlFile.toPath().getParent());
                try (PrintWriter pw = new PrintWriter(pumlFile)) {
                    pw.println("@startuml");
                    pw.println(content);
                    pw.println("@enduml");
                }
                logger.fine("Created sprite " + pumlFile.getAbsolutePath());

                File pumlViewFile = new File(pumlFile.getParentFile(), pumlFile.getName().replace(".puml", "_view.puml"));
                try (PrintWriter pw = new PrintWriter(pumlViewFile)) {
                    pw.println("@startuml");
                    pw.println("skinparam svgInline true");
                    pw.print("!include ");
                    pw.println(pumlFile.getName());
                    pw.println("listsprites");
                    pw.println("@enduml");
                }
                logger.fine("Created sprite view " + pumlViewFile.getAbsolutePath());
            }
        } catch (IOException e) {
            logger.severe("Error walking over " + directory.toFile().getAbsolutePath());
            throw new RuntimeException(e);
        }
        logger.fine("End building sprite for directory " + directory.toFile().getAbsolutePath());
    }

    protected String toInlineSprite(File svg) {
        String name = toSpriteName(svg.getName().substring(0, svg.getName().lastIndexOf(".")));
        try {
            String content = extractValidContent(svg);
            logger.finer("sprite for " + svg.getAbsolutePath());
            return String.format("sprite %s %s", name, content);
        } catch (IOException e) {
            logger.severe("Error reading file " + svg.getAbsolutePath());
            throw new RuntimeException(e);
        }
    }

    protected String extractValidContent(File file) throws IOException {
        String content = new String(Files.readAllBytes(file.toPath()));
        Document doc = Jsoup.parse(content, Parser.xmlParser());
        Element svg = doc.selectFirst("svg");
        svg.removeAttr("xmlns");
        Element validSVG = removeUnsupportedTags(svg);
        return validSVG.outerHtml();
        //return svg.outerHtml();
    }

    protected Element removeUnsupportedTags(Element svg) {
        logger.fine("Discard unsupported tags");
        //discard unnecessary elements
        svg.select("*")
                .stream()
                .filter(tag -> unsupportedTag.contains(tag.tagName()))
                .forEach(Node::remove);

        logger.fine("Discard empty groups and compact non-empty groups");
        //discard empty groups and compact non-empty groups
        while ((svg.selectFirst("g:empty") != null) || (svg.selectFirst("g>g:only-child") != null)) {
            svg.select("g:empty").forEach(Node::remove);
            Element onlyChild = svg.selectFirst("g>g:only-child");
            while (onlyChild != null) {
                assert onlyChild.parent() != null;
                onlyChild.parent().replaceWith(onlyChild);
                onlyChild = svg.selectFirst("g>g:only-child");
            }
        }
        return svg;
    }

    protected File getPumlFile(Path directory, Path icons, Path sprites) {
        File pumlFile = sprites.resolve(icons.relativize(directory)).toFile();
        pumlFile = Path.of(pumlFile.getParentFile().getAbsolutePath(), toSpriteName(pumlFile.getName()) + ".puml").toFile();
        logger.finer(String.format("%s -> %s", directory.toFile().getAbsolutePath(), pumlFile.getAbsolutePath()));
        return pumlFile;
    }

    public String toSpriteName(String name) {
        String spriteName = name.replaceAll("[)(&]", "")
                .replaceAll("[\\s-,\\/:?\"<>|.]+", "_")
                .replace("_$", "");
        logger.finer(String.format("%s -> %s", name, spriteName));
        return spriteName;
    }
}
