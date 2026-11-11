package dev.fanzik.client.config;

import java.io.IOException;

public final class ConfigFormatException extends IOException {
    public ConfigFormatException(String message) {
        super(message);
    }

    public ConfigFormatException(String message, Throwable cause) {
        super(message, cause);
    }
}
