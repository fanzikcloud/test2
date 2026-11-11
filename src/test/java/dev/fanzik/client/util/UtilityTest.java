package dev.fanzik.client.util;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;

import static org.junit.jupiter.api.Assertions.*;

class UtilityTest {
    @Test
    void colorOperationsKeepTheCorrectChannels() {
        assertEquals(0x80ABCDEF, Argb.alpha(0xFFABCDEF, 0.5));
        assertEquals(0xFFABCDEF, Argb.alpha(0xFFABCDEF, 4));
        assertEquals(0x00ABCDEF, Argb.alpha(0xFFABCDEF, -1));
        assertEquals(0x12888888, Argb.withAlpha(0xFF888888, 0x12));
        assertEquals(0xFF808080, Argb.lerp(0xFF000000, 0xFFFFFFFF, 0.5));
        assertEquals(0xFF000000, Argb.lerp(0xFF000000, 0xFFFFFFFF, -1));
    }

    @Test
    void windowsPathsAreSingleProcessArgumentsNotShellStrings() {
        Path path = Path.of("directory with spaces & symbols").toAbsolutePath();
        assertEquals(List.of(List.of("explorer.exe", path.toString())), DirectoryOpener.commandsFor("Windows 11", path));
    }

    @Test
    void linuxHasAnXdgAndGioFallback() {
        Path path = Path.of("configs").toAbsolutePath();
        assertEquals(List.of(List.of("xdg-open", path.toString()), List.of("gio", "open", path.toString())), DirectoryOpener.commandsFor("Linux", path));
        assertTrue(DirectoryOpener.commandsFor("unknown", path).isEmpty());
    }

    @Test
    void asyncErrorsAreUnwrappedWithoutLosingTheCause() {
        IllegalArgumentException cause = new IllegalArgumentException("bad profile");
        assertSame(cause, Failures.unwrap(new CompletionException(new ExecutionException(cause))));
        assertEquals("bad profile", Failures.describe(new CompletionException(cause)));
        assertEquals("IllegalStateException", Failures.describe(new IllegalStateException()));
    }
}
