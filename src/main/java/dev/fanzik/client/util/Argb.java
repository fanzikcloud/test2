package dev.fanzik.client.util;

public final class Argb {
    private Argb() {
        throw new AssertionError();
    }

    public static int alpha(int color, double opacity) {
        int alpha = (int) Math.round((color >>> 24) * Math.clamp(opacity, 0, 1));
        return (color & 0x00FFFFFF) | alpha << 24;
    }

    public static int withAlpha(int color, int alpha) {
        return (color & 0x00FFFFFF) | Math.clamp(alpha, 0, 255) << 24;
    }

    public static int lerp(int from, int to, double progress) {
        double t = Math.clamp(progress, 0, 1);
        int a = channel(from, to, 24, t);
        int r = channel(from, to, 16, t);
        int g = channel(from, to, 8, t);
        int b = channel(from, to, 0, t);
        return a << 24 | r << 16 | g << 8 | b;
    }

    private static int channel(int from, int to, int shift, double t) {
        int a = (from >>> shift) & 255;
        int b = (to >>> shift) & 255;
        return (int) Math.round(a + (b - a) * t);
    }
}
