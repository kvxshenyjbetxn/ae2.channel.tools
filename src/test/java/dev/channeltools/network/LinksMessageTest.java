package dev.channeltools.network;
import java.util.HashMap;
import java.util.Map;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import appeng.api.util.DimensionalCoord;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
public class LinksMessageTest {
    @Test public void reversedConnectionsHaveTheSameIdentity() {
        LinkKey forward = new LinkKey(1, 2, 3, -4, 5, 6), reverse = new LinkKey(-4, 5, 6, 1, 2, 3);
        assertEquals(forward, reverse); assertEquals(forward.hashCode(), reverse.hashCode());
    }
    @Test public void roundTripKeepsZeroUnknownAndDenseCapacity() {
        Map<LinkKey, Integer> links = new HashMap<>();
        links.put(new LinkKey(1,2,3,4,5,6), 0);
        links.put(new LinkKey(2,3,4,5,6,7), -1);
        links.put(new LinkKey(3,4,5,6,7,8), 32);
        ByteBuf buffer = Unpooled.buffer();
        try {
            LinksMessage original = new LinksMessage(new DimensionalCoord(2,64,-5,7), links);
            original.toBytes(buffer); LinksMessage decoded = new LinksMessage(); decoded.fromBytes(buffer);
            assertEquals(original.bound, decoded.bound); assertEquals(links, decoded.links); assertEquals(0, buffer.readableBytes());
        } finally { buffer.release(); }
    }
    @Test(expected = IllegalArgumentException.class) public void refusesOversizedCount() {
        ByteBuf buffer = Unpooled.buffer();
        try { new DimensionalCoord(0,0,0,0).writeToPacket(buffer); buffer.writeInt(LinksMessage.MAX_LINKS+1); new LinksMessage().fromBytes(buffer); }
        finally { buffer.release(); }
    }
    @Test(expected = IllegalArgumentException.class) public void refusesTruncatedPayloadBeforeAllocating() {
        ByteBuf buffer = Unpooled.buffer();
        try { new DimensionalCoord(0,0,0,0).writeToPacket(buffer); buffer.writeInt(1); new LinksMessage().fromBytes(buffer); }
        finally { buffer.release(); }
    }
    @Test(expected = IllegalArgumentException.class) public void refusesImpossibleChannelCapacity() {
        ByteBuf buffer = Unpooled.buffer();
        try {
            new DimensionalCoord(0,0,0,0).writeToPacket(buffer); buffer.writeInt(1);
            for(int i=0;i<6;i++) buffer.writeInt(i); buffer.writeByte(33); new LinksMessage().fromBytes(buffer);
        } finally { buffer.release(); }
    }
}
