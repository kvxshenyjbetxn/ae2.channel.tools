package dev.channeltools.core;

import java.io.InputStream;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldNode;

/** Resource inspection only: never load an AE2 class before its transformers run. */
public final class Compatibility {
    public static final boolean NATIVE_ALERTS = resource("appeng/me/ChannelAlertTracker.class");
    public static final boolean NATIVE_FREE_CHANNELS = hasField("appeng/items/tools/ToolNetworkVisualiser$VLink.class", "freeChannels");
    private Compatibility() { }
    private static boolean resource(String name) {
        return Compatibility.class.getClassLoader().getResource(name) != null;
    }
    private static boolean hasField(String resource, String name) {
        try (InputStream input = Compatibility.class.getClassLoader().getResourceAsStream(resource)) {
            if (input == null) return false;
            ClassNode node = new ClassNode();
            new ClassReader(input).accept(node, ClassReader.SKIP_CODE | ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
            for (Object raw : node.fields) if (((FieldNode) raw).name.equals(name)) return true;
            return false;
        } catch (java.io.IOException e) { throw new IllegalStateException("Cannot inspect AE2 compatibility", e); }
    }
}
