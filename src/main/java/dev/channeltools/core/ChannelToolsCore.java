package dev.channeltools.core;

import java.util.Map;
import cpw.mods.fml.relauncher.IFMLLoadingPlugin;

@IFMLLoadingPlugin.MCVersion("1.7.10")
// Forge's FMLDeobfTweaker runs at 1000; hook descriptors use deobfuscated Minecraft class names.
@IFMLLoadingPlugin.SortingIndex(1001)
@IFMLLoadingPlugin.TransformerExclusions("dev.channeltools.core")
public final class ChannelToolsCore implements IFMLLoadingPlugin {
    public String[] getASMTransformerClass() { return new String[] { "dev.channeltools.core.ChannelTransformer" }; }
    public String getModContainerClass() { return null; }
    public String getSetupClass() { return null; }
    public void injectData(Map<String, Object> data) { }
    public String getAccessTransformerClass() { return null; }
}
