package dev.channeltools.core;

import net.minecraft.launchwrapper.IClassTransformer;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.VarInsnNode;

/** Inserts callbacks; does not replace AE2's allocation algorithm, classes, enums or packet format. */
public final class ChannelTransformer implements IClassTransformer, Opcodes {
    private static final String HOOK = "dev/channeltools/server/ChannelHooks";
    private static final String LABEL = "dev/channeltools/client/ChannelLabels";
    private static final String GRID = "Lappeng/api/networking/IGrid;";

    public byte[] transform(String name, String transformedName, byte[] bytes) {
        if (bytes == null) return null;
        boolean alerts = !Compatibility.NATIVE_ALERTS;
        boolean labels = !Compatibility.NATIVE_FREE_CHANNELS;
        if (!(alerts && (transformedName.equals("appeng.block.AEBaseItemBlock")
                || transformedName.equals("appeng.parts.PartPlacement")
                || transformedName.equals("appeng.me.cache.PathGridCache")
                || transformedName.equals("appeng.me.pathfinding.PathingCalculation")))
                && !(labels && transformedName.equals("appeng.client.render.NetworkVisualiserRender"))) return bytes;
        ClassNode node = new ClassNode();
        new ClassReader(bytes).accept(node, 0);
        if (transformedName.endsWith("AEBaseItemBlock")) blockPlacement(node);
        else if (transformedName.endsWith("PartPlacement")) partPlacement(node);
        else if (transformedName.endsWith("PathGridCache")) repath(node);
        else if (transformedName.endsWith("PathingCalculation")) rejected(node);
        else labels(node);
        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        node.accept(writer);
        return writer.toByteArray();
    }

    private static void blockPlacement(ClassNode node) {
        MethodNode method = method(node, "placeBlockAt", "(Lnet/minecraft/item/ItemStack;Lnet/minecraft/entity/player/EntityPlayer;Lnet/minecraft/world/World;IIIIFFFI)Z");
        int count = 0;
        for (AbstractInsnNode insn : method.instructions.toArray()) if (insn.getOpcode() == IRETURN) {
            InsnList call = new InsnList();
            call.add(new InsnNode(DUP));
            call.add(new VarInsnNode(ALOAD, 2));
            call.add(new VarInsnNode(ALOAD, 3));
            for (int slot = 4; slot <= 6; slot++) call.add(new VarInsnNode(ILOAD, slot));
            call.add(invoke(HOOK, "blockPlaced", "(ZLnet/minecraft/entity/player/EntityPlayer;Lnet/minecraft/world/World;III)V"));
            method.instructions.insertBefore(insn, call);
            count++;
        }
        require(count > 0, node, "block placement returns");
    }

    private static void partPlacement(ClassNode node) {
        int count = 0;
        for (Object raw : node.methods) {
            MethodNode method = (MethodNode) raw;
            if (!method.name.equals("tryPlace")) continue;
            for (AbstractInsnNode insn : method.instructions.toArray()) if (insn instanceof MethodInsnNode) {
                MethodInsnNode call = (MethodInsnNode) insn;
                if (call.owner.equals("appeng/api/parts/IPartHost") && call.name.equals("addPart")
                        && call.desc.equals("(Lnet/minecraft/item/ItemStack;Lnet/minecraftforge/common/util/ForgeDirection;Lnet/minecraft/entity/player/EntityPlayer;)Lnet/minecraftforge/common/util/ForgeDirection;")) {
                    method.instructions.set(call, invoke(HOOK, "addPart", "(Lappeng/api/parts/IPartHost;Lnet/minecraft/item/ItemStack;Lnet/minecraftforge/common/util/ForgeDirection;Lnet/minecraft/entity/player/EntityPlayer;)Lnet/minecraftforge/common/util/ForgeDirection;"));
                    count++;
                }
            }
        }
        require(count == 1, node, "multipart placement callback");
    }

    private static InsnList gridArgument(ClassNode node) {
        InsnList list = new InsnList();
        list.add(new VarInsnNode(ALOAD, 0));
        list.add(new FieldInsnNode(GETFIELD, node.name, "myGrid", GRID));
        return list;
    }

    private static void repath(ClassNode node) {
        MethodNode tick = method(node, "onUpdateTick", "()V");
        int starts = 0, finishes = 0;
        for (AbstractInsnNode insn : tick.instructions.toArray()) {
            if (insn instanceof FieldInsnNode && insn.getOpcode() == PUTFIELD
                    && ((FieldInsnNode) insn).name.equals("updateNetwork")) {
                InsnList call = gridArgument(node);
                call.add(invoke(HOOK, "begin", "(" + GRID + ")V"));
                tick.instructions.insert(insn, call);
                starts++;
            }
            if (insn.getOpcode() == RETURN) {
                InsnList call = gridArgument(node);
                call.add(invoke(HOOK, "finish", "(" + GRID + ")V"));
                tick.instructions.insertBefore(insn, call);
                finishes++;
            }
        }
        require(starts == 1 && finishes > 0, node, "network recalculation callbacks");
        MethodNode adhoc = method(node, "calculateAdHocChannels", "()I");
        int returns = 0;
        for (AbstractInsnNode insn : adhoc.instructions.toArray()) if (insn.getOpcode() == IRETURN) {
            InsnList call = new InsnList();
            call.add(new InsnNode(DUP));
            call.add(gridArgument(node));
            call.add(invoke(HOOK, "adHocResult", "(I" + GRID + ")V"));
            adhoc.instructions.insertBefore(insn, call);
            returns++;
        }
        require(returns > 0, node, "controllerless network callbacks");
    }

    private static void rejected(ClassNode node) {
        MethodNode method = method(node, "tryUseChannel", "(Lappeng/me/GridNode;)Z");
        int returns = 0;
        for (AbstractInsnNode insn : method.instructions.toArray()) if (insn.getOpcode() == IRETURN) {
            InsnList call = new InsnList();
            call.add(new InsnNode(DUP));
            call.add(new VarInsnNode(ALOAD, 1));
            call.add(invoke(HOOK, "allocationResult", "(ZLappeng/me/GridNode;)V"));
            method.instructions.insertBefore(insn, call);
            returns++;
        }
        // In supported AE2 this method has exactly three rejections and one success.
        require(returns == 4, node, "channel allocation failure reasons");
    }

    private static void labels(ClassNode node) {
        MethodNode method = method(node, "doRender", "(Ljava/lang/Float;DDD)V");
        int reads = 0;
        for (AbstractInsnNode insn : method.instructions.toArray()) if (insn instanceof FieldInsnNode) {
            FieldInsnNode field = (FieldInsnNode) insn;
            if (field.getOpcode() == GETFIELD && field.owner.equals("appeng/items/tools/ToolNetworkVisualiser$VLink") && field.name.equals("channels")) {
                method.instructions.set(field, invoke(LABEL, reads++ == 0 ? "visible" : "count", "(Lappeng/items/tools/ToolNetworkVisualiser$VLink;)I"));
            }
        }
        require(reads == 2, node, "visualiser channel labels");
    }

    private static MethodInsnNode invoke(String owner, String name, String desc) {
        return new MethodInsnNode(INVOKESTATIC, owner, name, desc, false);
    }
    private static MethodNode method(ClassNode node, String name, String desc) {
        for (Object raw : node.methods) {
            MethodNode method = (MethodNode) raw;
            if (method.name.equals(name) && method.desc.equals(desc)) return method;
        }
        throw unsupported(node, name + desc);
    }
    private static void require(boolean condition, ClassNode node, String hook) {
        if (!condition) throw unsupported(node, hook);
    }
    private static IllegalStateException unsupported(ClassNode node, String hook) {
        return new IllegalStateException("AE2 Channel Tools 0.1.0: unsupported AE2 layout in " + node.name
                + " (" + hook + "). Built for rv3-beta-1082-GTNH; use a compatible addon build or remove the addon.");
    }
}
