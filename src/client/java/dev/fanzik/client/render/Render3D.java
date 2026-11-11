package dev.fanzik.client.render;

import com.mojang.blaze3d.systems.RenderSystem;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.BufferRenderer;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.render.VertexFormat;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.lwjgl.opengl.GL11;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Objects;

public final class Render3D implements AutoCloseable {
    private static final int MAX_QUADS = 65536;
    private final WorldRenderContext context;
    private final Matrix4f matrix;
    private final Vec3d camera;
    private final EnumMap<DepthMode, List<Quad>> batches = new EnumMap<>(DepthMode.class);
    private int quadCount;
    private boolean closed;

    public Render3D(WorldRenderContext context) {
        this.context = context;
        this.matrix = new Matrix4f(Objects.requireNonNull(context.matrixStack(), "World matrices are not ready")
                .peek().getPositionMatrix());
        this.camera = context.camera().getPos();
        for (DepthMode depthMode : DepthMode.values()) {
            batches.put(depthMode, new ArrayList<>());
        }
    }

    public WorldRenderContext context() {
        return context;
    }

    public Vec3d cameraPosition() {
        return camera;
    }

    public void quad(Vec3d a, Vec3d b, Vec3d c, Vec3d d, int color, DepthMode depth) {
        requireOpen();
        Objects.requireNonNull(depth);
        requireFinite(a);
        requireFinite(b);
        requireFinite(c);
        requireFinite(d);
        if ((color >>> 24) == 0) {
            return;
        }
        if (++quadCount > MAX_QUADS) {
            throw new IllegalStateException("World render vertex budget exceeded");
        }
        batches.get(depth).add(new Quad(a, b, c, d, color));
    }

    public void line(Vec3d from, Vec3d to, double width, int color, DepthMode depth) {
        requireOpen();
        requireFinite(from);
        requireFinite(to);
        if (!Double.isFinite(width) || width <= 0 || width > 16) {
            throw new IllegalArgumentException("World line width must be in (0, 16]");
        }
        Vec3d direction = to.subtract(from);
        if (direction.lengthSquared() < 1.0e-12) {
            return;
        }
        Vec3d midpoint = from.add(to).multiply(0.5);
        Vec3d side = direction.crossProduct(camera.subtract(midpoint));
        if (side.lengthSquared() < 1.0e-12) {
            side = direction.crossProduct(new Vec3d(0, 1, 0));
        }
        if (side.lengthSquared() < 1.0e-12) {
            side = direction.crossProduct(new Vec3d(1, 0, 0));
        }
        side = side.normalize().multiply(width * 0.5);
        quad(from.add(side), from.subtract(side), to.subtract(side), to.add(side), color, depth);
    }

    public void polyline(List<Vec3d> points, boolean loop, double width, int color, DepthMode depth) {
        for (int i = 1; i < points.size(); i++) {
            line(points.get(i - 1), points.get(i), width, color, depth);
        }
        if (loop && points.size() > 2) {
            line(points.getLast(), points.getFirst(), width, color, depth);
        }
    }

    public void filledBox(Box box, int color, DepthMode depth) {
        Vec3d[] p = corners(box);
        quad(p[0], p[1], p[2], p[3], color, depth);
        quad(p[4], p[7], p[6], p[5], color, depth);
        quad(p[0], p[4], p[5], p[1], color, depth);
        quad(p[3], p[2], p[6], p[7], color, depth);
        quad(p[0], p[3], p[7], p[4], color, depth);
        quad(p[1], p[5], p[6], p[2], color, depth);
    }

    public void wireBox(Box box, double width, int color, DepthMode depth) {
        Vec3d[] points = corners(box);
        int[][] edges = {{0, 1}, {1, 2}, {2, 3}, {3, 0}, {4, 5}, {5, 6}, {6, 7}, {7, 4}, {0, 4}, {1, 5}, {2, 6}, {3, 7}};
        for (int[] edge : edges) {
            line(points[edge[0]], points[edge[1]], width, color, depth);
        }
    }

    public void box(Box box, double lineWidth, int fill, int outline, DepthMode depth) {
        filledBox(box, fill, depth);
        wireBox(box, lineWidth, outline, depth);
    }

    public void circle(Vec3d center, double radius, int segments, double width, int color, DepthMode depth) {
        requireFinite(center);
        if (!Double.isFinite(radius) || radius <= 0 || segments < 3 || segments > 512) {
            throw new IllegalArgumentException("Invalid world circle dimensions");
        }
        List<Vec3d> points = new ArrayList<>(segments);
        for (int i = 0; i < segments; i++) {
            double angle = i * Math.PI * 2 / segments;
            points.add(center.add(Math.cos(angle) * radius, 0, Math.sin(angle) * radius));
        }
        polyline(points, true, width, color, depth);
    }

    public void billboard(Vec3d center, float width, float height, int color, DepthMode depth) {
        if (!Float.isFinite(width) || !Float.isFinite(height) || width <= 0 || height <= 0) {
            throw new IllegalArgumentException("Invalid billboard dimensions");
        }
        Vector3f rightVector = new Vector3f(1, 0, 0).rotate(context.camera().getRotation()).mul(width / 2);
        Vector3f upVector = new Vector3f(0, 1, 0).rotate(context.camera().getRotation()).mul(height / 2);
        Vec3d right = new Vec3d(rightVector.x, rightVector.y, rightVector.z);
        Vec3d up = new Vec3d(upVector.x, upVector.y, upVector.z);
        quad(center.subtract(right).subtract(up), center.add(right).subtract(up),
                center.add(right).add(up), center.subtract(right).add(up), color, depth);
    }

    private static Vec3d[] corners(Box b) {
        return new Vec3d[]{new Vec3d(b.minX, b.minY, b.minZ), new Vec3d(b.maxX, b.minY, b.minZ),
                new Vec3d(b.maxX, b.minY, b.maxZ), new Vec3d(b.minX, b.minY, b.maxZ),
                new Vec3d(b.minX, b.maxY, b.minZ), new Vec3d(b.maxX, b.maxY, b.minZ),
                new Vec3d(b.maxX, b.maxY, b.maxZ), new Vec3d(b.minX, b.maxY, b.maxZ)};
    }

    private static void requireFinite(Vec3d position) {
        Objects.requireNonNull(position);
        if (!Double.isFinite(position.x) || !Double.isFinite(position.y) || !Double.isFinite(position.z)) {
            throw new IllegalArgumentException("World coordinates must be finite");
        }
    }

    private void requireOpen() {
        if (closed) {
            throw new IllegalStateException("Renderer is already closed");
        }
    }

    private void vertex(BufferBuilder buffer, Vec3d position, int color) {
        buffer.vertex(matrix, (float) (position.x - camera.x), (float) (position.y - camera.y), (float) (position.z - camera.z))
                .color(color);
    }

    @Override
    public void close() {
        if (closed) {
            return;
        }
        closed = true;
        if (quadCount == 0) {
            return;
        }
        try (RenderState ignored = new RenderState()) {
            RenderSystem.enableBlend();
            RenderSystem.defaultBlendFunc();
            RenderSystem.disableCull();
            RenderSystem.depthMask(false);
            RenderSystem.setShaderColor(1, 1, 1, 1);
            RenderSystem.setShader(GameRenderer::getPositionColorProgram);
            for (DepthMode depthMode : DepthMode.values()) {
                List<Quad> quads = batches.get(depthMode);
                if (quads.isEmpty()) {
                    continue;
                }
                if (depthMode == DepthMode.TESTED) {
                    RenderSystem.enableDepthTest();
                    RenderSystem.depthFunc(GL11.GL_LEQUAL);
                } else {
                    RenderSystem.disableDepthTest();
                }
                BufferBuilder buffer = Tessellator.getInstance().begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_COLOR);
                for (Quad quad : quads) {
                    vertex(buffer, quad.a, quad.color);
                    vertex(buffer, quad.b, quad.color);
                    vertex(buffer, quad.c, quad.color);
                    vertex(buffer, quad.d, quad.color);
                }
                BufferRenderer.drawWithGlobalProgram(buffer.end());
                quads.clear();
            }
        }
    }

    public enum DepthMode {
        TESTED, THROUGH_WALLS
    }

    private record Quad(Vec3d a, Vec3d b, Vec3d c, Vec3d d, int color) {
    }
}
