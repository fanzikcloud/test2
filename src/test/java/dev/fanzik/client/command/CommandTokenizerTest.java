package dev.fanzik.client.command;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class CommandTokenizerTest {
    @Test
    void splitsWhitespaceAndPreservesQuotedArguments() {
        assertEquals(List.of("cfg", "save", "fanzik"), CommandTokenizer.tokenize("  cfg\t save  fanzik  "));
        assertEquals(List.of("one", "two words", "three words"), CommandTokenizer.tokenize("one \"two words\" 'three words'"));
        assertEquals(List.of("", "ab"), CommandTokenizer.tokenize("\"\" a\"b\""));
        assertTrue(CommandTokenizer.tokenize(" ").isEmpty());
    }

    @Test
    void handlesEscapes() {
        assertEquals(List.of("a\"b", "c\\d", "a b"), CommandTokenizer.tokenize("\"a\\\"b\" c\\\\d a\\ b"));
    }

    @Test
    void rejectsUnfinishedQuotesAndEscapes() {
        assertThrows(IllegalArgumentException.class, () -> CommandTokenizer.tokenize("'not closed"));
        assertThrows(IllegalArgumentException.class, () -> CommandTokenizer.tokenize("unfinished\\"));
    }

    @Test
    void boundsArgumentsAndInputLength() {
        assertThrows(IllegalArgumentException.class, () -> CommandTokenizer.tokenize("x".repeat(513)));
        assertThrows(IllegalArgumentException.class, () -> CommandTokenizer.tokenize("x ".repeat(33)));
        assertThrows(IllegalArgumentException.class, () -> CommandTokenizer.tokenize(null));
        assertEquals(32, CommandTokenizer.tokenize("x ".repeat(32)).size());
    }
}
