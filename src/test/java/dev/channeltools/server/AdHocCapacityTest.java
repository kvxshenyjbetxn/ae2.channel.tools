package dev.channeltools.server;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import org.junit.Test;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import appeng.api.networking.GridFlags;
import appeng.api.networking.IGridNode;
public class AdHocCapacityTest {
    private static IGridNode node(boolean compressed) {
        return (IGridNode) Proxy.newProxyInstance(IGridNode.class.getClassLoader(), new Class<?>[]{IGridNode.class}, (proxy, method, args) -> {
            if (method.getName().equals("hasFlag")) return args[0] == GridFlags.REQUIRE_CHANNEL || (compressed && args[0] == GridFlags.COMPRESSED_CHANNEL);
            if (method.getName().equals("hashCode")) return System.identityHashCode(proxy);
            if (method.getName().equals("equals")) return proxy == args[0];
            return null;
        });
    }
    @Test public void ninthConsumerExceedsTheSharedLimit() {
        List<IGridNode> nodes = new ArrayList<>(); for(int i=0;i<8;i++) nodes.add(node(false));
        assertFalse(ChannelHooks.adHocCapacityExceeded(nodes, false));
        nodes.add(node(false)); assertTrue(ChannelHooks.adHocCapacityExceeded(nodes, false));
    }
    @Test public void compressedFailureTakesPrecedenceEvenAfterNineConsumers() {
        List<IGridNode> nodes = new ArrayList<>(); for(int i=0;i<9;i++) nodes.add(node(false)); nodes.add(node(true));
        assertFalse(ChannelHooks.adHocCapacityExceeded(nodes, true));
    }
}
