package dev.channeltools;

import net.minecraftforge.common.config.Configuration;
import org.apache.logging.log4j.Logger;
import cpw.mods.fml.common.Mod;
import cpw.mods.fml.common.SidedProxy;
import cpw.mods.fml.common.event.FMLPreInitializationEvent;
import cpw.mods.fml.common.event.FMLServerStoppedEvent;
import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.network.NetworkRegistry;
import cpw.mods.fml.common.network.simpleimpl.SimpleNetworkWrapper;
import cpw.mods.fml.relauncher.Side;
import dev.channeltools.core.Compatibility;
import dev.channeltools.network.AlertMessage;
import dev.channeltools.network.LinksMessage;
import dev.channeltools.server.ChannelAlertTracker;
import dev.channeltools.server.ChannelHooks;
import dev.channeltools.server.VisualiserService;

@Mod(modid = ChannelTools.ID, name = "AE2 Channel Tools", version = "0.1.0", acceptedMinecraftVersions = "[1.7.10]",
        dependencies = "required-after:appliedenergistics2")
public final class ChannelTools {
    public static final String ID = "ae2channeltools";
    @SidedProxy(clientSide = "dev.channeltools.client.ClientProxy", serverSide = "dev.channeltools.CommonProxy")
    public static CommonProxy proxy;
    public static SimpleNetworkWrapper network;
    public static boolean alertsEnabled;
    public static boolean freeChannelsEnabled;
    public static Logger log;

    @Mod.EventHandler
    public void preInit(FMLPreInitializationEvent event) {
        log = event.getModLog();
        Configuration config = new Configuration(event.getSuggestedConfigurationFile());
        config.load();
        alertsEnabled = config.getBoolean("channelLossAlerts", "features", true, "Notify the placing player when existing devices lose channels due to capacity limits.") && !Compatibility.NATIVE_ALERTS;
        freeChannelsEnabled = config.getBoolean("freeChannels", "features", true, "Show free channel capacity in the existing Network Visualiser's Full mode. Toggle locally with /ae2channels.") && !Compatibility.NATIVE_FREE_CHANNELS;
        if (config.hasChanged()) config.save();
        network = NetworkRegistry.INSTANCE.newSimpleChannel(ID);
        network.registerMessage(AlertMessage.Handler.class, AlertMessage.class, 0, Side.CLIENT);
        network.registerMessage(LinksMessage.Handler.class, LinksMessage.class, 1, Side.CLIENT);
        FMLCommonHandler.instance().bus().register(new VisualiserService());
        proxy.init();
        log.info("AE2 Channel Tools: alerts={}, freeChannels={}, nativeAlerts={}, nativeFreeChannels={}", alertsEnabled, freeChannelsEnabled, Compatibility.NATIVE_ALERTS, Compatibility.NATIVE_FREE_CHANNELS);
    }

    @Mod.EventHandler
    public void stop(FMLServerStoppedEvent event) {
        ChannelAlertTracker.clear();
        ChannelHooks.clear();
        VisualiserService.clear();
    }
}
