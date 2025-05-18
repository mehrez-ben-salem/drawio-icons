package m4z.app.etl.drawio;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import m4z.app.config.AppConfig;
import m4z.app.config.ProxyManager;
import m4z.app.etl.Processor;
import org.jsoup.Connection;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.parser.Parser;

import java.io.IOException;
import java.lang.reflect.Type;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.text.Normalizer;
import java.util.*;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class IconsExtractor extends Processor {

    private static final Logger logger = Logger.getLogger(IconsExtractor.class.getName());
    private static final String DATA_IMAGE_PREFIX = "data:image/svg+xml;base64,";
    private static final Pattern P_SIZE = Pattern.compile("(?<size>(\\d+\\.?\\d+))(p[tx])?");
    // Regex to match CSS property-value pairs
    private static final Pattern P_STYLE = Pattern.compile("([\\w-]+)\\s*:\\s*([^;]+)");

    private final Pattern inputPattern;

    public IconsExtractor(AppConfig config) {
        super(config);
        inputPattern = Pattern.compile(getConfig().app().extractor().input().filter().regex(), Pattern.CASE_INSENSITIVE);
    }

    @Override
    public void process() {
        Path source = Paths.get(getConfig().app().extractor().input().path());
        Path target = Paths.get(getConfig().app().extractor().output());
        Stream<MxLibrary> libraryStream = extractLibraries(source);
        saveIcons(libraryStream, target);
    }

    protected void saveIcons(Stream<MxLibrary> libraries, Path target) {
        libraries.filter(this::accept).forEach(library -> saveIcons(library, target));
    }

    protected Stream<MxLibrary> extractLibraries(Path source) {
        try {
            Document doc = Jsoup.parse(source);
            Element container = doc.getElementsByClass("geSidebarContainer").first();
            return container.selectStream("a[title].geTitle")
                    .map(this::extractLibrary);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    protected MxLibrary extractLibrary(Element libraryAnchor) {
        Element span = libraryAnchor.selectFirst("span");
        String libraryName = span.text();
        MxLibrary library = new MxLibrary();
        library.setName(libraryName);

        if (isRemoteLibrary(libraryAnchor)) {
            library.setIcons(extractRemoteIcons(libraryAnchor));
        } else {
            library.setIcons(extractIcons(libraryAnchor));
        }

        return library;
    }

    protected boolean accept(MxLibrary library) {
        if (getConfig().app().extractor().input().filter().active()) {
            boolean accepted = inputPattern.matcher(library.getName()).matches();
            logger.fine(String.format("Icons palette '%s' %s", library.getName(), (accepted ? "accepted" : "rejected")));
            return accepted;
        }
        return !library.getIcons().isEmpty();
    }

    protected void saveIcons(MxLibrary library, Path target) {
        Path folder = Paths.get(target.toFile().getAbsolutePath(), toPath(library.getName()));
        try {
            for (MxIcon icon : library.getIcons()) {
                if (icon.isSvgImage()) {
                    Files.createDirectories(folder);
                    Path file = Paths.get(folder.toAbsolutePath().toString(), icon.getTitle() + ".svg");
                    Document doc = Jsoup.parse(icon.getSvgPayload(), Parser.xmlParser());
                    Element svg = doc.selectFirst("svg");
                    Files.writeString(file,
                            //svg.toString().replaceAll(">\\s+<", "><").replace("viewbox", "viewBox"),
                            svg.toString().replace("viewbox", "viewBox"),
                            StandardOpenOption.WRITE,
                            StandardOpenOption.CREATE,
                            StandardOpenOption.TRUNCATE_EXISTING);
                }
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    protected boolean isRemoteLibrary(Element libraryAnchor) {
        String title = libraryAnchor.attr("title");
        String[] values = title.split("\r\n");
        return values.length > 1;
    }

    protected Set<MxIcon> extractRemoteIcons(Element libraryAnchor) {
        String title = libraryAnchor.attr("title");
        String[] values = title.split("\r\n");
        String url = java.net.URLDecoder.decode(values[1].substring(1), StandardCharsets.UTF_8);
        HttpClient httpClient = HttpClient.newBuilder()
                .proxy(ProxyManager.proxySelector())
                .build();
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .build();

        try {
            HttpResponse<byte[]> response = httpClient.send(request, HttpResponse.BodyHandlers.ofByteArray());
            if (response.statusCode() == 200) {
                String responseBody = new String(response.body());
                Document doc = Jsoup.parse(responseBody, Parser.xmlParser());
                Element element = doc.selectFirst("mxlibrary");

                Type type = new TypeToken<Set<MxIcon>>() {
                }.getType();
                Gson gson = new GsonBuilder().create();
                Set<MxIcon> icons = gson.fromJson(element.text(), type);
                return icons;
            }
        } catch (IOException | InterruptedException e) {
            throw new RuntimeException(e);
        }

        return null;
    }

    protected Set<MxIcon> extractIcons(Element libraryAnchor) {
        Element div = libraryAnchor.nextElementSibling();
        return div.selectStream("div.geSidebar>a.geItem")
                .map(this::extractIcon)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
    }

    protected MxIcon extractIcon(Element iconAnchor) {
        Element svg = iconAnchor.selectFirst("svg");
        if (svg == null) {
            return null;
        }
        Element title = iconAnchor.nextElementSibling();
        if (title == null) {
            return null;
        }
        Map<String, String> style = parseStyle(svg);
        MxIcon icon = new MxIcon();
        icon.setTitle(toIconName(title.text()));
        if (style.containsKey("height")) {
            icon.setHeight(extractSize(style.get("height")));
        }
        if (style.containsKey("width")) {
            icon.setWidth(extractSize(style.get("width")));
        }
        icon.setSvg(loadImage(svg).toString());

        return icon;
    }

    protected String extractSize(String value) {
        logger.fine(value);
        Matcher matcher = P_SIZE.matcher(value);
        if (matcher.matches()) {
            return matcher.group("size");
        }
        return value;
    }

    protected Element loadImage(Element svg) {
        Element image = svg.selectFirst("g>image[xlink:href]");
        if (image != null) {
            logger.fine("Resolve referenced image");
            Element linkedSvg = loadLinkedImage(image.attr("xlink:href"));
            if (linkedSvg != null) {
                svg.replaceWith(linkedSvg);
                return loadImage(linkedSvg);
            }
        }
        return svg;
    }

    protected Element loadLinkedImage(String href) {
        if (href.startsWith(DATA_IMAGE_PREFIX)) {
            logger.fine(String.format("Nested Base64 image resolution: %s", href));
            String image = new String(Base64.getDecoder().decode(href.substring(DATA_IMAGE_PREFIX.length())));
            Document doc = Jsoup.parse(image, Parser.xmlParser());
            return doc.selectFirst("svg");
        } else if (href.endsWith(".svg")) {
            logger.fine(String.format("Resolve external reference image: %s", href));
            try {
                Connection connection = Jsoup.connect(href);
                connection.proxy(ProxyManager.proxy());
                Document doc = connection.get();
                return doc.selectFirst("svg");
            } catch (IOException e) {
                logger.severe(String.format("Ressource Not Found: %s", href));
                //throw new RuntimeException(e);
            }
        }
        return null;
    }

    protected String toPath(String libraryName) {
        String path = Arrays.stream(libraryName.trim().split("/"))
                .map(String::trim)
                .map(this::removeAccents)
                .collect(Collectors.joining("/"));
        logger.fine(String.format("Icon palette description to Path: %s -> %s", libraryName, path));
        return path;
    }

    protected String toIconName(String description) {
        String iconName = description.trim()
                .replaceAll("[)(&]", "")
                .replaceAll("[\\s-,/\\:?\"<>|.]+", "_")
                .replace("_$", "");
        iconName = removeAccents(iconName);
        logger.fine(String.format("Icon description to icon name :%s -> %s", description, iconName));
        return iconName;
    }

    protected String removeAccents(String text) {
        if (text == null) return null;

        return Normalizer.normalize(text, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "");
    }

    protected Map<String, String> parseStyle(Element element) {
        Map<String, String> styleMap = new HashMap<>();
        String styleAttribute = element.attr("style");
        if ((styleAttribute == null) || styleAttribute.trim().isEmpty()) {
            return styleMap;
        }

        Matcher matcher = P_STYLE.matcher(styleAttribute);
        while (matcher.find()) {
            String key = matcher.group(1).trim();
            String value = matcher.group(2).trim();
            styleMap.put(key, value);
        }
        return styleMap;
    }
}
