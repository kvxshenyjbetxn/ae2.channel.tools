package dev.channeltools.core;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import org.junit.Test;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import cpw.mods.fml.relauncher.IFMLLoadingPlugin;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.analysis.Analyzer;
import org.objectweb.asm.tree.analysis.BasicVerifier;
public class ChannelTransformerTest {
    @Test public void hooksRunAfterForgeRuntimeDeobfuscation() {
        IFMLLoadingPlugin.SortingIndex index = ChannelToolsCore.class.getAnnotation(IFMLLoadingPlugin.SortingIndex.class);
        assertNotNull("Coremod must explicitly run after FMLDeobfTweaker", index);
        assertTrue("Forge runtime deobfuscation uses index 1000", index.value() > 1000);
    }
    @Test public void failsClearlyWhenTheAllocationHookDisappears() throws Exception {
        String name = "appeng.me.pathfinding.PathingCalculation";
        ClassNode node = new ClassNode();
        new ClassReader(resource(name)).accept(node, 0);
        node.methods.removeIf(raw -> ((MethodNode) raw).name.equals("tryUseChannel"));
        ClassWriter writer = new ClassWriter(0); node.accept(writer);
        try {
            new ChannelTransformer().transform(name, name, writer.toByteArray());
            fail("An incompatible AE2 layout must not be silently accepted");
        } catch (IllegalStateException expected) {
            assertTrue(expected.getMessage().contains("appeng/me/pathfinding/PathingCalculation"));
            assertTrue(expected.getMessage().contains("tryUseChannel"));
            assertTrue(expected.getMessage().contains("rv3-beta-1082-GTNH"));
        }
    }
    @Test public void patchesAndVerifiesActualReleasedAE2Bytecode() throws Exception {
        String[] targets = { "appeng.block.AEBaseItemBlock", "appeng.parts.PartPlacement", "appeng.me.cache.PathGridCache",
                "appeng.me.pathfinding.PathingCalculation", "appeng.client.render.NetworkVisualiserRender" };
        for (String name : targets) {
            byte[] source = resource(name);
            byte[] transformed = new ChannelTransformer().transform(name, name, source);
            assertFalse("No patch applied to " + name, java.util.Arrays.equals(source, transformed));
            ClassNode node = new ClassNode(); new ClassReader(transformed).accept(node, 0);
            int hooks = 0;
            for (Object raw : node.methods) {
                MethodNode method = (MethodNode) raw;
                if ((method.access & (Opcodes.ACC_ABSTRACT | Opcodes.ACC_NATIVE)) == 0)
                    new Analyzer(new BasicVerifier()).analyze(node.name, method);
                for (AbstractInsnNode insn : method.instructions.toArray())
                    if (insn instanceof MethodInsnNode && ((MethodInsnNode) insn).owner.startsWith("dev/channeltools/")) hooks++;
            }
            assertTrue(name, hooks > 0);
        }
    }
    @Test public void unrelatedClassesRemainIdentical() throws Exception {
        byte[] source = resource("appeng.api.networking.IGridNode");
        assertSame(source, new ChannelTransformer().transform("appeng.api.networking.IGridNode", "appeng.api.networking.IGridNode", source));
    }
    private static byte[] resource(String name) throws IOException {
        try (InputStream stream = ChannelTransformerTest.class.getClassLoader().getResourceAsStream(name.replace('.', '/') + ".class")) {
            assertNotNull(name, stream); ByteArrayOutputStream out = new ByteArrayOutputStream(); byte[] buffer = new byte[8192]; int read;
            while ((read = stream.read(buffer)) != -1) out.write(buffer, 0, read);
            return out.toByteArray();
        }
    }
}
