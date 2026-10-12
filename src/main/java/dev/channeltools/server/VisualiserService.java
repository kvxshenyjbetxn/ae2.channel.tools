package dev.channeltools.server;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.server.MinecraftServer;
import net.minecraft.tileentity.TileEntity;
import net.minecraftforge.common.util.ForgeDirection;
import appeng.api.config.SecurityPermissions;
import appeng.api.networking.IGrid;
import appeng.api.networking.IGridBlock;
import appeng.api.networking.IGridConnection;
import appeng.api.networking.IGridHost;
import appeng.api.networking.IGridNode;
import appeng.api.networking.pathing.IPathingGrid;
import appeng.api.networking.security.ISecurityGrid;
import appeng.api.util.DimensionalCoord;
import appeng.items.tools.ToolNetworkVisualiser;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import dev.channeltools.ChannelTools;
import dev.channeltools.network.LinkKey;
import dev.channeltools.network.LinksMessage;

/** Supplies a separate bounded packet while the existing AE2 visualiser is held. */
public final class VisualiserService {
    private static final Map<EntityPlayerMP, LastUpdate> UPDATES = new WeakHashMap<>();
    public static void clear() { UPDATES.clear(); }
    @SubscribeEvent
    public void tick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        ChannelHooks.clear();
        if (!ChannelTools.freeChannelsEnabled) return;
        MinecraftServer server = MinecraftServer.getServer();
        if (server == null || server.getConfigurationManager() == null) return;
        for (Object raw : server.getConfigurationManager().playerEntityList) {
            EntityPlayerMP player = (EntityPlayerMP) raw;
            ItemStack stack = player.inventory.getCurrentItem();
            if (stack == null || !(stack.getItem() instanceof ToolNetworkVisualiser) || !stack.hasTagCompound()
                    || !stack.getTagCompound().hasKey("dim")) { UPDATES.remove(player); continue; }
            DimensionalCoord bound = DimensionalCoord.readFromNBT(stack.getTagCompound());
            if (bound.getDimension() != player.dimension) { UPDATES.remove(player); continue; }
            long now = player.worldObj.getTotalWorldTime();
            LastUpdate last = UPDATES.get(player);
            if (last != null && last.bound.equals(bound) && now >= last.tick && now - last.tick < 100) continue;
            UPDATES.put(player, new LastUpdate(bound, now));
            send(player, bound);
        }
    }
    private static void send(EntityPlayerMP player, DimensionalCoord bound) {
        NearbyLinks links = new NearbyLinks(LinksMessage.MAX_LINKS);
        if (player.worldObj.blockExists(bound.x, bound.y, bound.z)) {
            TileEntity tile = player.worldObj.getTileEntity(bound.x, bound.y, bound.z);
            IGridNode root = tile instanceof IGridHost ? ((IGridHost) tile).getGridNode(ForgeDirection.UNKNOWN) : null;
            IGrid grid = root == null ? null : root.getGrid();
            if (grid != null && !((IPathingGrid) grid.getCache(IPathingGrid.class)).isNetworkBooting()
                    && ((ISecurityGrid) grid.getCache(ISecurityGrid.class)).hasPermission(player, SecurityPermissions.BUILD)) {
                NetworkChannelAvailability availability = new NetworkChannelAvailability(grid);
                Set<IGridConnection> seen = new HashSet<>();
                for (IGridNode node : grid.getNodes()) {
                    if (!node.getGridBlock().isWorldAccessible()) continue;
                    for (IGridConnection connection : node.getConnections()) {
                        if (!seen.add(connection)) continue;
                        IGridBlock a = connection.a().getGridBlock(), b = connection.b().getGridBlock();
                        if (!a.isWorldAccessible() || !b.isWorldAccessible()) continue;
                        DimensionalCoord pa = a.getLocation(), pb = b.getLocation();
                        if (pa == null || pb == null || !pa.isInWorld(player.worldObj) || !pb.isInWorld(player.worldObj)) continue;
                        if (pa.x == pb.x && pa.y == pb.y && pa.z == pb.z) continue;
                        double dx = (pa.x + (double) pb.x) / 2 + .5 - player.posX;
                        double dy = (pa.y + (double) pb.y) / 2 + .5 - player.posY;
                        double dz = (pa.z + (double) pb.z) / 2 + .5 - player.posZ;
                        // AE2 draws labels within 16 blocks. Include a movement margin until the next update.
                        double distanceSquared = dx*dx + dy*dy + dz*dz;
                        if (distanceSquared > 32*32) continue;
                        LinkKey key = new LinkKey(pa.x, pa.y, pa.z, pb.x, pb.y, pb.z);
                        int free = availability.getFreeChannels(connection);
                        links.offer(key, free, distanceSquared);
                    }
                }
            }
        }
        ChannelTools.network.sendTo(new LinksMessage(bound, links.toMap()), player);
    }
    private static final class LastUpdate {
        final DimensionalCoord bound; final long tick;
        LastUpdate(DimensionalCoord bound, long tick) { this.bound = bound; this.tick = tick; }
    }
}
