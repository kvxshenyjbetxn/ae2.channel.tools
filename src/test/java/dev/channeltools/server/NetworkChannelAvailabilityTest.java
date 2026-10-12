package dev.channeltools.server;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.junit.Test;

import appeng.api.networking.GridFlags;
import appeng.api.networking.IGrid;
import appeng.api.networking.IGridConnection;
import appeng.api.networking.IGridMultiblock;
import appeng.api.networking.IGridNode;
import appeng.api.networking.pathing.ControllerState;
import appeng.api.networking.pathing.IPathingGrid;
import appeng.util.ReadOnlyCollection;

public class NetworkChannelAvailabilityTest {

    @Test
    public void overloadedAdHocConnectionsReportZeroRatherThanEight() {
        NetworkChannelAvailability availability = availability(ControllerState.NO_CONTROLLER, consumers(9), true);
        assertEquals(0, availability.getFreeChannels(unassignedConnection()));
    }

    @Test
    public void aControllerConflictDoesNotExposeCapacity() {
        NetworkChannelAvailability availability = availability(ControllerState.CONTROLLER_CONFLICT, consumers(1), true);
        assertEquals(-1, availability.getFreeChannels(unassignedConnection()));
    }

    @Test
    public void disabledChannelsDoNotExposeCapacityInEitherNetworkType() {
        for (ControllerState state : new ControllerState[] { ControllerState.NO_CONTROLLER, ControllerState.CONTROLLER_ONLINE }) {
            assertEquals(-1, availability(state, consumers(1), false).getFreeChannels(unassignedConnection()));
        }
    }

    @Test
    public void emptyAdHocNetworkHasEightFreeChannels() {
        assertEquals(8, NetworkChannelAvailability.adHocFreeChannels(Collections.emptyList()));
    }

    @Test
    public void countsDemandRatherThanFinalizedAssignments() {
        // Deliberately do not expose getUsedChannels(): even unassigned consumers occupy capacity.
        assertEquals(1, NetworkChannelAvailability.adHocFreeChannels(consumers(7)));
        assertEquals(0, NetworkChannelAvailability.adHocFreeChannels(consumers(8)));
        assertEquals(0, NetworkChannelAvailability.adHocFreeChannels(consumers(9)));
        assertEquals(0, NetworkChannelAvailability.adHocFreeChannels(consumers(32)));
    }

    @Test
    public void cablesAndDenseFlagsDoNotChangeTheSharedLimit() {
        List<IGridNode> nodes = consumers(7);
        nodes.add(node(null, GridFlags.PREFERRED));
        nodes.add(node(null, GridFlags.PREFERRED, GridFlags.DENSE_CAPACITY));
        assertEquals(1, NetworkChannelAvailability.adHocFreeChannels(nodes));
    }

    @Test
    public void aMultiblockConsumesOneChannelRegardlessOfMemberOrder() {
        List<IGridNode> members = new ArrayList<>();
        for (int i = 0; i < 12; i++) {
            members.add(node(members, GridFlags.REQUIRE_CHANNEL, GridFlags.MULTIBLOCK));
        }
        List<IGridNode> nodes = consumers(6);
        nodes.addAll(members);
        assertEquals(1, NetworkChannelAvailability.adHocFreeChannels(nodes));
        Collections.reverse(nodes);
        assertEquals(1, NetworkChannelAvailability.adHocFreeChannels(nodes));
        assertFalse(ChannelHooks.adHocCapacityExceeded(nodes, false));
        nodes.addAll(consumers(2));
        assertEquals(0, NetworkChannelAvailability.adHocFreeChannels(nodes));
        assertTrue(ChannelHooks.adHocCapacityExceeded(nodes, false));
    }

    @Test
    public void compressedConflictIsUnknownRatherThanEightFreeChannels() {
        List<IGridNode> nodes = consumers(1);
        nodes.add(node(null, GridFlags.REQUIRE_CHANNEL, GridFlags.COMPRESSED_CHANNEL));
        nodes.add(node(null, GridFlags.CANNOT_CARRY_COMPRESSED));
        assertEquals(-1, NetworkChannelAvailability.adHocFreeChannels(nodes));
    }

    @Test
    public void compressedConflictStillTakesPrecedenceInAnOverloadedNetwork() {
        List<IGridNode> nodes = consumers(9);
        nodes.add(node(null, GridFlags.REQUIRE_CHANNEL, GridFlags.COMPRESSED_CHANNEL));
        nodes.add(node(null, GridFlags.CANNOT_CARRY_COMPRESSED));
        assertEquals(-1, NetworkChannelAvailability.adHocFreeChannels(nodes));
        assertFalse(ChannelHooks.adHocCapacityExceeded(nodes, true));
        Collections.reverse(nodes);
        assertEquals(-1, NetworkChannelAvailability.adHocFreeChannels(nodes));
    }

    @Test
    public void compressedConsumersAreAllowedWithoutARestriction() {
        List<IGridNode> nodes = consumers(6);
        nodes.add(node(null, GridFlags.REQUIRE_CHANNEL, GridFlags.COMPRESSED_CHANNEL));
        assertEquals(1, NetworkChannelAvailability.adHocFreeChannels(nodes));
    }

    @Test
    public void aRestrictionAloneDoesNotInvalidateTheNetwork() {
        List<IGridNode> nodes = consumers(7);
        nodes.add(node(null, GridFlags.CANNOT_CARRY_COMPRESSED));
        assertEquals(1, NetworkChannelAvailability.adHocFreeChannels(nodes));
    }

    private static List<IGridNode> consumers(int count) {
        List<IGridNode> nodes = new ArrayList<>();
        for (int i = 0; i < count; i++) nodes.add(node(null, GridFlags.REQUIRE_CHANNEL));
        return nodes;
    }

    private static NetworkChannelAvailability availability(ControllerState state, List<IGridNode> nodes, boolean enabled) {
        IPathingGrid pathing = (IPathingGrid) Proxy.newProxyInstance(
                IPathingGrid.class.getClassLoader(), new Class<?>[] { IPathingGrid.class },
                (proxy, method, args) -> {
                    if (method.getName().equals("getControllerState")) return state;
                    throw new AssertionError("Unexpected pathing method: " + method.getName());
                });
        IGrid grid = (IGrid) Proxy.newProxyInstance(IGrid.class.getClassLoader(), new Class<?>[] { IGrid.class },
                (proxy, method, args) -> {
                    if (method.getName().equals("getCache")) return pathing;
                    if (method.getName().equals("getNodes")) return new ReadOnlyCollection<>(nodes);
                    throw new AssertionError("Unexpected grid method: " + method.getName());
                });
        return new NetworkChannelAvailability(grid, enabled);
    }

    private static IGridConnection unassignedConnection() {
        return (IGridConnection) Proxy.newProxyInstance(
                IGridConnection.class.getClassLoader(), new Class<?>[] { IGridConnection.class },
                (proxy, method, args) -> {
                    if (method.getName().equals("getUsedChannels")) return 0;
                    throw new AssertionError("Unexpected connection method: " + method.getName());
                });
    }

    private static IGridNode node(List<IGridNode> members, GridFlags... flags) {
        List<GridFlags> nodeFlags = Arrays.asList(flags);
        IGridMultiblock block = members == null ? null : (IGridMultiblock) Proxy.newProxyInstance(
                IGridMultiblock.class.getClassLoader(), new Class<?>[] { IGridMultiblock.class },
                (proxy, method, args) -> {
                    if (method.getName().equals("getMultiblockNodes")) return members.iterator();
                    throw new AssertionError("Unexpected block method: " + method.getName());
                });
        return (IGridNode) Proxy.newProxyInstance(
                IGridNode.class.getClassLoader(), new Class<?>[] { IGridNode.class },
                (proxy, method, args) -> {
                    switch (method.getName()) {
                        case "hasFlag": return nodeFlags.contains(args[0]);
                        case "getGridBlock": return block;
                        case "hashCode": return System.identityHashCode(proxy);
                        case "equals": return proxy == args[0];
                        default: throw new AssertionError("Unexpected node method: " + method.getName());
                    }
                });
    }
}
