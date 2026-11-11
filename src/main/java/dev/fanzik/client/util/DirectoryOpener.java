package dev.fanzik.client.util;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

public final class DirectoryOpener {
    private DirectoryOpener() {
        throw new AssertionError();
    }

    public static void open(Path directory) throws IOException {
        Path absolute = directory.toAbsolutePath().normalize();
        Files.createDirectories(absolute);
        String operatingSystem = System.getProperty("os.name", "");
        IOException lastFailure = null;
        for (List<String> command : commandsFor(operatingSystem, absolute)) {
            try {
                Process process = new ProcessBuilder(command).redirectError(ProcessBuilder.Redirect.DISCARD)
                        .redirectOutput(ProcessBuilder.Redirect.DISCARD).start();
                if (!process.waitFor(3, TimeUnit.SECONDS) || process.exitValue() == 0
                        || operatingSystem.toLowerCase(Locale.ROOT).startsWith("windows")) {
                    return;
                }
                lastFailure = new IOException(command.getFirst() + " exited with code " + process.exitValue());
            } catch (IOException exception) {
                lastFailure = exception;
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new IOException("Opening the config directory was interrupted", exception);
            }
        }
        throw new IOException("Cannot open the file manager. Directory: " + absolute, lastFailure);
    }

    public static List<List<String>> commandsFor(String operatingSystem, Path directory) {
        String os = operatingSystem.toLowerCase(Locale.ROOT);
        String path = directory.toAbsolutePath().normalize().toString();
        if (os.startsWith("windows")) {
            return List.of(List.of("explorer.exe", path));
        }
        if (os.contains("linux")) {
            return List.of(List.of("xdg-open", path), List.of("gio", "open", path));
        }
        if (os.contains("mac") || os.contains("darwin")) {
            return List.of(List.of("open", path));
        }
        return List.of();
    }
}
