package m4z.app.etl.svg;

import m4z.app.config.AppConfig;
import m4z.app.etl.Processor;
import org.jsoup.Jsoup;
import org.jsoup.nodes.*;
import org.jsoup.parser.Parser;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

public class DefaultOptimizer extends Processor {
    private static final Logger logger = Logger.getLogger(DefaultOptimizer.class.getName());
    private static final Pattern P_SIZE = Pattern.compile("(?<size>(\\d+\\.?\\d+))p[tx]");
    private static final String equals_something = "=\"([^\"]+)\"";
    private static final String colon_something = ":([^;\"]+)";
    private static final Pattern STYLE_FONT_SIZE = Pattern.compile(Pattern.quote("font-size") + colon_something);
    private static final Pattern DATA_FONT_SIZE = Pattern.compile("font-size" + equals_something);


    public DefaultOptimizer(AppConfig config) {
        super(config);
    }

    @Override
    public void process() {
        Path source = Paths.get(getConfig().app().optimizer().input().path());
        Path target = Paths.get(getConfig().app().optimizer().output());
        optimize(source, target);
    }

    protected void optimize(Path source, Path target) {
        try {
            Files.walkFileTree(source, new SimpleFileVisitor<Path>() {
                @Override
                public FileVisitResult visitFile(Path svg, BasicFileAttributes attrs) {
                    if (svg.toFile().getName().endsWith(".svg")) {
                        Path optimized = target.resolve(source.relativize(svg));
                        optimizeInternal(svg, optimized);
                    }
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
                    Path optimized = target.resolve(source.relativize(dir));
                    try {
                        Files.createDirectories(optimized);
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFileFailed(Path file, IOException exc) {
                    System.err.println("Error accessing file: " + file);
                    return FileVisitResult.CONTINUE;
                }
            });
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    protected void optimizeInternal(Path icon, Path optimized) {
        String content = read(icon);
        Document doc = Jsoup.parse(content, Parser.xmlParser());
        Element svg = doc.selectFirst("svg");
        svg = optimize(svg);
        write(optimized, svg.outerHtml().replaceAll(">[\r\n\\s]+<", "><"));
    }

    protected Element optimize(Element svg) {
        logger.fine("optimizing svg element limiting attribute set and compacting structure");
        return optimizeAttributes(optimizeTree(svg));
    }

    protected String read(Path svg) {
        try {
            return new String(Files.readAllBytes(svg));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    protected void write(Path svg, String content) {
        try (PrintWriter pw = new PrintWriter(svg.toFile())) {
            pw.print(content);
            pw.flush();
        } catch (FileNotFoundException e) {
            throw new RuntimeException(e);
        }
    }

    protected Element optimizeAttributes(Element svg) {
        discardCustomAttributes(svg);

        Attribute width = svg.attribute("width");
        Attribute height = svg.attribute("height");
        Attribute viewBox = svg.attribute("viewBox");
        Map<String, String> styles = toMap(svg.attr("style"));

        svg.clearAttributes();
        svg.attr("xmlns", "http://www.w3.org/2000/svg");
        if (svg.selectFirst("[^xlink:]") != null) {
            svg.attr("xmlns:xlink", "http://www.w3.org/1999/xlink");
        }

        if (width != null) {
            svg.attr("width", correctSize(width.getValue()));
        } else if (styles.containsKey("width")) {
            svg.attr("width", correctSize(styles.get("width")));
        }

        if (height != null) {
            svg.attr("height", correctSize(height.getValue()));
        } else if (styles.containsKey("height")) {
            svg.attr("height", correctSize(styles.get("height")));
        }

        if (viewBox != null) {
            logger.fine("Correct already existing viewBox attribute");
            correctViewBox(viewBox);
            svg.attr("viewBox", viewBox.getValue());
        } else {
            String value = String.format("0 0 %s %s", svg.attr("width"), svg.attr("height"));
            svg.attr("viewBox", value);
            logger.fine(String.format("Append constructed attribute viewBox(%s)", value));
        }
        return svg;
    }

    protected Element optimizeTree(Element svg) {
        logger.fine("Discard extra directives (foreignObject, metadata, title, desc)");
        //discard unnecessary elements
        //svg.select("foreignObject, metadata, title, desc").forEach(Node::remove);
        svg.select("*").forEach(element -> {
            logger.finer("Discard custom directives, having ':' in tag name)");
            //discard custom elements
            if (element.tagName().contains(":")) {
                element.remove();
            }
        });

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

    protected void discardCustomAttributes(Element svg) {
        svg.select("*").forEach(element -> {
            Attributes origin = element.attributes().clone();
            element.clearAttributes();
            origin.forEach(attribute -> {
                if (attribute.getKey().equals("xlink:href")) {//preserve internal reference
                    logger.finer(String.format("Preserve attribute %s=%s", attribute.getKey(), attribute.getValue()));
                    element.attr(attribute.getKey(), attribute.getValue());
                } else if (!(attribute.getKey().contains(":") || attribute.getKey().contains("-"))) {   //discard custom attributes
                    logger.finer(String.format("Retain attribute %s=%s", attribute.getKey(), attribute.getValue()));
                    correctFontSize(attribute);
                    element.attr(attribute.getKey(), attribute.getValue());
                } else {
                    logger.finer(String.format("Discard attribute %s=%s", attribute.getKey(), attribute.getValue()));
                }
            });
        });
    }

    protected void correctFontSize(Attribute attribute) {
        if (true/*attribute.getKey().equalsIgnoreCase("font-size") || attribute.getKey().equalsIgnoreCase("style")*/) {
            String attributeValue = attribute.getValue();

            Matcher matcher = P_SIZE.matcher(attributeValue);
            StringBuilder result = new StringBuilder();

            while (matcher.find()) {
                String size = matcher.group("size");
                String correctedSize = correctSize(size);
                logger.finer(String.format("%s -> %s", size, correctedSize));
                matcher.appendReplacement(result, Matcher.quoteReplacement(correctedSize));
            }

            matcher.appendTail(result);
            attribute.setValue(result.toString());
            logger.fine(String.format("attr[%s] : %s -> %s", attribute.getKey(), attributeValue, attribute.getValue()));
        }
    }

    protected String correctSize(String size) {
        String correctedSize = String.valueOf(Math.round(Float.parseFloat(size.replaceAll("[^\\d.]", ""))));
        logger.finer(String.format("%s -> %s", size, correctedSize));
        return correctedSize;
    }

    protected void correctViewBox(Attribute viewBox) {
        String[] value = viewBox.getValue().split(" ");
        String[] correcteValue = value.clone();
        correcteValue[2] = correctSize(value[2]);
        correcteValue[3] = correctSize(value[3]);
        String[] concatenation = Stream.of(value, correcteValue).flatMap(Stream::of).toArray(String[]::new);
        logger.finer(String.format("viewBox=(%s %s %s %s) -> viewBox=(%s %s %s %s)", concatenation));
        viewBox.setValue(String.join(" ", correcteValue));
    }

    protected Map<String, String> toMap(String style) {
        logger.finer(String.format("Convert style in key value pair :: %s", style));
        Map<String, String> map = new HashMap<>();
        for (String part : style.split(";")) {
            String[] pair = part.split(":");
            if (pair.length == 2) {
                map.put(pair[0].trim(), pair[1].trim());
            }
        }
        return map;
    }
}
