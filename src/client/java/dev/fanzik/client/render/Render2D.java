package dev.fanzik.client.render;

import dev.fanzik.client.util.Argb;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.text.OrderedText;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.util.List;

public final class Render2D implements AutoCloseable {
    public static final Identifier UI_FONT = Identifier.of("fanzik", "ui");
    private static final Style FONT_STYLE = Style.EMPTY.withFont(UI_FONT);
    private final DrawContext context;
    private final TextRenderer font;
    private double opacity = 1;

    public Render2D(DrawContext context) {
        this.context = context;
        this.font = MinecraftClient.getInstance().textRenderer;
    }

    public DrawContext context() {
        return context;
    }

    public static Text styled(String text) {
        return Text.literal(text).setStyle(FONT_STYLE);
    }

    public float textWidth(String text) {
        return font.getWidth(styled(text));
    }

    public float textWidth(String text, float scale) {
        return textWidth(text) * scale;
    }

    public void rect(float x, float y, float width, float height, int color) {
        gradient(x, y, width, height, color, color);
    }

    public void gradient(float x, float y, float width, float height, int top, int bottom) {
        if (width <= 0 || height <= 0) {
            return;
        }
        quad(x, y, top, x, y + height, bottom, x + width, y + height, bottom, x + width, y, top);
    }

    public void rounded(float x, float y, float width, float height, float radius, int color) {
        roundedGradient(x, y, width, height, radius, color, color);
    }

    public void roundedGradient(float x, float y, float width, float height, float radius, int top, int bottom) {
        if (width <= 0 || height <= 0) {
            return;
        }
        float r = Math.clamp(radius, 0, Math.min(width, height) * 0.5f);
        if (r < 0.25f) {
            gradient(x, y, width, height, top, bottom);
            return;
        }
        float[][] inner = contour(x, y, width, height, r);
        float[][] outer = contour(x - 0.65f, y - 0.65f, width + 1.3f, height + 1.3f, r + 0.65f);
        float centerX = x + width / 2;
        float centerY = y + height / 2;
        int center = Argb.lerp(top, bottom, 0.5);
        for (int i = 0; i < inner.length; i++) {
            int next = (i + 1) % inner.length;
            int firstColor = gradientColor(inner[i][1], y, height, top, bottom);
            int secondColor = gradientColor(inner[next][1], y, height, top, bottom);
            quad(centerX, centerY, center, inner[i][0], inner[i][1], firstColor,
                    inner[next][0], inner[next][1], secondColor, centerX, centerY, center);
            quad(inner[i][0], inner[i][1], firstColor, outer[i][0], outer[i][1], Argb.withAlpha(firstColor, 0),
                    outer[next][0], outer[next][1], Argb.withAlpha(secondColor, 0), inner[next][0], inner[next][1], secondColor);
        }
    }

    public void stroke(float x, float y, float width, float height, float radius, float thickness, int color) {
        if (width <= 0 || height <= 0 || thickness <= 0) {
            return;
        }
        float t = Math.min(thickness, Math.min(width, height) * 0.5f);
        float[][] outer = contour(x, y, width, height, Math.clamp(radius, 0, Math.min(width, height) / 2));
        float[][] inner = contour(x + t, y + t, width - 2 * t, height - 2 * t, Math.max(0, radius - t));
        for (int i = 0; i < outer.length; i++) {
            int next = (i + 1) % outer.length;
            quad(outer[i][0], outer[i][1], color, outer[next][0], outer[next][1], color,
                    inner[next][0], inner[next][1], color, inner[i][0], inner[i][1], color);
        }
    }

    public void shadow(float x, float y, float width, float height, float radius, float blur, int color) {
        int steps = Math.clamp((int) Math.ceil(blur), 4, 16);
        for (int i = steps; i >= 1; i--) {
            float spread = blur * i / steps;
            double strength = 0.1 * (1 - i / (double) (steps + 1));
            rounded(x - spread, y - spread + blur * 0.2f, width + 2 * spread, height + 2 * spread,
                    radius + spread, Argb.alpha(color, strength));
        }
    }

    public void circle(float x, float y, float radius, int color) {
        if (radius <= 0) {
            return;
        }
        int segments = Math.clamp((int) Math.ceil(radius * 3), 16, 96);
        for (int i = 0; i < segments; i++) {
            double a = i * Math.PI * 2 / segments;
            double b = (i + 1) * Math.PI * 2 / segments;
            float ax = x + (float) Math.cos(a) * radius;
            float ay = y + (float) Math.sin(a) * radius;
            float bx = x + (float) Math.cos(b) * radius;
            float by = y + (float) Math.sin(b) * radius;
            quad(x, y, color, ax, ay, color, bx, by, color, x, y, color);
            float outer = radius + 0.65f;
            quad(ax, ay, color, x + (float) Math.cos(a) * outer, y + (float) Math.sin(a) * outer, Argb.withAlpha(color, 0),
                    x + (float) Math.cos(b) * outer, y + (float) Math.sin(b) * outer, Argb.withAlpha(color, 0), bx, by, color);
        }
    }

    public void line(float x1, float y1, float x2, float y2, float thickness, int color) {
        float dx = x2 - x1;
        float dy = y2 - y1;
        float length = (float) Math.hypot(dx, dy);
        if (length < 0.001f || thickness <= 0) {
            return;
        }
        float nx = -dy / length * thickness / 2;
        float ny = dx / length * thickness / 2;
        quad(x1 + nx, y1 + ny, color, x2 + nx, y2 + ny, color,
                x2 - nx, y2 - ny, color, x1 - nx, y1 - ny, color);
    }

    public void arc(float x, float y, float radius, double start, double end, float thickness, int color) {
        int segments = Math.max(8, (int) Math.ceil(Math.abs(end - start) * radius / 3));
        for (int i = 0; i < segments; i++) {
            double a = start + (end - start) * i / segments;
            double b = start + (end - start) * (i + 1) / segments;
            line(x + (float) Math.cos(a) * radius, y + (float) Math.sin(a) * radius,
                    x + (float) Math.cos(b) * radius, y + (float) Math.sin(b) * radius, thickness, color);
        }
    }

    public void text(String text, float x, float y, int color) {
        text(text, x, y, 1, color);
    }

    public void text(String text, float x, float y, float scale, int color) {
        if (text.isEmpty() || scale <= 0 || (color >>> 24) == 0 || opacity <= 0.01) {
            return;
        }
        context.draw();
        try (Scope ignored = transform(x, y, scale)) {
            context.drawText(font, styled(text), 0, 0, Argb.alpha(color, opacity), false);
            context.draw();
        }
    }

    public void centered(String text, float centerX, float y, float scale, int color) {
        text(text, centerX - textWidth(text, scale) / 2, y, scale, color);
    }

    public float paragraph(String text, float x, float y, float width, float lineHeight, int color) {
        List<OrderedText> lines = font.wrapLines(styled(text), Math.max(1, (int) width));
        context.draw();
        int index = 0;
        for (OrderedText line : lines) {
            context.drawText(font, line, Math.round(x), Math.round(y + index * lineHeight), Argb.alpha(color, opacity), false);
            index++;
        }
        context.draw();
        return lines.size() * lineHeight;
    }

    public String elide(String text, float width) {
        if (textWidth(text) <= width) {
            return text;
        }
        String suffix = "…";
        int end = text.length();
        while (end > 0 && textWidth(text.substring(0, end) + suffix) > width) {
            end = text.offsetByCodePoints(end, -1);
        }
        return end == 0 && textWidth(suffix) > width ? "" : text.substring(0, end) + suffix;
    }

    public Scope transform(float x, float y, float scale) {
        context.getMatrices().push();
        context.getMatrices().translate(x, y, 0);
        context.getMatrices().scale(scale, scale, 1);
        return () -> context.getMatrices().pop();
    }

    public Scope opacity(double value) {
        double previous = opacity;
        opacity *= Math.clamp(value, 0, 1);
        return () -> opacity = previous;
    }

    public Scope scissor(float x, float y, float width, float height) {
        Matrix4f matrix = context.getMatrices().peek().getPositionMatrix();
        Vector3f start = matrix.transformPosition(new Vector3f(x, y, 0));
        Vector3f end = matrix.transformPosition(new Vector3f(x + Math.max(0, width), y + Math.max(0, height), 0));
        context.draw();
        context.enableScissor((int) Math.floor(start.x), (int) Math.floor(start.y),
                (int) Math.ceil(end.x), (int) Math.ceil(end.y));
        return () -> {
            context.draw();
            context.disableScissor();
        };
    }

    private void quad(float x1, float y1, int c1, float x2, float y2, int c2,
                      float x3, float y3, int c3, float x4, float y4, int c4) {
        Matrix4f matrix = context.getMatrices().peek().getPositionMatrix();
        VertexConsumer vertices = context.getVertexConsumers().getBuffer(RenderLayer.getGui());
        vertices.vertex(matrix, x1, y1, 0).color(Argb.alpha(c1, opacity));
        if ((x2 - x1) * (y3 - y1) - (y2 - y1) * (x3 - x1) > 0) {
            vertices.vertex(matrix, x4, y4, 0).color(Argb.alpha(c4, opacity));
            vertices.vertex(matrix, x3, y3, 0).color(Argb.alpha(c3, opacity));
            vertices.vertex(matrix, x2, y2, 0).color(Argb.alpha(c2, opacity));
        } else {
            vertices.vertex(matrix, x2, y2, 0).color(Argb.alpha(c2, opacity));
            vertices.vertex(matrix, x3, y3, 0).color(Argb.alpha(c3, opacity));
            vertices.vertex(matrix, x4, y4, 0).color(Argb.alpha(c4, opacity));
        }
    }

    private static int gradientColor(float y, float topY, float height, int top, int bottom) {
        return Argb.lerp(top, bottom, (y - topY) / height);
    }

    private static float[][] contour(float x, float y, float width, float height, float radius) {
        float r = Math.clamp(radius, 0, Math.min(width, height) / 2);
        float[][] centers = {{x + width - r, y + r}, {x + width - r, y + height - r},
                {x + r, y + height - r}, {x + r, y + r}};
        float[][] points = new float[36][2];
        for (int corner = 0; corner < 4; corner++) {
            for (int segment = 0; segment <= 8; segment++) {
                double angle = (-90 + corner * 90 + segment * 90.0 / 8) * Math.PI / 180;
                int index = corner * 9 + segment;
                points[index][0] = centers[corner][0] + (float) Math.cos(angle) * r;
                points[index][1] = centers[corner][1] + (float) Math.sin(angle) * r;
            }
        }
        return points;
    }

    @Override
    public void close() {
        context.draw();
    }

    @FunctionalInterface
    public interface Scope extends AutoCloseable {
        @Override
        void close();
    }
}
