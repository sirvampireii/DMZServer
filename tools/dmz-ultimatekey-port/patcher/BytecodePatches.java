import java.nio.file.Files;
import java.nio.file.Path;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;

/**
 * In-place bytecode patches for classes that cannot be recompiled here (client-only classes need
 * the client Minecraft jar). Each patch states how many call sites it must change and fails the
 * build when the count is different.
 *
 * usage: BytecodePatches <staged classes dir>
 */
public final class BytecodePatches {
    public static void main(String[] args) throws Exception {
        Path root = Path.of(args[0]);
        // DMZ 2.2: ConfigManager.applySpecificSyncedConfig(String, String) returns boolean (was void).
        retargetVoidToBoolean(root, "com/dmzlegacy/ultimatekey/client/editor/PreviewModel",
                "com/dragonminez/common/config/ConfigManager", "applySpecificSyncedConfig",
                "(Ljava/lang/String;Ljava/lang/String;)", 2);
    }

    private static void retargetVoidToBoolean(Path root, String cls, String owner, String name, String args,
                                              int expected) throws Exception {
        Path file = root.resolve(cls + ".class");
        ClassNode node = new ClassNode();
        new ClassReader(Files.readAllBytes(file)).accept(node, 0);
        int changed = 0;
        for (MethodNode m : node.methods) {
            for (AbstractInsnNode insn : m.instructions.toArray()) {
                if (insn instanceof MethodInsnNode call && call.getOpcode() == Opcodes.INVOKESTATIC
                        && call.owner.equals(owner) && call.name.equals(name) && call.desc.equals(args + "V")) {
                    call.desc = args + "Z";
                    m.instructions.insert(call, new InsnNode(Opcodes.POP));
                    changed++;
                }
            }
        }
        if (changed != expected) {
            throw new IllegalStateException(cls + ": expected " + expected + " calls to " + name + ", patched " + changed);
        }
        // Only a POP after a call: max stack and frames are unchanged, so no recomputation is needed.
        ClassWriter w = new ClassWriter(0);
        node.accept(w);
        Files.write(file, w.toByteArray());
        System.out.println("patched " + cls + ": " + changed + " x " + name + " now returns boolean");
    }

    private BytecodePatches() {
    }
}
