package dev.fanzik.client.config;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class ConfigRepositoryTest {
    @TempDir
    Path temporary;
    private ConfigRepository repository;

    @BeforeEach
    void setUp() throws Exception {
        repository = new ConfigRepository(temporary.resolve("config root"), new ConfigCodec("test"));
    }

    @Test
    void savesAndLoadsInPathsContainingSpaces() throws Exception {
        repository.save("Fanzik", ConfigCodecTest.snapshot(true));
        assertEquals(ConfigCodecTest.snapshot(true), repository.load("FANZIK"));
        assertTrue(Files.isRegularFile(repository.directory().resolve("fanzik.json")));
    }

    @Test
    void overwritesAtomicallyAndLeavesNoTemporaryFiles() throws Exception {
        repository.save("test", ConfigCodecTest.snapshot(true));
        repository.save("test", ConfigCodecTest.snapshot(false));
        assertFalse(repository.load("test").modules().get("auto-sprint").enabled());
        try (var files = Files.list(repository.directory())) {
            assertEquals(List.of("test.json"), files.map(path -> path.getFileName().toString()).toList());
        }
    }

    @Test
    void stateIsSeparateFromNamedProfiles() throws Exception {
        assertTrue(repository.loadState().isEmpty());
        repository.saveState(ConfigCodecTest.snapshot(true));
        assertEquals(ConfigCodecTest.snapshot(true), repository.loadState().orElseThrow());
        assertTrue(repository.list().isEmpty());
    }

    @Test
    void listsOnlyValidRegularProfilesInOrder() throws Exception {
        repository.save("z", ConfigCodecTest.snapshot(true));
        repository.save("a", ConfigCodecTest.snapshot(false));
        Files.writeString(repository.directory().resolve("ignored.tmp"), "x");
        Files.writeString(repository.directory().resolve("not valid.json"), "x");
        Files.createDirectory(repository.directory().resolve("directory.json"));
        assertEquals(List.of("a", "z"), repository.list().stream().map(ConfigEntry::name).toList());
        assertTrue(repository.list().getFirst().size() > 0);
    }

    @Test
    void missingAndMalformedFilesFailWithoutChangingAnotherProfile() throws Exception {
        repository.save("good", ConfigCodecTest.snapshot(true));
        Files.writeString(repository.directory().resolve("broken.json"), "{not json");
        assertThrows(IOException.class, () -> repository.load("missing"));
        assertThrows(ConfigFormatException.class, () -> repository.load("broken"));
        assertEquals(ConfigCodecTest.snapshot(true), repository.load("good"));
    }

    @Test
    void limitsInputSizeAndRejectsInvalidUtf8() throws Exception {
        Files.write(repository.directory().resolve("huge.json"), new byte[ConfigRepository.MAX_FILE_SIZE + 1]);
        assertThrows(ConfigFormatException.class, () -> repository.load("huge"));
        Files.write(repository.directory().resolve("bad-utf8.json"), new byte[]{(byte) 0xC3, (byte) 0x28});
        assertThrows(IOException.class, () -> repository.load("bad-utf8"));
    }

    @Test
    void cannotEscapeTheProfileDirectory() {
        assertThrows(IllegalArgumentException.class, () -> repository.save("../outside", ConfigCodecTest.snapshot(true)));
        assertThrows(IllegalArgumentException.class, () -> repository.load("..\\outside"));
        assertFalse(Files.exists(temporary.resolve("outside.json")));
    }

    @Test
    void invalidAutosaveCanBeRetainedForRecovery() throws Exception {
        Path state = repository.directory().getParent().resolve("state.json");
        Files.writeString(state, "broken original");
        Path backup = repository.quarantineState();
        assertEquals("broken original", Files.readString(backup));
        assertFalse(Files.exists(state));
        assertTrue(repository.loadState().isEmpty());
    }

    @Test
    void rejectsSymlinkProfilesOnSupportedSystems() throws Exception {
        Path target = temporary.resolve("outside.json");
        Files.writeString(target, "keep me");
        Path link = repository.directory().resolve("linked.json");
        createSymlinkOrSkip(link, target);
        assertThrows(IOException.class, () -> repository.load("linked"));
        assertThrows(IOException.class, () -> repository.save("linked", ConfigCodecTest.snapshot(true)));
        assertEquals("keep me", Files.readString(target));
        assertTrue(repository.list().isEmpty());
    }

    @Test
    void rejectsASymlinkConfigDirectory() throws Exception {
        Path root = temporary.resolve("other-root");
        Files.createDirectories(root);
        createSymlinkOrSkip(root.resolve("configs"), repository.directory());
        assertThrows(IOException.class, () -> new ConfigRepository(root, new ConfigCodec("test")));
    }

    private void createSymlinkOrSkip(Path link, Path target) throws Exception {
        try {
            Files.createSymbolicLink(link, target);
        } catch (IOException | UnsupportedOperationException | SecurityException exception) {
            assumeTrue(false, "Symlink creation is unavailable: " + exception.getMessage());
        }
    }
}
