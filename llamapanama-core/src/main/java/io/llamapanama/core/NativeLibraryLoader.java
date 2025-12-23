package io.llamapanama.core;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Locale;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;

final class NativeLibraryLoader {
    private static final AtomicBoolean LOADED = new AtomicBoolean(false);
    private static Path loadedPath;
    private NativeLibraryLoader() {}

    static Path ensureLoaded() {
        if (LOADED.get()) {
            return loadedPath;
        }
        synchronized (NativeLibraryLoader.class) {
            if (LOADED.get()) {
                return loadedPath;
            }
            try {
                String mapped = mapLibraryName();
                Path extracted = extractLibrary(mapped);
                System.load(extracted.toAbsolutePath().toString());
                LOADED.set(true);
                loadedPath = extracted;
                return extracted;
            } catch (IOException e) {
                throw new IllegalStateException("Failed to load native library", e);
            }
        }
    }

    private static String mapLibraryName() {
        String os = System.getProperty("os.name").toLowerCase(Locale.ROOT);
        String archRaw = System.getProperty("os.arch").toLowerCase(Locale.ROOT);
        String arch = switch (archRaw) {
            case "x86_64", "amd64" -> "x86_64";
            case "aarch64", "arm64" -> "aarch64";
            default -> archRaw;
        };
        String osPart;
        String lib;
        if (os.contains("win")) {
            osPart = "windows";
            lib = "llamapanama.dll";
        } else if (os.contains("mac") || os.contains("darwin")) {
            osPart = "darwin";
            lib = "libllamapanama.dylib";
        } else {
            osPart = "linux";
            lib = "libllamapanama.so";
        }
        return osPart + "-" + arch + "/" + lib;
    }

    private static Path extractLibrary(String mapped) throws IOException {
        String resourcePath = "/" + mapped;
        try (InputStream in = NativeLibraryLoader.class.getResourceAsStream(resourcePath)) {
            if (in == null) {
                throw new IOException("Native library resource not found: " + resourcePath);
            }
            Path tempDir = Files.createTempDirectory("llamapanama");
            Path target = tempDir.resolve(mapped.substring(mapped.lastIndexOf('/') + 1));
            Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
            target.toFile().deleteOnExit();
            Objects.requireNonNull(tempDir.toFile()).deleteOnExit();
            return target;
        }
    }
}
