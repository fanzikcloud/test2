package dev.fanzik.client.render;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.gl.ShaderProgram;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL14;

final class RenderState implements AutoCloseable {
    private final boolean blend = GL11.glIsEnabled(GL11.GL_BLEND);
    private final boolean depth = GL11.glIsEnabled(GL11.GL_DEPTH_TEST);
    private final boolean cull = GL11.glIsEnabled(GL11.GL_CULL_FACE);
    private final boolean depthMask = GL11.glGetBoolean(GL11.GL_DEPTH_WRITEMASK);
    private final int depthFunction = GL11.glGetInteger(GL11.GL_DEPTH_FUNC);
    private final int sourceRgb = GL11.glGetInteger(GL14.GL_BLEND_SRC_RGB);
    private final int destinationRgb = GL11.glGetInteger(GL14.GL_BLEND_DST_RGB);
    private final int sourceAlpha = GL11.glGetInteger(GL14.GL_BLEND_SRC_ALPHA);
    private final int destinationAlpha = GL11.glGetInteger(GL14.GL_BLEND_DST_ALPHA);
    private final ShaderProgram shader = RenderSystem.getShader();
    private final float[] color = RenderSystem.getShaderColor().clone();

    @Override
    public void close() {
        RenderSystem.depthMask(depthMask);
        RenderSystem.depthFunc(depthFunction);
        if (depth) {
            RenderSystem.enableDepthTest();
        } else {
            RenderSystem.disableDepthTest();
        }
        if (cull) {
            RenderSystem.enableCull();
        } else {
            RenderSystem.disableCull();
        }
        RenderSystem.blendFuncSeparate(sourceRgb, destinationRgb, sourceAlpha, destinationAlpha);
        if (blend) {
            RenderSystem.enableBlend();
        } else {
            RenderSystem.disableBlend();
        }
        RenderSystem.setShader(() -> shader);
        RenderSystem.setShaderColor(color[0], color[1], color[2], color[3]);
    }
}
