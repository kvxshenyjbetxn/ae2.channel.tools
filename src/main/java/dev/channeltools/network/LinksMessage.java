package dev.channeltools.network;
import java.util.HashMap;
import java.util.Map;
import appeng.api.util.DimensionalCoord;
import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import io.netty.buffer.ByteBuf;
import dev.channeltools.ChannelTools;
public final class LinksMessage implements IMessage {
    public static final int MAX_LINKS = 4096;
    public DimensionalCoord bound;
    public Map<LinkKey, Integer> links = new HashMap<>();
    public LinksMessage() { }
    public LinksMessage(DimensionalCoord bound, Map<LinkKey, Integer> links) {
        if (links.size() > MAX_LINKS) throw new IllegalArgumentException("Too many visualiser links");
        this.bound = bound; this.links = new HashMap<>(links);
    }
    public void fromBytes(ByteBuf stream) {
        bound = DimensionalCoord.readFromPacket(stream);
        int count = stream.readInt();
        if (count < 0 || count > MAX_LINKS || stream.readableBytes() != count * 25) throw new IllegalArgumentException("Invalid visualiser link payload");
        Map<LinkKey, Integer> decoded = new HashMap<>();
        for (int i = 0; i < count; i++) {
            LinkKey key = new LinkKey(stream.readInt(), stream.readInt(), stream.readInt(), stream.readInt(), stream.readInt(), stream.readInt());
            int free = stream.readByte();
            if (free < -1 || free > 32) throw new IllegalArgumentException("Invalid channel capacity");
            decoded.put(key, free);
        }
        links = decoded;
    }
    public void toBytes(ByteBuf stream) {
        bound.writeToPacket(stream); stream.writeInt(links.size());
        for (Map.Entry<LinkKey, Integer> entry : links.entrySet()) {
            LinkKey key = entry.getKey();
            stream.writeInt(key.ax); stream.writeInt(key.ay); stream.writeInt(key.az);
            stream.writeInt(key.bx); stream.writeInt(key.by); stream.writeInt(key.bz); stream.writeByte(entry.getValue());
        }
    }
    public static final class Handler implements IMessageHandler<LinksMessage, IMessage> {
        public IMessage onMessage(LinksMessage message, MessageContext context) { ChannelTools.proxy.receive(message); return null; }
    }
}
