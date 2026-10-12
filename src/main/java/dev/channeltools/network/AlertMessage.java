package dev.channeltools.network;
import java.util.ArrayList;
import java.util.List;
import net.minecraftforge.common.util.ForgeDirection;
import appeng.api.util.DimensionalCoord;
import cpw.mods.fml.common.network.ByteBufUtils;
import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import io.netty.buffer.ByteBuf;
import dev.channeltools.ChannelTools;
public final class AlertMessage implements IMessage {
    public static final int MAX_TARGETS = 64;
    public List<AlertTarget> targets = new ArrayList<>();
    public int total;
    public AlertMessage() { }
    public AlertMessage(List<AlertTarget> targets) {
        total = targets.size();
        this.targets = new ArrayList<>(targets.subList(0, Math.min(total, MAX_TARGETS)));
    }
    public void fromBytes(ByteBuf stream) {
        total = stream.readInt(); int size = stream.readInt();
        if (size < 0 || size > MAX_TARGETS || total < size) throw new IllegalArgumentException("Invalid channel alert size");
        List<AlertTarget> decoded = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            DimensionalCoord location = DimensionalCoord.readFromPacket(stream);
            int side = stream.readUnsignedByte();
            if (side > ForgeDirection.UNKNOWN.ordinal()) throw new IllegalArgumentException("Invalid channel alert side");
            decoded.add(new AlertTarget(location, ForgeDirection.getOrientation(side), ByteBufUtils.readItemStack(stream)));
        }
        targets = decoded;
    }
    public void toBytes(ByteBuf stream) {
        stream.writeInt(total); stream.writeInt(targets.size());
        for (AlertTarget target : targets) {
            target.location.writeToPacket(stream); stream.writeByte(target.side.ordinal()); ByteBufUtils.writeItemStack(stream, target.icon);
        }
    }
    public static final class Handler implements IMessageHandler<AlertMessage, IMessage> {
        public IMessage onMessage(AlertMessage message, MessageContext context) { ChannelTools.proxy.receive(message); return null; }
    }
}
