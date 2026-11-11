package dev.fanzik.client.command;

import java.util.ArrayList;
import java.util.List;

public final class CommandTokenizer {
    private CommandTokenizer() {
        throw new AssertionError();
    }

    public static List<String> tokenize(String input) {
        if (input == null || input.length() > 512) {
            throw new IllegalArgumentException("Command length must not exceed 512 characters");
        }
        List<String> tokens = new ArrayList<>();
        StringBuilder token = new StringBuilder();
        char quote = 0;
        boolean escaped = false;
        boolean started = false;
        for (int i = 0; i < input.length(); i++) {
            char character = input.charAt(i);
            if (escaped) {
                token.append(character);
                escaped = false;
                started = true;
            } else if (character == '\\') {
                escaped = true;
                started = true;
            } else if (quote != 0) {
                if (character == quote) {
                    quote = 0;
                } else {
                    token.append(character);
                }
            } else if (character == '"' || character == '\'') {
                quote = character;
                started = true;
            } else if (Character.isWhitespace(character)) {
                if (started) {
                    tokens.add(token.toString());
                    token.setLength(0);
                    started = false;
                }
            } else {
                token.append(character);
                started = true;
            }
            if (tokens.size() > 32) {
                throw new IllegalArgumentException("Too many command arguments");
            }
        }
        if (quote != 0 || escaped) {
            throw new IllegalArgumentException("Unclosed quote or unfinished escape sequence");
        }
        if (started) {
            tokens.add(token.toString());
        }
        if (tokens.size() > 32) {
            throw new IllegalArgumentException("Too many command arguments");
        }
        return List.copyOf(tokens);
    }
}
