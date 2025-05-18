package m4z.app.tools;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class Utils {
    private static final Path root = Paths.get("C:/INTERNAL/DA/vector-icon-lib");
    private static final Path raw = Paths.get("C:/INTERNAL/DA/vector-icon-lib/OfficeSymbols_2014");

    public static void main(String[] args) throws IOException {
        Files.walk(Paths.get("OfficeSymbols_2014_SVG_Optimized"))
                .filter(path -> path.toString().endsWith(".svg"))
                .forEach(Utils::correctName);
    }

    public static void correctName(Path path) {
        File file = path.toFile();
        File correctFile = new File(file.getParentFile(), file.getName().replaceAll("[\\s-,]+", "_"));
        file.renameTo(correctFile);
        //System.out.printf("%s -> %s%n", file.getName(), correctFile.getName());
    }

    public static void extractSize(String[] args) throws IOException {
        //Paths.get("C:/INTERNAL/DA/vector-icon-lib/optimized");
        Files.walk(raw)
                .filter(path -> path.toString().endsWith(".svg"))
                .forEach(rawSvg -> {
                            try {
                                Path optimizedSvg = toOptimized(rawSvg);
                                //System.out.printf("%s -> %s%n", rawSvg, optimizedSvg);
                                long rawSize = Files.size(rawSvg);
                                long optimizeSize = Files.size(optimizedSvg);
                                int ratio = (int) (optimizeSize * 100 / rawSize);
                                System.out.printf("%s, %d, %d, %d%%%n", root.relativize(rawSvg), rawSize, optimizeSize, ratio);
                            } catch (IOException e) {
                                throw new RuntimeException(e);
                            }
                        }
                );
    }

    public static Path toOptimized(Path raw) {
        return Paths.get("C:/INTERNAL/DA/vector-icon-lib/optimized", root.relativize(raw).toString());
    }
}
