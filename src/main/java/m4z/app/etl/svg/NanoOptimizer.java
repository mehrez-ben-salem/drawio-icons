package m4z.app.etl.svg;

import m4z.app.config.AppConfig;
import m4z.app.config.ProxyManager;
import m4z.app.etl.Processor;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.PrintWriter;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Base64;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

public class NanoOptimizer extends Processor {
    public static String NANO_OPTIMIZER_PATH = "https://vecta.io/nano/api";// "https://api.vecta.io/nano/optimize";

    public NanoOptimizer(AppConfig config) {
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
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                    if (file.toFile().getName().endsWith(".svg")) {
                        Path optimized = target.resolve(source.relativize(file));
                        optimizeInternal(file, optimized);
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

    protected void optimizeInternal(Path svg, Path optimized) {
        try {
            String content = callOptimize(svg).get();
            if (content != null) {
                write(optimized, content);
            }
        } catch (InterruptedException | ExecutionException | IOException e) {
            throw new RuntimeException(e);
        }
    }

    protected CompletableFuture<String> callOptimize(Path svg) throws IOException {
        HttpClient httpClient = HttpClient.newBuilder()
                .proxy(ProxyManager.proxySelector())
                .build();

        String requestBody = read(svg);
        HttpRequest request = HttpRequest.newBuilder(URI.create(NANO_OPTIMIZER_PATH))
                //.header("Content-Type", "text/plain")
                .header("Content-Type", "image/svg+xml")
                //.header("Accept", "image/svg+xml")
                .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                .build();

        return httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenApply(res -> {
                    System.out.printf("Http Status Code %d%n", res.statusCode());
                    return res;
                })
                .thenApply(HttpResponse::body);
    }

    protected String toBase64(Path svg) {
        return String.format("data:image/png;base64,%s", new String(Base64.getEncoder().encode(read(svg).getBytes())));
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

}
