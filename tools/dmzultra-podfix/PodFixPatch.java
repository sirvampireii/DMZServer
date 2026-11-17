import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Enumeration;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.LdcInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.VarInsnNode;

/**
 * Builds dmzultra-0.5.7-noscale-placefix-podfix.jar from dmzultra-0.5.7-noscale-placefix.jar (build.sh makes
 * that from the stock jar first). Fixes port voyages (Kanassa, Arlia, the other canon worlds) ending
 * "where they began" when the player leaves the pod during the flight or the wait for the ground:
 *
 * <ol>
 *   <li>{@code PodVoyage.podfixLeftPod(ServerPlayer, Voyage, long)} and the field {@code PODFIX_HELD} are moved
 *       in from the compiled stub ({@code stub/com/dmzultra/multiverse/PodVoyage.java}, which documents them).
 *   <li>{@code PodVoyage.advance}: the one {@code cancel(player, "they left the pod")} (reached when the player is
 *       not alive in their own pod in the world they left from) calls {@code podfixLeftPod(player, voyage, now)}
 *       instead. For any voyage that is not to a port, or a dead player, or one in another world, that is the
 *       same cancel with the same words.
 *   <li>{@code PodVoyage.request(ServerPlayer, Port)}: the landing ticket on the port's world has distance
 *       {@value #PORT_TICKET} instead of 2. The wait for the ground ({@code Port.ready}, which for the canon
 *       worlds is {@code Habitats.ensureBuilt}: 3x3 chunks around five spots up to 24 blocks from the landing)
 *       reads fully loaded chunks out to distance 3; distance 2 left that row unloaded while nobody was on the
 *       planet, so the check never passed and every port voyage waited its full 600 ticks, then dropped the
 *       player from y 150 with Slow Falling. The check itself, the 600-tick limit and that fallback are unchanged.
 *   <li>{@code META-INF/MANIFEST.MF} Build-Time gains " podfix". The mod version stays 0.5.7.
 * </ol>
 * Every other entry is copied byte for byte.
 */
public final class PodFixPatch {
    static final String VOYAGE = "com/dmzultra/multiverse/PodVoyage";
    static final String VOYAGE_CLASS = VOYAGE + ".class";
    static final String INNER = "L" + VOYAGE + "$Voyage;";
    static final String PLAYER = "Lnet/minecraft/server/level/ServerPlayer;";
    static final String HOOK = "podfixLeftPod";
    static final String HOOK_DESC = "(" + PLAYER + INNER + "J)V";
    static final String FIELD = "PODFIX_HELD";
    static final String MANIFEST = "META-INF/MANIFEST.MF";
    static final String MODS_TOML = "META-INF/mods.toml";
    static final String STAMP = " podfix";
    static final String WHY = "they left the pod";
    /** Landing ticket distance for a port voyage (stock 2): full chunks out to 4 cover the ready check's 3. */
    static final int PORT_TICKET = 4;

    public static void main(String[] args) throws IOException {
        if (args.length != 3) {
            System.err.println("usage: PodFixPatch <dmzultra-0.5.7-noscale-placefix.jar> <stub PodVoyage.class> <out jar>");
            System.exit(2);
        }
        Path in = Path.of(args[0]);
        byte[] stub = Files.readAllBytes(Path.of(args[1]));
        Path out = Path.of(args[2]);
        if (in.toAbsolutePath().normalize().equals(out.toAbsolutePath().normalize())) {
            throw new IllegalArgumentException("refusing to overwrite the input jar");
        }
        int copied = 0;
        boolean voyage = false, manifest = false, versioned = false;
        try (ZipFile zip = new ZipFile(in.toFile());
                ZipOutputStream zout = new ZipOutputStream(Files.newOutputStream(out))) {
            Enumeration<? extends ZipEntry> entries = zip.entries();
            while (entries.hasMoreElements()) {
                ZipEntry e = entries.nextElement();
                String name = e.getName();
                byte[] data = read(zip, e);
                if (name.equals(VOYAGE_CLASS)) {
                    data = patchVoyage(data, stub);
                    voyage = true;
                } else if (name.equals(MANIFEST)) {
                    String mf = new String(data, StandardCharsets.UTF_8);
                    if (!mf.contains(" noscale placefix")) {
                        throw new IllegalStateException("input is not a noscale placefix build");
                    }
                    data = stamp(mf).getBytes(StandardCharsets.UTF_8);
                    manifest = true;
                } else {
                    if (name.equals(MODS_TOML)) {
                        versioned = new String(data, StandardCharsets.UTF_8).contains("version=\"0.5.7\"");
                    }
                    copied++;
                }
                ZipEntry copy = new ZipEntry(name);
                copy.setTime(e.getTime());
                zout.putNextEntry(copy);
                zout.write(data);
                zout.closeEntry();
            }
        }
        if (!(voyage && manifest && versioned)) {
            Files.deleteIfExists(out);
            throw new IllegalStateException("input is not the expected 0.5.7 layout: voyage=" + voyage
                    + " manifest=" + manifest + " version=" + versioned);
        }
        System.out.println("patched 2 entries, " + copied + " entries copied unchanged -> " + out);
    }

    static byte[] patchVoyage(byte[] data, byte[] stubData) {
        ClassReader reader = new ClassReader(data);
        ClassNode node = new ClassNode();
        reader.accept(node, 0);
        ClassNode stub = new ClassNode();
        new ClassReader(stubData).accept(stub, 0);
        if (!stub.name.equals(VOYAGE)) {
            throw new IllegalStateException("stub is " + stub.name);
        }
        for (MethodNode m : node.methods) {
            if (m.name.equals(HOOK)) {
                throw new IllegalStateException("PodVoyage already has " + HOOK);
            }
        }
        for (FieldNode f : node.fields) {
            if (f.name.equals(FIELD)) {
                throw new IllegalStateException("PodVoyage already has " + FIELD);
            }
        }
        node.methods.add(find(stub, HOOK, HOOK_DESC));
        FieldNode held = null;
        for (FieldNode f : stub.fields) {
            if (f.name.equals(FIELD)) {
                held = f;
            }
        }
        if (held == null || held.value != null || (held.access & Opcodes.ACC_STATIC) == 0) {
            throw new IllegalStateException("stub " + FIELD + " missing or not a plain static field");
        }
        node.fields.add(held);

        MethodNode advance = find(node, "advance", "(" + PLAYER + "J)V");
        // aload_0; ldc "they left the pod"; invokestatic cancel(ServerPlayer, String)V
        int hits = 0;
        for (AbstractInsnNode i = advance.instructions.getFirst(); i != null; i = i.getNext()) {
            if (i instanceof LdcInsnNode ldc && WHY.equals(ldc.cst)
                    && i.getPrevious() instanceof VarInsnNode p && p.getOpcode() == Opcodes.ALOAD && p.var == 0
                    && i.getNext() instanceof MethodInsnNode call && call.getOpcode() == Opcodes.INVOKESTATIC
                    && call.owner.equals(VOYAGE) && call.name.equals("cancel")
                    && call.desc.equals("(" + PLAYER + "Ljava/lang/String;)V")) {
                InsnList args = new InsnList();
                args.add(new VarInsnNode(Opcodes.ALOAD, 4)); // voyage
                args.add(new VarInsnNode(Opcodes.LLOAD, 1)); // now
                advance.instructions.insertBefore(ldc, args);
                advance.instructions.remove(ldc);
                call.name = HOOK;
                call.desc = HOOK_DESC;
                hits++;
                break;
            }
        }
        if (hits != 1) {
            throw new IllegalStateException("cancel(player, \"" + WHY + "\") not found in PodVoyage.advance");
        }
        assertLocal(advance, 4, INNER, "voyage");
        assertLocal(advance, 1, "J", "now");
        advance.maxStack = Math.max(advance.maxStack, 4);

        // request(player, port): the landing ticket loads full chunks out to distance 2, but the ready check
        // (Habitats.ensureBuilt: 3x3 chunks around spots 24 blocks out) reads chunks out to distance 3, so it
        // never passed while nobody was on the planet and every port voyage waited out its 600 ticks.
        MethodNode port = find(node, "request", "(" + PLAYER + "L" + VOYAGE + "$Port;)Z");
        int tickets = 0;
        for (AbstractInsnNode i = port.instructions.getFirst(); i != null; i = i.getNext()) {
            if (i instanceof org.objectweb.asm.tree.FieldInsnNode f && f.getOpcode() == Opcodes.GETSTATIC
                    && f.name.equals("LANDING_TICKET")
                    && f.getNext() instanceof VarInsnNode a && a.getOpcode() == Opcodes.ALOAD
                    && a.getNext() instanceof org.objectweb.asm.tree.InsnNode two && two.getOpcode() == Opcodes.ICONST_2
                    && two.getNext() instanceof VarInsnNode b && b.getOpcode() == Opcodes.ALOAD && b.var == a.var
                    && b.getNext() instanceof MethodInsnNode add && add.name.equals("m_8387_")) {
                port.instructions.set(two, new org.objectweb.asm.tree.InsnNode(Opcodes.ICONST_0 + PORT_TICKET));
                tickets++;
            }
        }
        if (tickets != 1) {
            throw new IllegalStateException("expected one LANDING_TICKET distance 2 in request(player, port), found " + tickets);
        }
        ClassWriter cw = new ClassWriter(reader, 0);
        node.accept(cw);
        return cw.toByteArray();
    }

    /** The slot really holds what the patch loads from it (from the stock LocalVariableTable). */
    static void assertLocal(MethodNode m, int slot, String desc, String name) {
        if (m.localVariables == null || m.localVariables.stream().noneMatch(
                v -> v.index == slot && v.desc.equals(desc) && v.name.equals(name))) {
            throw new IllegalStateException("advance slot " + slot + " is not " + name + " " + desc);
        }
    }

    static MethodNode find(ClassNode node, String name, String desc) {
        MethodNode found = null;
        for (MethodNode m : node.methods) {
            if (m.name.equals(name) && m.desc.equals(desc)) {
                if (found != null) {
                    throw new IllegalStateException(node.name + "." + name + desc + " found twice");
                }
                found = m;
            }
        }
        if (found == null) {
            throw new IllegalStateException(node.name + "." + name + desc + " not found");
        }
        return found;
    }

    static String stamp(String mf) {
        String nl = mf.contains("\r\n") ? "\r\n" : "\n";
        StringBuilder sb = new StringBuilder();
        boolean done = false;
        for (String line : mf.split("\r?\n", -1)) {
            if (line.startsWith("Build-Time: ") && !line.endsWith(STAMP)) {
                line = line + STAMP;
                done = true;
            }
            sb.append(line).append(nl);
        }
        if (!done) {
            throw new IllegalStateException("no Build-Time line in " + MANIFEST);
        }
        return sb.substring(0, sb.length() - nl.length());
    }

    static byte[] read(ZipFile zip, ZipEntry e) throws IOException {
        try (InputStream s = zip.getInputStream(e)) {
            ByteArrayOutputStream b = new ByteArrayOutputStream();
            s.transferTo(b);
            return b.toByteArray();
        }
    }

    private PodFixPatch() {}
}
