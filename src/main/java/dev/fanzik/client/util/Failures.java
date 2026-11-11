package dev.fanzik.client.util;

import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;

public final class Failures {
    private Failures() {
        throw new AssertionError();
    }

    public static Throwable unwrap(Throwable throwable) {
        Throwable current = throwable;
        while ((current instanceof CompletionException || current instanceof ExecutionException) && current.getCause() != null) {
            current = current.getCause();
        }
        return current;
    }

    public static String describe(Throwable throwable) {
        Throwable cause = unwrap(throwable);
        String message = cause.getMessage();
        return message == null || message.isBlank() ? cause.getClass().getSimpleName() : message;
    }
}
