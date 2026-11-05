import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.commons.ClassRemapper;
import org.objectweb.asm.commons.SimpleRemapper;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.AnnotationNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.LdcInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;

/**
 * Moves DUK's empires layer from Noea onto DMZ Ultra, in the built jar.
 *
 * usage: NoeaToUltra <in.jar> <out.jar> <noea-to-ultra.txt>
 *
 * 1. Every class that names a Noea class is remapped with the class table (Ultra classes, or DUK's
 *    empires/ultra facades). Annotation strings (mixin targets) are rewritten with the same table.
 * 2. Mixins with no Ultra target are deleted and taken out of the empires mixin config: bounty
 *    erasure (Noea DivineImmortalityEvents), bounty grab (Noea HakaiGrabService.start(player, bool)),
 *    and the bounty hakai ledger (Noea DestroyerRoleService.canDamageWithHakaiProjectile).
 * 3. The planet destruction ledger mixin follows the code to Ultra's GalaxyWave2.
 * 4. The empires layer requires dmzultra instead of noeabosses.
 * 5. Live destroyer term maintenance is switched off: Ultra's Destroyer Path owns the seats.
 * Any class or text entry still naming com/butterjaffa afterwards fails the build.
 */
public final class NoeaToUltra {
    private static final String MIXIN_PKG = "com/dmzlegacy/ultimatekey/empires/mixin/";
    private static final Set<String> DROPPED_MIXINS = Set.of("BountyErasureMixin", "BountyGrabMixin", "DestroyerHakaiLedgerMixin");
    private static final String LEDGER_MIXIN = MIXIN_PKG + "DeepSpaceDestructionLedgerMixin";
    private static final String GALAXY_WAVE = "com/dmzultra/space/catalogue/GalaxyWave2";
    private static final String EMPIRES_JSON = "dmzultimatekey.empires.mixins.json";
    private static final String UNIVERSE_MANAGEMENT = "com/dmzlegacy/ultimatekey/empires/UniverseManagement";

    public static void main(String[] args) throws Exception {
        Map<String, String> table = new LinkedHashMap<>();
        for (String line : Files.readAllLines(Path.of(args[2]), StandardCharsets.UTF_8)) {
            line = line.split("#", 2)[0].trim();
            if (!line.isEmpty()) {
                String[] p = line.split("\\s+");
                table.put(p[0], p[1]);
            }
        }
        SimpleRemapper remapper = new SimpleRemapper(table);
        int remapped = 0;
        List<String> problems = new ArrayList<>();
        try (ZipFile in = new ZipFile(args[0]);
             ZipOutputStream out = new ZipOutputStream(Files.newOutputStream(Path.of(args[1])))) {
            for (ZipEntry e : Collections.list(in.entries())) {
                String name = e.getName();
                byte[] bytes = in.getInputStream(e).readAllBytes();
                if (name.endsWith(".class")) {
                    String cls = name.substring(0, name.length() - 6);
                    if (cls.startsWith(MIXIN_PKG) && DROPPED_MIXINS.contains(cls.substring(MIXIN_PKG.length()))) {
                        System.out.println("dropped mixin " + cls);
                        continue;
                    }
                    if (contains(bytes, "com/butterjaffa") || contains(bytes, "noeabosses")) {
                        bytes = rewrite(bytes, cls, remapper, table);
                        remapped++;
                    }
                    if (cls.equals(UNIVERSE_MANAGEMENT)) {
                        bytes = skipLiveTermMaintenance(bytes);
                    }
                } else if (name.equals(EMPIRES_JSON)) {
                    String json = new String(bytes, StandardCharsets.UTF_8);
                    for (String m : DROPPED_MIXINS) {
                        String before = json;
                        json = json.replaceAll("\\s*\"" + m + "\",?", "");
                        if (json.equals(before)) {
                            problems.add(EMPIRES_JSON + " does not list " + m);
                        }
                    }
                    json = json.replaceAll(",(\\s*\\])", "$1");
                    bytes = json.getBytes(StandardCharsets.UTF_8);
                }
                if (contains(bytes, "com/butterjaffa") || contains(bytes, "com.butterjaffa")) {
                    problems.add(name + " still names com/butterjaffa");
                }
                ZipEntry copy = new ZipEntry(name);
                copy.setTime(e.getTime());
                out.putNextEntry(copy);
                out.write(bytes);
                out.closeEntry();
            }
        }
        if (!problems.isEmpty()) {
            problems.forEach(p -> System.err.println("ERROR: " + p));
            System.exit(1);
        }
        System.out.println("remapped " + remapped + " classes from Noea to DMZ Ultra");
    }

    private static byte[] rewrite(byte[] bytes, String cls, SimpleRemapper remapper, Map<String, String> table) {
        ClassNode node = new ClassNode();
        new ClassReader(bytes).accept(new ClassRemapper(node, remapper), 0);
        boolean ledger = cls.equals(LEDGER_MIXIN);
        for (List<AnnotationNode> list : annotationLists(node)) {
            for (AnnotationNode a : list) {
                rewriteAnnotation(a, table, ledger);
            }
        }
        for (MethodNode m : node.methods) {
            for (AbstractInsnNode insn : m.instructions.toArray()) {
                if (insn instanceof LdcInsnNode ldc && "noeabosses".equals(ldc.cst)) {
                    ldc.cst = "dmzultra";  // the empires layer's required-mod list
                } else if (insn instanceof LdcInsnNode ldc && ldc.cst instanceof String s && s.contains("com.butterjaffa.")) {
                    // reflective lookups (Class.forName) use dotted names
                    String dotted = table.get(s.replace('.', '/'));
                    if (dotted == null) {
                        throw new IllegalStateException(cls + ": no mapping for reflective name " + s);
                    }
                    ldc.cst = dotted.replace('/', '.');
                }
            }
        }
        ClassWriter w = new ClassWriter(0);
        node.accept(w);
        return w.toByteArray();
    }

    private static List<List<AnnotationNode>> annotationLists(ClassNode node) {
        List<List<AnnotationNode>> out = new ArrayList<>();
        if (node.invisibleAnnotations != null) {
            out.add(node.invisibleAnnotations);
        }
        if (node.visibleAnnotations != null) {
            out.add(node.visibleAnnotations);
        }
        for (MethodNode m : node.methods) {
            if (m.invisibleAnnotations != null) {
                out.add(m.invisibleAnnotations);
            }
            if (m.visibleAnnotations != null) {
                out.add(m.visibleAnnotations);
            }
        }
        return out;
    }

    @SuppressWarnings("unchecked")
    private static void rewriteAnnotation(AnnotationNode a, Map<String, String> table, boolean ledger) {
        if (a.values == null) {
            return;
        }
        boolean mixin = a.desc.equals("Lorg/spongepowered/asm/mixin/Mixin;");
        for (int i = 1; i < a.values.size(); i += 2) {
            String key = (String)a.values.get(i - 1);
            Object v = a.values.get(i);
            if (ledger && mixin && key.equals("value")) {
                a.values.set(i, new ArrayList<>(List.of(Type.getObjectType(GALAXY_WAVE))));
            } else {
                a.values.set(i, rewriteValue(v, table, ledger));
            }
        }
    }

    @SuppressWarnings("unchecked")
    private static Object rewriteValue(Object v, Map<String, String> table, boolean ledger) {
        if (v instanceof String s) {
            for (Map.Entry<String, String> m : table.entrySet()) {
                s = s.replace("L" + m.getKey() + ";", "L" + m.getValue() + ";");
            }
            return s;
        }
        if (v instanceof AnnotationNode nested) {
            rewriteAnnotation(nested, table, ledger);
            return nested;
        }
        if (v instanceof List<?> list) {
            List<Object> copy = new ArrayList<>();
            for (Object o : list) {
                copy.add(rewriteValue(o, table, ledger));
            }
            return copy;
        }
        return v;
    }

    /**
     * UniverseManagement.maintenance runs DUK's destroyer terms (term end, demotion, succession) on
     * the live seats. Ultra owns those seats now, so the live call is dropped; the self-tests still
     * call maintainAssignments on their own in-memory registry.
     */
    private static byte[] skipLiveTermMaintenance(byte[] bytes) {
        ClassNode node = new ClassNode();
        new ClassReader(bytes).accept(node, 0);
        int dropped = 0;
        for (MethodNode m : node.methods) {
            if (!m.name.equals("maintenance")) {
                continue;
            }
            for (AbstractInsnNode insn : m.instructions.toArray()) {
                if (insn instanceof MethodInsnNode call && call.getOpcode() == Opcodes.INVOKESTATIC
                        && call.owner.equals(UNIVERSE_MANAGEMENT) && call.name.equals("maintainAssignments")) {
                    InsnList pops = new InsnList();
                    Type[] args = Type.getArgumentTypes(call.desc);
                    for (int i = args.length - 1; i >= 0; i--) {
                        pops.add(new InsnNode(args[i].getSize() == 2 ? Opcodes.POP2 : Opcodes.POP));
                    }
                    m.instructions.insert(call, pops);
                    m.instructions.remove(call);
                    dropped++;
                }
            }
        }
        if (dropped != 1) {
            throw new IllegalStateException("UniverseManagement.maintenance: expected 1 maintainAssignments call, found " + dropped);
        }
        ClassWriter w = new ClassWriter(0);
        node.accept(w);
        System.out.println("live destroyer term maintenance switched off (Ultra owns the seats)");
        return w.toByteArray();
    }

    private static boolean contains(byte[] haystack, String needle) {
        byte[] n = needle.getBytes(StandardCharsets.UTF_8);
        outer:
        for (int i = 0; i + n.length <= haystack.length; i++) {
            for (int j = 0; j < n.length; j++) {
                if (haystack[i + j] != n[j]) {
                    continue outer;
                }
            }
            return true;
        }
        return false;
    }

    private NoeaToUltra() {
    }
}
