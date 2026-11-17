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
import org.objectweb.asm.tree.FrameNode;
import org.objectweb.asm.tree.IincInsnNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.IntInsnNode;
import org.objectweb.asm.tree.JumpInsnNode;
import org.objectweb.asm.tree.LabelNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.VarInsnNode;

/**
 * Builds dmzultra-0.5.7-noscale-placefix.jar from dmzultra-0.5.7-noscale.jar (build.sh makes that from
 * the stock jar first). Fixes Karakura Town never finding ground on a modded world:
 *
 * <ol>
 *   <li>Adds {@code com/dmzultra/r3/bleachworld/places/PlaceGroundFix.class} (source next to this file).
 *   <li>{@code PlaceGround.vegetation(BlockState)} and {@code PlaceGround.natural(BlockState)} start with
 *       {@code if (PlaceGroundFix.plant|ground(state)) return true;}; the rest of each method is unchanged.
 *       So Pam's crops, gardens and fruit, modded plants, and Tinkers' slime grass, dirt and congealed slime
 *       stop counting as a player's build in {@code PlaceGround.ground}.
 *   <li>{@code PlaceBuilder.scan}: a spot is refused as built after {@value #BUILT_MAX} built columns instead of
 *       3, so a stray block nobody classified does not refuse a whole town site; a real base still does.
 *       {@code PlaceBuilder.BUILT_MAX} (a constant only {@code scan} inlines) is set to match.
 *   <li>{@code META-INF/MANIFEST.MF} Build-Time gains " placefix". The mod version stays 0.5.7.
 * </ol>
 * Every other entry is copied byte for byte. The ring, survey cadence, forced path (including its built
 * check), the two-phase build and StructureGuard are untouched.
 */
public final class PlaceFixPatch {
    static final String GROUND = "com/dmzultra/r3/bleachworld/places/PlaceGround.class";
    static final String BUILDER = "com/dmzultra/r3/bleachworld/places/PlaceBuilder.class";
    static final String FIX = "com/dmzultra/r3/bleachworld/places/PlaceGroundFix";
    static final String MANIFEST = "META-INF/MANIFEST.MF";
    static final String MODS_TOML = "META-INF/mods.toml";
    static final String STATE = "Lnet/minecraft/world/level/block/state/BlockState;";
    static final String SCAN_DESC =
            "(Lnet/minecraft/server/MinecraftServer;Lnet/minecraft/server/level/ServerLevel;"
                    + "Lcom/dmzultra/r3/bleachworld/places/PlaceSite;IIZZ)Ljava/lang/String;";
    static final String STAMP = " placefix";
    static final int STOCK_BUILT_MAX = 3;
    static final int BUILT_MAX = 8;

    public static void main(String[] args) throws IOException {
        if (args.length != 3) {
            System.err.println("usage: PlaceFixPatch <dmzultra-0.5.7-noscale.jar> <PlaceGroundFix.class> <out jar>");
            System.exit(2);
        }
        Path in = Path.of(args[0]);
        byte[] fix = Files.readAllBytes(Path.of(args[1]));
        Path out = Path.of(args[2]);
        if (in.toAbsolutePath().normalize().equals(out.toAbsolutePath().normalize())) {
            throw new IllegalArgumentException("refusing to overwrite the input jar");
        }
        int copied = 0;
        boolean ground = false, builder = false, manifest = false, versioned = false;
        long time = 0;
        try (ZipFile zip = new ZipFile(in.toFile());
                ZipOutputStream zout = new ZipOutputStream(Files.newOutputStream(out))) {
            Enumeration<? extends ZipEntry> entries = zip.entries();
            while (entries.hasMoreElements()) {
                ZipEntry e = entries.nextElement();
                String name = e.getName();
                byte[] data = read(zip, e);
                if (name.equals(FIX + ".class")) {
                    throw new IllegalStateException(name + " is already in the input jar");
                }
                switch (name) {
                    case GROUND -> {
                        data = patchGround(data);
                        ground = true;
                        time = e.getTime();
                    }
                    case BUILDER -> {
                        data = patchBuilder(data);
                        builder = true;
                    }
                    case MANIFEST -> {
                        String mf = new String(data, StandardCharsets.UTF_8);
                        if (!mf.contains(" noscale")) {
                            throw new IllegalStateException("input is not a noscale build (no \" noscale\" in Build-Time)");
                        }
                        data = stamp(mf).getBytes(StandardCharsets.UTF_8);
                        manifest = true;
                    }
                    default -> {
                        if (name.equals(MODS_TOML)) {
                            versioned = new String(data, StandardCharsets.UTF_8).contains("version=\"0.5.7\"");
                        }
                        copied++;
                    }
                }
                put(zout, name, e.getTime(), data);
                if (name.equals(GROUND)) {
                    put(zout, FIX + ".class", time, fix);
                }
            }
        }
        if (!(ground && builder && manifest && versioned)) {
            Files.deleteIfExists(out);
            throw new IllegalStateException("input is not the expected 0.5.7 noscale layout: ground=" + ground
                    + " builder=" + builder + " manifest=" + manifest + " version=" + versioned);
        }
        System.out.println("patched 3 entries, added PlaceGroundFix, " + copied + " entries copied unchanged -> " + out);
    }

    static void put(ZipOutputStream zout, String name, long time, byte[] data) throws IOException {
        ZipEntry copy = new ZipEntry(name);
        copy.setTime(time);
        zout.putNextEntry(copy);
        zout.write(data);
        zout.closeEntry();
    }

    static byte[] patchGround(byte[] data) {
        ClassReader reader = new ClassReader(data);
        ClassNode node = new ClassNode();
        reader.accept(node, 0);
        prefix(node, "vegetation", "plant");
        prefix(node, "natural", "ground");
        ClassWriter cw = new ClassWriter(reader, 0);
        node.accept(cw);
        return cw.toByteArray();
    }

    /**
     * {@code if (PlaceGroundFix.<helper>(state)) return true;} in front of {@code <method>(BlockState)Z}.
     * The frame after the jump is F_SAME: same locals as the method entry, empty stack.
     */
    static void prefix(ClassNode node, String method, String helper) {
        MethodNode target = find(node, method, "(" + STATE + ")Z");
        LabelNode rest = new LabelNode();
        InsnList pre = new InsnList();
        pre.add(new VarInsnNode(Opcodes.ALOAD, 0));
        pre.add(new MethodInsnNode(Opcodes.INVOKESTATIC, FIX, helper, "(" + STATE + ")Z", false));
        pre.add(new JumpInsnNode(Opcodes.IFEQ, rest));
        pre.add(new InsnNode(Opcodes.ICONST_1));
        pre.add(new InsnNode(Opcodes.IRETURN));
        pre.add(rest);
        pre.add(new FrameNode(Opcodes.F_SAME, 0, null, 0, null));
        target.instructions.insert(pre);
        target.maxStack = Math.max(target.maxStack, 1);
    }

    static byte[] patchBuilder(byte[] data) {
        ClassReader reader = new ClassReader(data);
        ClassNode node = new ClassNode();
        reader.accept(node, 0);
        boolean field = false;
        for (FieldNode f : node.fields) {
            if (f.name.equals("BUILT_MAX")) {
                if (!Integer.valueOf(STOCK_BUILT_MAX).equals(f.value)) {
                    throw new IllegalStateException("PlaceBuilder.BUILT_MAX is " + f.value + ", not " + STOCK_BUILT_MAX);
                }
                f.value = BUILT_MAX;
                field = true;
            }
        }
        if (!field) {
            throw new IllegalStateException("PlaceBuilder.BUILT_MAX not found");
        }
        MethodNode scan = find(node, "scan", SCAN_DESC);
        // ++built > 3  ==  iinc v 1; iload v; iconst_3; if_icmple
        int hits = 0;
        for (AbstractInsnNode i = scan.instructions.getFirst(); i != null; i = i.getNext()) {
            if (i instanceof IincInsnNode inc && inc.incr == 1
                    && inc.getNext() instanceof VarInsnNode load && load.getOpcode() == Opcodes.ILOAD && load.var == inc.var
                    && load.getNext() instanceof InsnNode three && three.getOpcode() == Opcodes.ICONST_3
                    && three.getNext() instanceof JumpInsnNode jump && jump.getOpcode() == Opcodes.IF_ICMPLE) {
                scan.instructions.set(three, new IntInsnNode(Opcodes.BIPUSH, BUILT_MAX));
                hits++;
            }
        }
        if (hits != 1) {
            throw new IllegalStateException("expected one '++built > 3' in PlaceBuilder.scan, found " + hits);
        }
        ClassWriter cw = new ClassWriter(reader, 0);
        node.accept(cw);
        return cw.toByteArray();
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

    private PlaceFixPatch() {}
}
