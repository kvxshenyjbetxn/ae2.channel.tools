package dev.channeltools.client;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.util.IChatComponent;
import net.minecraft.world.World;
import net.minecraftforge.client.ClientCommandHandler;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.util.ForgeDirection;
import dev.channeltools.CommonProxy;
import dev.channeltools.network.AlertMessage;
import dev.channeltools.network.AlertTarget;
import dev.channeltools.network.LinksMessage;

public final class ClientProxy extends CommonProxy {
    public void init() {
        MinecraftForge.EVENT_BUS.register(ChannelAlertHighlighter.INSTANCE);
        MinecraftForge.EVENT_BUS.register(new ChannelLabels());
        ClientCommandHandler.instance.registerCommand(new ToggleChannelsCommand());
    }
    public void receive(final LinksMessage message) {
        final Minecraft mc = Minecraft.getMinecraft();
        final World world = mc.theWorld;
        mc.func_152344_a(() -> {
            if (world != null && mc.theWorld == world) ChannelLabels.accept(message);
        });
    }
    public void receive(final AlertMessage message) {
        final Minecraft mc = Minecraft.getMinecraft();
        final EntityPlayer player = mc.thePlayer;
        final World world = mc.theWorld;
        mc.func_152344_a(() -> {
            if (player == null || mc.thePlayer != player || world != mc.theWorld) return;
            int shown = Math.min(message.targets.size(), 8);
            for (int i = 0; i < shown; i++) {
                AlertTarget target = message.targets.get(i);
                IChatComponent name = target.icon == null ? new ChatComponentTranslation("chat.ae2channeltools.device") : target.icon.func_151000_E();
                if (target.side == ForgeDirection.UNKNOWN) player.addChatMessage(new ChatComponentTranslation(
                        "chat.ae2channeltools.lost", name, target.location.x, target.location.y, target.location.z, target.location.getDimension()));
                else player.addChatMessage(new ChatComponentTranslation("chat.ae2channeltools.lostPart", name,
                        new ChatComponentTranslation("chat.ae2channeltools.side." + target.side.name()),
                        target.location.x, target.location.y, target.location.z, target.location.getDimension()));
            }
            if (message.total > shown) player.addChatMessage(new ChatComponentTranslation("chat.ae2channeltools.more", message.total - shown));
            ChannelAlertHighlighter.highlight(message.targets);
        });
    }
}
