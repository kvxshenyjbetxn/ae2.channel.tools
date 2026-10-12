package dev.channeltools.client;

import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;
import net.minecraftforge.client.event.GuiScreenEvent;
import net.minecraftforge.client.event.RenderWorldLastEvent;
import net.minecraftforge.common.util.ForgeDirection;
import net.minecraftforge.event.world.WorldEvent;

import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.util.glu.GLU;

import appeng.api.util.DimensionalCoord;
import appeng.container.AEBaseContainer;
import dev.channeltools.network.AlertMessage;
import net.minecraft.entity.EntityLivingBase;
import dev.channeltools.network.AlertTarget;
import appeng.parts.AEBasePart;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;

/** Independent of the search highlighters: blinking outlines, but continuously visible navigation lines. */
public final class ChannelAlertHighlighter {

    public static final ChannelAlertHighlighter INSTANCE = new ChannelAlertHighlighter();
    private static final long DURATION = TimeUnit.SECONDS.toNanos(15);
    private static final long BLINK_INTERVAL = TimeUnit.MILLISECONDS.toNanos(500);
    private final Map<AlertTarget, Long> targets = new LinkedHashMap<>();
    private final FloatBuffer modelView = BufferUtils.createFloatBuffer(16);
    private final FloatBuffer projection = BufferUtils.createFloatBuffer(16);
    private final IntBuffer viewport = BufferUtils.createIntBuffer(16);
    private final FloatBuffer lineStart = BufferUtils.createFloatBuffer(3);
    private World renderWorld;
    private int dimension;
    private double doubleX, doubleY, doubleZ;

    private ChannelAlertHighlighter() {}

    public static void highlight(List<AlertTarget> newAlertTargets) {
        final World world = Minecraft.getMinecraft().theWorld;
        if (INSTANCE.renderWorld != world) INSTANCE.clear();
        INSTANCE.renderWorld = world;
        final long expiry = System.nanoTime() + DURATION;
        for (AlertTarget target : newAlertTargets) {
            if (world != null && target.location.getDimension() == world.provider.dimensionId) {
                INSTANCE.targets.put(target, expiry);
            }
        }
        while (INSTANCE.targets.size() > AlertMessage.MAX_TARGETS) {
            INSTANCE.targets.remove(INSTANCE.targets.keySet().iterator().next());
        }
    }

    public void clear() {
        targets.clear();
        renderWorld = null;
    }

    @SubscribeEvent
    public void onWorldUnload(WorldEvent.Unload event) {
        if (event.world == renderWorld) clear();
    }

    @SubscribeEvent
    public void onGuiOpened(GuiScreenEvent.InitGuiEvent.Post event) {
        if (targets.isEmpty()) return;
        final Minecraft mc = Minecraft.getMinecraft();
        // Only acknowledge a successfully displayed GUI, not a click or a canceled open request.
        if (event.gui != mc.currentScreen || renderWorld != mc.theWorld) return;
        if (event.gui instanceof GuiContainer gui && gui.inventorySlots instanceof AEBaseContainer container) {
            final Object target = container.getTarget();
            if (target instanceof AEBasePart part) {
                final TileEntity tile = part.getTile();
                if (tile != null && tile.getWorldObj() != null) {
                    targets.remove(new AlertTarget(part.getLocation(), part.getSide(), null));
                }
            } else if (target instanceof TileEntity tile && tile.getWorldObj() != null) {
                targets.remove(new AlertTarget(new DimensionalCoord(tile), ForgeDirection.UNKNOWN, null));
            }
        }
    }

    @SubscribeEvent
    public void renderHighlightedBlocks(RenderWorldLastEvent event) {
        final Minecraft mc = Minecraft.getMinecraft();
        if (renderWorld != mc.theWorld || mc.theWorld == null || mc.renderViewEntity == null) {
            clear();
            return;
        }
        final long now = System.nanoTime();
        targets.values().removeIf(expiry -> now - expiry >= 0);
        if (targets.isEmpty() || mc.gameSettings.hideGUI) return;
        updateCameraState(event);
        beginOutline();
        try {
            GL11.glDisable(GL11.GL_LIGHTING);
            GL11.glDepthMask(false);
            // Unproject the actual viewport centre, so the line starts at the crosshair even with view bobbing/F5.
            modelView.clear();
            projection.clear();
            viewport.clear();
            lineStart.clear();
            GL11.glGetFloat(GL11.GL_MODELVIEW_MATRIX, modelView);
            GL11.glGetFloat(GL11.GL_PROJECTION_MATRIX, projection);
            GL11.glGetInteger(GL11.GL_VIEWPORT, viewport);
            final boolean drawLines = GLU.gluUnProject(
                    viewport.get(0) + viewport.get(2) / 2.0f,
                    viewport.get(1) + viewport.get(3) / 2.0f,
                    0.01f,
                    modelView,
                    projection,
                    viewport,
                    lineStart);
            for (Map.Entry<AlertTarget, Long> entry : targets.entrySet()) {
                final AlertTarget target = entry.getKey();
                if (target.location.getDimension() != dimension) continue;
                if (((entry.getValue() - now) / BLINK_INTERVAL & 1) == 1) {
                    renderHighLightedBlocksOutline(target.location.x, target.location.y, target.location.z);
                }
                if (drawLines) {
                    final Tessellator tess = Tessellator.instance;
                    tess.startDrawing(GL11.GL_LINES);
                    tess.setColorRGBA_F(1.0f, 0.15f, 0.15f, 1.0f);
                    tess.addVertex(lineStart.get(0), lineStart.get(1), lineStart.get(2));
                    tess.addVertex(
                            target.location.x + 0.5 + target.side.offsetX * 0.45,
                            target.location.y + 0.5 + target.side.offsetY * 0.45,
                            target.location.z + 0.5 + target.side.offsetZ * 0.45);
                    tess.draw();
                }
            }
        } finally {
            endOutline();
        }
    }

    protected void updateCameraState(RenderWorldLastEvent event) {
        Minecraft mc = Minecraft.getMinecraft();
        dimension = mc.theWorld.provider.dimensionId;

        EntityLivingBase p = mc.renderViewEntity;
        doubleX = p.lastTickPosX + (p.posX - p.lastTickPosX) * event.partialTicks;
        doubleY = p.lastTickPosY + (p.posY - p.lastTickPosY) * event.partialTicks;
        doubleZ = p.lastTickPosZ + (p.posZ - p.lastTickPosZ) * event.partialTicks;
    }

    protected void beginOutline() {
        GL11.glPushMatrix();
        GL11.glPushAttrib(GL11.GL_ALL_ATTRIB_BITS);
        GL11.glLineWidth(3);
        GL11.glTranslated(-doubleX, -doubleY, -doubleZ);

        GL11.glDisable(GL11.GL_DEPTH_TEST);
        GL11.glDisable(GL11.GL_TEXTURE_2D);
    }

    protected void endOutline() {
        GL11.glPopAttrib();
        GL11.glPopMatrix();
    }

    protected void renderHighLightedBlocksOutline(double x, double y, double z) {
        renderHighLightedBlocksOutline(x, y, z, 1.0f, 0.0f, 0.0f, 1.0f);
    }

    protected void renderHighLightedBlocksOutline(double x, double y, double z, float r, float g, float b, float a) {
        Tessellator tess = Tessellator.instance;
        tess.startDrawing(GL11.GL_LINE_STRIP);

        tess.setColorRGBA_F(r, g, b, a);

        tess.addVertex(x, y, z);
        tess.addVertex(x, y + 1, z);
        tess.addVertex(x, y + 1, z + 1);
        tess.addVertex(x, y, z + 1);
        tess.addVertex(x, y, z);

        tess.addVertex(x + 1, y, z);
        tess.addVertex(x + 1, y + 1, z);
        tess.addVertex(x + 1, y + 1, z + 1);
        tess.addVertex(x + 1, y, z + 1);
        tess.addVertex(x + 1, y, z);

        tess.addVertex(x, y, z);
        tess.addVertex(x + 1, y, z);
        tess.addVertex(x + 1, y, z + 1);
        tess.addVertex(x, y, z + 1);
        tess.addVertex(x, y + 1, z + 1);
        tess.addVertex(x + 1, y + 1, z + 1);
        tess.addVertex(x + 1, y + 1, z);
        tess.addVertex(x + 1, y, z);
        tess.addVertex(x, y, z);
        tess.addVertex(x + 1, y, z);
        tess.addVertex(x + 1, y + 1, z);
        tess.addVertex(x, y + 1, z);
        tess.addVertex(x, y + 1, z + 1);
        tess.addVertex(x + 1, y + 1, z + 1);
        tess.addVertex(x + 1, y, z + 1);
        tess.addVertex(x, y, z + 1);

        tess.draw();
    }

}
