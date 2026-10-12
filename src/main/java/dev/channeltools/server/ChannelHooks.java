package dev.channeltools.server;

import java.util.ArrayDeque;
import java.util.Collections;
import java.util.Deque;
import java.util.HashSet;
import java.util.Set;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;
import net.minecraftforge.common.util.ForgeDirection;
import appeng.api.networking.GridFlags;
import appeng.api.networking.IGrid;
import appeng.api.networking.IGridNode;
import appeng.api.parts.IPartHost;
import appeng.core.AEConfig;
import appeng.core.features.AEFeature;
import appeng.me.GridNode;
import appeng.me.helpers.IGridProxyable;
import dev.channeltools.ChannelTools;

/** Synchronous callbacks around AE2's real allocation pass, rather than polling node activeness. */
public final class ChannelHooks {
    private static final ThreadLocal<Deque<Context>> CONTEXTS = ThreadLocal.withInitial(ArrayDeque::new);
    private ChannelHooks() { }

    public static void blockPlaced(boolean success, EntityPlayer player, World world, int x, int y, int z) {
        if (!success || !ChannelTools.alertsEnabled || world.isRemote) return;
        TileEntity tile = world.getTileEntity(x, y, z);
        if (tile instanceof IGridProxyable) ChannelAlertTracker.recordPlacement(player, world, (IGridProxyable) tile);
    }
    public static ForgeDirection addPart(IPartHost host, ItemStack held, ForgeDirection side, EntityPlayer player) {
        ForgeDirection placed = host.addPart(held, side, player);
        if (placed != null && ChannelTools.alertsEnabled && host.getTile() != null) {
            Object part = host.getPart(placed);
            if (part instanceof IGridProxyable) ChannelAlertTracker.recordPlacement(player, host.getTile().getWorldObj(), (IGridProxyable) part);
        }
        return placed;
    }
    public static void begin(IGrid grid) {
        if (!ChannelTools.alertsEnabled) return;
        EntityPlayerMP player = ChannelAlertTracker.takePlacement(grid);
        // Push even with no player, so a nested recalculation cannot contribute failures to its parent.
        CONTEXTS.get().push(new Context(grid, player));
    }
    public static void allocationResult(boolean success, GridNode node) {
        Context ctx = CONTEXTS.get().peek();
        if (success || ctx == null || ctx.player == null) return;
        if (node.hasFlag(GridFlags.COMPRESSED_CHANNEL) && !node.getSubtreeAllowsCompressedChannels()) return;
        ctx.rejected.add(node);
    }
    public static void adHocResult(int result, IGrid grid) {
        Context ctx = CONTEXTS.get().peek();
        if (result != 0 || ctx == null || ctx.grid != grid || ctx.player == null
                || !AEConfig.instance.isFeatureEnabled(AEFeature.Channels)) return;
        Set<IGridNode> nodes = new HashSet<>();
        boolean disallowsCompressed = false;
        for (IGridNode node : grid.getNodes()) {
            if (node.hasFlag(GridFlags.REQUIRE_CHANNEL)) nodes.add(node);
            if (node.hasFlag(GridFlags.CANNOT_CARRY_COMPRESSED)) disallowsCompressed = true;
        }
        if (adHocCapacityExceeded(nodes, disallowsCompressed)) ctx.rejected.addAll(nodes);
    }
    static boolean adHocCapacityExceeded(Iterable<IGridNode> nodes, boolean disallowsCompressed) {
        return NetworkChannelAvailability.countAdHocChannels(nodes, disallowsCompressed) > 8;
    }
    public static void finish(IGrid grid) {
        Deque<Context> stack = CONTEXTS.get();
        Context ctx = stack.peek();
        if (ctx == null || ctx.grid != grid) return;
        stack.pop();
        if (ctx.player != null && !grid.isEmpty()) ChannelAlertTracker.notifyPlayer(ctx.player,
                ChannelAlertTracker.findLostChannels(ctx.before, ctx.rejected, grid));
        if (stack.isEmpty()) CONTEXTS.remove();
    }
    /** Also runs at server-tick end to discard interrupted/exceptional passes without sending a false alert. */
    public static void clear() { CONTEXTS.remove(); }
    private static final class Context {
        final IGrid grid;
        final EntityPlayerMP player;
        final Set<IGridNode> before;
        final Set<IGridNode> rejected = new HashSet<>();
        Context(IGrid grid, EntityPlayerMP player) {
            this.grid = grid; this.player = player;
            before = player == null ? Collections.emptySet() : ChannelAlertTracker.snapshot(grid.getNodes());
        }
    }
}
