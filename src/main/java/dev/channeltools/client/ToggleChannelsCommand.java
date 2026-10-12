package dev.channeltools.client;
import net.minecraft.command.CommandBase;
import net.minecraft.command.ICommandSender;
import net.minecraft.util.ChatComponentTranslation;
import dev.channeltools.ChannelTools;
import dev.channeltools.core.Compatibility;
public final class ToggleChannelsCommand extends CommandBase {
    public String getCommandName() { return "ae2channels"; }
    public String getCommandUsage(ICommandSender sender) { return "/ae2channels"; }
    public int getRequiredPermissionLevel() { return 0; }
    public void processCommand(ICommandSender sender, String[] args) {
        if (Compatibility.NATIVE_FREE_CHANNELS) { sender.addChatMessage(new ChatComponentTranslation("chat.ae2channeltools.native")); return; }
        if (!ChannelTools.freeChannelsEnabled) { sender.addChatMessage(new ChatComponentTranslation("chat.ae2channeltools.disabled")); return; }
        ChannelLabels.showFree = !ChannelLabels.showFree;
        sender.addChatMessage(new ChatComponentTranslation(ChannelLabels.showFree ? "chat.ae2channeltools.free" : "chat.ae2channeltools.used"));
    }
}
