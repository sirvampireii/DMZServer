import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.MethodNode;

/**
 * Compile-time only: applies Forge's access transformer to a copy of the vanilla SRG jar, so javac
 * sees members (like ServerGamePacketListenerImpl.f_9742_) that Forge makes public at runtime.
 * The widened jar is never shipped.
 *
 * usage: AtWiden <minecraft-srg.jar> <forge-universal.jar> <out.jar>
 */
public final class AtWiden {
    private record Rule(String access, String finality) {
    }

    public static void main(String[] args) throws Exception {
        Map<String, Map<String, Rule>> rules = new HashMap<>();
        try (ZipFile forge = new ZipFile(args[1])) {
            ZipEntry at = forge.getEntry("META-INF/accesstransformer.cfg");
            try (BufferedReader in = new BufferedReader(new InputStreamReader(forge.getInputStream(at), StandardCharsets.UTF_8))) {
                String line;
                while ((line = in.readLine()) != null) {
                    line = line.split("#", 2)[0].trim();
                    if (line.isEmpty()) {
                        continue;
                    }
                    String[] parts = line.split("\\s+");
                    String mod = parts[0];
                    String access = mod.replace("+f", "").replace("-f", "");
                    String finality = mod.endsWith("-f") ? "-f" : mod.endsWith("+f") ? "+f" : "";
                    String owner = parts[1].replace('.', '/');
                    String member = parts.length > 2 ? parts[2] : "";
                    rules.computeIfAbsent(owner, k -> new HashMap<>()).put(member, new Rule(access, finality));
                }
            }
        }
        int widened = 0;
        try (ZipFile mc = new ZipFile(args[0]);
             ZipOutputStream out = new ZipOutputStream(Files.newOutputStream(Path.of(args[2])))) {
            for (ZipEntry e : java.util.Collections.list(mc.entries())) {
                if (e.isDirectory()) {
                    continue;
                }
                byte[] bytes = mc.getInputStream(e).readAllBytes();
                String name = e.getName();
                if (name.endsWith(".class")) {
                    Map<String, Rule> forClass = rules.get(name.substring(0, name.length() - 6));
                    if (forClass != null) {
                        bytes = widen(bytes, forClass);
                        widened++;
                    }
                }
                out.putNextEntry(new ZipEntry(name));
                out.write(bytes);
                out.closeEntry();
            }
        }
        System.out.println("AT applied to " + widened + " classes -> " + args[2]);
    }

    private static byte[] widen(byte[] bytes, Map<String, Rule> rules) {
        ClassNode node = new ClassNode();
        new ClassReader(bytes).accept(node, 0);
        Rule self = rules.get("");
        if (self != null) {
            node.access = apply(node.access, self);
        }
        for (FieldNode f : node.fields) {
            Rule r = rules.get(f.name);
            if (r != null) {
                f.access = apply(f.access, r);
            }
        }
        for (MethodNode m : node.methods) {
            Rule r = rules.get(m.name + m.desc);
            if (r != null) {
                m.access = apply(m.access, r);
            }
        }
        ClassWriter w = new ClassWriter(0);
        node.accept(w);
        return w.toByteArray();
    }

    private static int apply(int access, Rule r) {
        int vis = Opcodes.ACC_PUBLIC | Opcodes.ACC_PROTECTED | Opcodes.ACC_PRIVATE;
        int now = access & vis;
        int want = switch (r.access()) {
            case "public" -> Opcodes.ACC_PUBLIC;
            case "protected" -> now == Opcodes.ACC_PUBLIC ? Opcodes.ACC_PUBLIC : Opcodes.ACC_PROTECTED;
            case "default" -> now == Opcodes.ACC_PRIVATE ? 0 : now;
            default -> now;
        };
        access = (access & ~vis) | want;
        if (r.finality().equals("-f")) {
            access &= ~Opcodes.ACC_FINAL;
        } else if (r.finality().equals("+f")) {
            access |= Opcodes.ACC_FINAL;
        }
        return access;
    }

    private AtWiden() {
    }
}
