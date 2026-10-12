package dev.channeltools.client;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import net.minecraft.client.Minecraft;
import net.minecraft.item.ItemStack;
import net.minecraft.util.StatCollector;
import net.minecraft.world.World;
import net.minecraftforge.event.entity.player.ItemTooltipEvent;
import net.minecraftforge.event.world.WorldEvent;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import appeng.api.util.DimensionalCoord;
import appeng.items.tools.ToolNetworkVisualiser;
import appeng.items.tools.ToolNetworkVisualiser.VLink;
import appeng.items.tools.ToolNetworkVisualiser.VLinkFlags;
import dev.channeltools.ChannelTools;
import dev.channeltools.network.LinkKey;
import dev.channeltools.network.LinksMessage;

public final class ChannelLabels {
    public static boolean showFree = true;
    private static Map<LinkKey, Integer> links = Collections.emptyMap();
    private static DimensionalCoord bound;
    private static World snapshotWorld;
    private static long expires;
    public static void accept(LinksMessage message) {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.theWorld == null || message.bound.getDimension() != mc.theWorld.provider.dimensionId) return;
        links = message.links; bound = message.bound; snapshotWorld = mc.theWorld;
        expires = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
    }
    private static boolean enabled() { return showFree && ChannelTools.freeChannelsEnabled; }
    public static int visible(VLink link) {
        if (!enabled()) return link.channels;
        return free(link) >= 0 ? 1 : 0;
    }
    public static int count(VLink link) { return enabled() ? free(link) : link.channels; }
    private static int free(VLink link) {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.thePlayer == null || snapshotWorld != mc.theWorld || bound == null || System.nanoTime() - expires >= 0
                || link.flags.contains(VLinkFlags.PROXY)) return -1;
        ItemStack held = mc.thePlayer.inventory.getCurrentItem();
        if (held == null || !(held.getItem() instanceof ToolNetworkVisualiser) || !held.hasTagCompound()
                || !held.getTagCompound().hasKey("dim") || !bound.equals(DimensionalCoord.readFromNBT(held.getTagCompound()))) return -1;
        Integer free = links.get(new LinkKey(link.node1.x, link.node1.y, link.node1.z, link.node2.x, link.node2.y, link.node2.z));
        return free == null ? -1 : free;
    }
    @SubscribeEvent public void unload(WorldEvent.Unload event) {
        if (event.world == snapshotWorld) { links = Collections.emptyMap(); bound = null; snapshotWorld = null; }
    }
    @SubscribeEvent public void tooltip(ItemTooltipEvent event) {
        if (event.itemStack.getItem() instanceof ToolNetworkVisualiser && ChannelTools.freeChannelsEnabled) {
            event.toolTip.add(StatCollector.translateToLocal(showFree ? "tooltip.ae2channeltools.free" : "tooltip.ae2channeltools.used"));
            event.toolTip.add(StatCollector.translateToLocal("tooltip.ae2channeltools.toggle"));
        }
    }
}
