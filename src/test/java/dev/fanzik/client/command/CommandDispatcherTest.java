package dev.fanzik.client.command;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class CommandDispatcherTest {
    private final List<CommandDispatcher.Failure> errors = new ArrayList<>();
    private final CommandDispatcher dispatcher = new CommandDispatcher(errors::add);

    @Test
    void regularChatAndSlashCommandsAreUntouched() {
        assertFalse(dispatcher.dispatch("hello"));
        assertFalse(dispatcher.dispatch("/help"));
        assertFalse(dispatcher.dispatch(null));
        assertTrue(errors.isEmpty());
    }

    @Test
    void dotCommandsAreConsumedEvenWhenUnknownOrMalformed() {
        assertTrue(dispatcher.dispatch(".unknown"));
        assertEquals(CommandDispatcher.FailureType.UNKNOWN, errors.getLast().type());
        assertTrue(dispatcher.dispatch(".cfg \"unclosed"));
        assertEquals(CommandDispatcher.FailureType.INPUT, errors.getLast().type());
    }

    @Test
    void dispatchesCaseInsensitivelyAndSupportsAliases() {
        List<List<String>> calls = new ArrayList<>();
        dispatcher.register(new ClientCommand("cfg", ".cfg", "d", List.of("config"), calls::add, args -> List.of()));
        assertTrue(dispatcher.dispatch(".CFG save fanzik"));
        assertTrue(dispatcher.dispatch(".config load \"fanzik\""));
        assertEquals(List.of(List.of("save", "fanzik"), List.of("load", "fanzik")), calls);
    }

    @Test
    void emptyDotInvokesHelp() {
        AtomicInteger calls = new AtomicInteger();
        dispatcher.register(new ClientCommand("help", ".help", "d", List.of(), args -> calls.incrementAndGet(), args -> List.of()));
        assertTrue(dispatcher.dispatch("."));
        assertEquals(1, calls.get());
    }

    @Test
    void reportsHandlerFailuresAndKeepsFutureCommandsWorking() {
        dispatcher.register(new ClientCommand("bad", ".bad", "d", List.of(), args -> { throw new IllegalStateException("boom"); }, args -> List.of()));
        dispatcher.dispatch(".bad");
        assertEquals(CommandDispatcher.FailureType.EXECUTION, errors.getFirst().type());
        assertEquals("boom", errors.getFirst().message());
        assertTrue(dispatcher.dispatch(".unknown"));
    }

    @Test
    void rejectsDuplicateNamesAndAliasesWithoutPartialRegistration() {
        ClientCommand first = new ClientCommand("one", ".one", "d", List.of("alias"), args -> {}, args -> List.of());
        dispatcher.register(first);
        assertThrows(IllegalArgumentException.class, () -> dispatcher.register(new ClientCommand("two", ".two", "d", List.of("alias"), args -> {}, args -> List.of())));
        assertEquals(1, dispatcher.commands().size());
        assertThrows(UnsupportedOperationException.class, () -> dispatcher.commands().clear());
    }

    @Test
    void tabCompletionReplacesOnlyTheCurrentToken() {
        dispatcher.register(new ClientCommand("cfg", ".cfg", "d", List.of(), args -> {},
                args -> args.size() == 1 ? List.of("save", "load", "list", "dir") : List.of("fanzik", "other")));
        assertEquals(List.of(".cfg"), dispatcher.suggest(".c"));
        assertEquals(List.of(".cfg list", ".cfg load"), dispatcher.suggest(".cfg l"));
        assertEquals(List.of(".cfg load fanzik"), dispatcher.suggest(".cfg load f"));
        assertTrue(dispatcher.suggest("hello").isEmpty());
        assertTrue(dispatcher.suggest(".missing ").isEmpty());
    }
}
