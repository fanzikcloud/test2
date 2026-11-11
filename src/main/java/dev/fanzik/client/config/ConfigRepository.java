package dev.fanzik.client.config;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

public final class ConfigRepository {
    public static final int MAX_FILE_SIZE = 1024 * 1024;
    private final Path root;
    private final Path directory;
    private final ConfigCodec codec;

    public ConfigRepository(Path root, ConfigCodec codec) throws IOException {
        this.root = root.toAbsolutePath().normalize();
        this.directory = this.root.resolve("configs");
        this.codec = codec;
        Files.createDirectories(this.root);
        if (Files.isSymbolicLink(directory)) {
            throw new IOException("The configs directory must not be a symbolic link");
        }
        Files.createDirectories(directory);
    }

    public Path directory() {
        return directory;
    }

    public void save(String name, ConfigSnapshot snapshot) throws IOException {
        write(profilePath(name), snapshot);
    }

    public ConfigSnapshot load(String name) throws IOException {
        return read(profilePath(name));
    }

    public Optional<ConfigSnapshot> loadState() throws IOException {
        Path path = root.resolve("state.json");
        if (!Files.exists(path, LinkOption.NOFOLLOW_LINKS)) {
            return Optional.empty();
        }
        return Optional.of(read(path));
    }

    public void saveState(ConfigSnapshot snapshot) throws IOException {
        write(root.resolve("state.json"), snapshot);
    }

    public Path quarantineState() throws IOException {
        Path target = root.resolve("state.invalid-" + Instant.now().toEpochMilli() + ".json");
        Files.move(root.resolve("state.json"), target);
        return target;
    }

    public List<ConfigEntry> list() throws IOException {
        List<ConfigEntry> entries = new ArrayList<>();
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(directory, "*.json")) {
            for (Path path : stream) {
                if (!Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)) {
                    continue;
                }
                String filename = path.getFileName().toString();
                String name = filename.substring(0, filename.length() - 5);
                try {
                    if (ConfigName.normalize(name).equals(name)) {
                        entries.add(new ConfigEntry(name, Files.getLastModifiedTime(path, LinkOption.NOFOLLOW_LINKS).toInstant(), Files.size(path)));
                    }
                } catch (IllegalArgumentException ignored) {
                    continue;
                }
            }
        }
        entries.sort(Comparator.comparing(ConfigEntry::name));
        return List.copyOf(entries);
    }

    private Path profilePath(String name) {
        return directory.resolve(ConfigName.normalize(name) + ".json");
    }

    private ConfigSnapshot read(Path path) throws IOException {
        if (Files.isSymbolicLink(path) || !Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)) {
            throw new IOException("Config does not exist or is not a regular file: " + path.getFileName());
        }
        byte[] bytes;
        try (InputStream input = Files.newInputStream(path, LinkOption.NOFOLLOW_LINKS)) {
            bytes = input.readNBytes(MAX_FILE_SIZE + 1);
        }
        if (bytes.length > MAX_FILE_SIZE) {
            throw new ConfigFormatException("Config exceeds the 1 MiB limit");
        }
        String json = StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString();
        return codec.decode(json);
    }

    private void write(Path target, ConfigSnapshot snapshot) throws IOException {
        if (Files.isSymbolicLink(target)) {
            throw new IOException("Refusing to overwrite a symbolic link: " + target.getFileName());
        }
        byte[] data = codec.encode(snapshot).getBytes(StandardCharsets.UTF_8);
        if (data.length > MAX_FILE_SIZE) {
            throw new IOException("Config exceeds the 1 MiB limit");
        }
        Path temporary = Files.createTempFile(target.getParent(), "." + target.getFileName() + "-", ".tmp");
        try {
            try (FileChannel channel = FileChannel.open(temporary, StandardOpenOption.WRITE)) {
                ByteBuffer buffer = ByteBuffer.wrap(data);
                while (buffer.hasRemaining()) {
                    channel.write(buffer);
                }
                channel.force(true);
            }
            try {
                Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException exception) {
                Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(temporary);
        }
    }
}
