package dev.channeltools.server;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import appeng.api.networking.GridFlags;
import appeng.api.networking.IGrid;
import appeng.api.networking.IGridConnection;
import appeng.api.networking.IGridMultiblock;
import appeng.api.networking.IGridNode;
import appeng.api.networking.pathing.ControllerState;
import appeng.api.networking.pathing.IPathingGrid;
import appeng.core.AEConfig;
import appeng.core.features.AEFeature;
import appeng.me.GridConnection;
import appeng.me.GridNode;

/** Calculates how many more devices a connection can supply along its route to the controller. */
public final class NetworkChannelAvailability {

    private final ControllerState controllerState;
    private final boolean channelsEnabled;
    private final int adHocFreeChannels;
    private final Map<GridNode, Integer> upstreamRemaining = new IdentityHashMap<>();

    public NetworkChannelAvailability(IGrid grid) {
        this(grid, AEConfig.instance.isFeatureEnabled(AEFeature.Channels));
    }

    NetworkChannelAvailability(IGrid grid, boolean channelsEnabled) {
        this.controllerState = ((IPathingGrid) grid.getCache(IPathingGrid.class)).getControllerState();
        this.channelsEnabled = channelsEnabled;
        this.adHocFreeChannels = channelsEnabled && controllerState == ControllerState.NO_CONTROLLER
                ? adHocFreeChannels(grid.getNodes()) : -1;
    }

    public int getFreeChannels(IGridConnection connection) {
        if (!channelsEnabled) return -1;

        if (controllerState == ControllerState.NO_CONTROLLER) {
            // AE2 resets every connection to zero when demand exceeds eight. Count demand, not assignments.
            return adHocFreeChannels;
        }
        if (controllerState != ControllerState.CONTROLLER_ONLINE) return -1;

        GridConnection routed = (GridConnection) connection;
        if (routed.b().getControllerRoute() != routed) {
            // A non-tree connection carries no channels; its used count cannot describe available routes.
            return -1;
        }

        int remaining = spare(connection);
        GridNode parent = (GridNode) routed.getControllerRoute();
        return Math.min(remaining, remainingUpstream(parent, routed));
    }

    static int adHocFreeChannels(Iterable<IGridNode> nodes) {
        boolean disallowsCompressed = false;
        for (IGridNode node : nodes) {
            if (node.hasFlag(GridFlags.CANNOT_CARRY_COMPRESSED)) disallowsCompressed = true;
        }
        int required = countAdHocChannels(nodes, disallowsCompressed);
        return required < 0 ? -1 : Math.max(0, 8 - required);
    }

    /** Returns -1 for compressed-channel conflicts; each multiblock consumes only one channel. */
    static int countAdHocChannels(Iterable<IGridNode> nodes, boolean disallowsCompressed) {
        Set<IGridNode> ignored = new HashSet<>();
        int count = 0;
        for (IGridNode node : nodes) {
            if (!node.hasFlag(GridFlags.REQUIRE_CHANNEL) || ignored.contains(node)) continue;
            if (disallowsCompressed && node.hasFlag(GridFlags.COMPRESSED_CHANNEL)) return -1;
            count++;
            if (node.hasFlag(GridFlags.MULTIBLOCK)) {
                Iterator<IGridNode> members = ((IGridMultiblock) node.getGridBlock()).getMultiblockNodes();
                while (members.hasNext()) ignored.add(members.next());
            }
        }
        return count;
    }

    private int remainingUpstream(GridNode node, GridConnection route) {
        if (isController(node)) return remainingOnFace(node, route);

        List<GridNode> pending = new ArrayList<>();
        int remaining;
        while (true) {
            Integer cached = upstreamRemaining.get(node);
            if (cached != null) {
                remaining = cached;
                break;
            }

            pending.add(node);
            GridConnection parentRoute = (GridConnection) node.getControllerRoute();
            node = (GridNode) parentRoute.getControllerRoute();
            if (isController(node)) {
                remaining = remainingOnFace(node, parentRoute);
                break;
            }
        }

        for (int i = pending.size() - 1; i >= 0; i--) {
            GridNode current = pending.get(i);
            GridConnection parentRoute = (GridConnection) current.getControllerRoute();
            remaining = Math.min(remaining, Math.max(0, current.getMaxChannels() - current.getUsedChannels()));
            remaining = Math.min(remaining, spare(parentRoute));
            upstreamRemaining.put(current, remaining);
        }
        return remaining;
    }

    private static int remainingOnFace(GridNode controller, GridConnection route) {
        int used = 0;
        for (IGridConnection connection : controller.getConnections()) {
            if (connection.getDirection(controller) == route.getDirection(controller)) {
                used += connection.getUsedChannels();
            }
        }
        return Math.max(0, 32 - used);
    }

    private static int spare(IGridConnection connection) {
        int capacity = connection.a().hasFlag(GridFlags.DENSE_CAPACITY)
                && connection.b().hasFlag(GridFlags.DENSE_CAPACITY) ? 32 : 8;
        return Math.max(0, capacity - connection.getUsedChannels());
    }

    private static boolean isController(IGridNode node) {
        for (Class<?> type = node.getMachine().getClass(); type != null; type = type.getSuperclass()) {
            String name = type.getName();
            if (name.equals("appeng.tile.networking.TileController") || name.equals("appeng.tile.networking.TileCreativeEnergyController")) return true;
        }
        return false;
    }
}
