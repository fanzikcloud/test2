package dev.fanzik.client.render;

import dev.fanzik.client.module.Category;

public final class Icons {
    private Icons() {
        throw new AssertionError();
    }

    public static void category(Render2D r, Category category, float x, float y, float size, int color) {
        try (Render2D.Scope ignored = r.transform(x, y, size / 24)) {
            switch (category) {
                case COMBAT -> {
                    path(r, color, 5, 4, 18, 17, 17, 19, 4, 6, 5, 4);
                    path(r, color, 19, 4, 6, 17, 7, 19, 20, 6, 19, 4);
                    r.line(3, 16, 8, 21, 1.6f, color);
                    r.line(16, 21, 21, 16, 1.6f, color);
                }
                case MOVEMENT -> bolt(r, 0, 0, 24, color);
                case RENDER -> {
                    path(r, color, 2, 12, 6, 7, 12, 5, 18, 7, 22, 12, 18, 17, 12, 19, 6, 17, 2, 12);
                    r.arc(12, 12, 3, 0, Math.PI * 2, 1.7f, color);
                }
                case PLAYER -> {
                    r.arc(12, 7, 4, 0, Math.PI * 2, 1.7f, color);
                    r.arc(12, 20, 7, Math.PI, Math.PI * 2, 1.7f, color);
                    r.line(5, 20, 19, 20, 1.7f, color);
                }
                case MISC -> {
                    for (int i = 0; i < 2; i++) {
                        for (int j = 0; j < 2; j++) {
                            r.stroke(3 + i * 11, 3 + j * 11, 7, 7, 2, 1.5f, color);
                        }
                    }
                }
            }
        }
    }

    public static void bolt(Render2D r, float x, float y, float size, int color) {
        try (Render2D.Scope ignored = r.transform(x, y, size / 24)) {
            path(r, color, 14, 2, 5, 14, 11, 14, 10, 22, 20, 10, 13, 10, 14, 2);
        }
    }

    public static void search(Render2D r, float x, float y, int color) {
        r.arc(x + 6, y + 6, 4.5f, 0, Math.PI * 2, 1.4f, color);
        r.line(x + 9.5f, y + 9.5f, x + 13, y + 13, 1.5f, color);
    }

    public static void folder(Render2D r, float x, float y, int color) {
        path(r, color, x, y + 3, x + 5, y + 3, x + 7, y + 5, x + 15, y + 5,
                x + 15, y + 14, x, y + 14, x, y + 3);
    }

    public static void close(Render2D r, float x, float y, int color) {
        r.line(x + 2, y + 2, x + 10, y + 10, 1.5f, color);
        r.line(x + 10, y + 2, x + 2, y + 10, 1.5f, color);
    }

    public static void chevron(Render2D r, float x, float y, int color) {
        path(r, color, x, y, x + 4, y + 4, x + 8, y);
    }

    public static void check(Render2D r, float x, float y, int color) {
        path(r, color, x, y + 4, x + 3, y + 7, x + 9, y);
    }

    public static void logo(Render2D r, float x, float y, float size, int color) {
        try (Render2D.Scope ignored = r.transform(x, y, size / 24)) {
            r.line(6, 19, 6, 5, 2.6f, color);
            r.line(6, 5, 18, 5, 2.6f, color);
            r.line(6, 11, 15, 11, 2.6f, color);
            r.line(15, 15, 18, 18, 2.6f, color);
        }
    }

    private static void path(Render2D r, int color, float... points) {
        for (int i = 2; i < points.length; i += 2) {
            r.line(points[i - 2], points[i - 1], points[i], points[i + 1], 1.6f, color);
        }
    }
}
