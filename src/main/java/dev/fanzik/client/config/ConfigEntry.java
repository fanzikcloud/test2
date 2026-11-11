package dev.fanzik.client.config;

import java.time.Instant;

public record ConfigEntry(String name, Instant modifiedAt, long size) {
}
