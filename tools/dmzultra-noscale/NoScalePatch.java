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
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.LdcInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;

/**
 * Builds dmzultra-0.4.0-noscale.jar from the stock DMZ Ultra 0.4.0 jar: no Overhaul form scaling on
 * either side (server combat or client screens).
 *
 * <ol>
 *   <li>{@code dmzultra.mixins.json}: {@code FormDataScaleMixin} leaves the common {@code mixins} list.
 *   <li>{@code com/dmzultra/mixin/FormDataScaleMixin.class} is deleted.
 *   <li>{@code OverhaulScale.active()} returns false. The class stays (FormsChecks, RealisticProgression,
 *       RealismService and TaimujinRaceMixin call it); {@code scaled} then returns the configured value and
 *       {@code factor}/{@code factorOf} return 1.0. Without this, {@code RealisticProgression.ownerForm}
 *       (on by default) still divides the now-unscaled multiplier by the race factor.
 *   <li>{@code FormsChecks.overhaulScale()} (dev self-test, only with -Ddmzultra.selftest) logs that the
 *       scale is off instead of expecting scaled values.
 *   <li>{@code META-INF/MANIFEST.MF} Build-Time gains " noscale", so Ultra's own boot line reads
 *       "DMZ Ultra 0.4.0 (built ... noscale) is loading." The mod version stays 0.4.0.
 * </ol>
 * Every other entry is copied byte for byte. The stock jar is only read.
 */
public final class NoScalePatch {
    static final String MIXINS = "dmzultra.mixins.json";
    static final String MIXIN = "FormDataScaleMixin";
    static final String MIXIN_CLASS = "com/dmzultra/mixin/FormDataScaleMixin.class";
    static final String SCALE = "com/dmzultra/form/OverhaulScale.class";
    static final String CHECKS = "com/dmzultra/dev/FormsChecks.class";
    static final String MANIFEST = "META-INF/MANIFEST.MF";
    static final String STAMP = " noscale";
    static final String CHECK_LINE =
            "[SelfTest] Forms on Overhaul's scale: off in this build (noscale); forms keep their configured multipliers.";

    public static void main(String[] args) throws IOException {
        if (args.length != 2) {
            System.err.println("usage: NoScalePatch <stock dmzultra-0.4.0.jar> <out dmzultra-0.4.0-noscale.jar>");
            System.exit(2);
        }
        Path in = Path.of(args[0]);
        Path out = Path.of(args[1]);
        if (in.toAbsolutePath().normalize().equals(out.toAbsolutePath().normalize())) {
            throw new IllegalArgumentException("refusing to overwrite the stock jar");
        }
        int copied = 0;
        boolean mixins = false, scale = false, checks = false, manifest = false, removed = false;
        try (ZipFile zip = new ZipFile(in.toFile());
                ZipOutputStream zout = new ZipOutputStream(Files.newOutputStream(out))) {
            Enumeration<? extends ZipEntry> entries = zip.entries();
            while (entries.hasMoreElements()) {
                ZipEntry e = entries.nextElement();
                String name = e.getName();
                byte[] data = read(zip, e);
                switch (name) {
                    case MIXIN_CLASS -> {
                        removed = true;
                        continue;
                    }
                    case MIXINS -> {
                        data = dropMixin(new String(data, StandardCharsets.UTF_8)).getBytes(StandardCharsets.UTF_8);
                        mixins = true;
                    }
                    case SCALE -> {
                        data = patchClass(data, "active", "()Z", NoScalePatch::returnFalse);
                        scale = true;
                    }
                    case CHECKS -> {
                        data = patchClass(data, "overhaulScale", "()V", NoScalePatch::logOff);
                        checks = true;
                    }
                    case MANIFEST -> {
                        data = stamp(new String(data, StandardCharsets.UTF_8)).getBytes(StandardCharsets.UTF_8);
                        manifest = true;
                    }
                    default -> copied++;
                }
                ZipEntry copy = new ZipEntry(name);
                copy.setTime(e.getTime());
                zout.putNextEntry(copy);
                zout.write(data);
                zout.closeEntry();
            }
        }
        if (!(mixins && scale && checks && manifest && removed)) {
            Files.deleteIfExists(out);
            throw new IllegalStateException("stock jar is not the expected 0.4.0 layout: mixins=" + mixins
                    + " scale=" + scale + " checks=" + checks + " manifest=" + manifest + " mixinClass=" + removed);
        }
        System.out.println("patched 5 changes, " + copied + " entries copied unchanged -> " + out);
    }

    /** Remove the mixin name from the "mixins" array, keeping the file's own layout. */
    static String dropMixin(String json) {
        String after = json.replaceFirst("\\s*\"" + MIXIN + "\"\\s*,", "");
        if (after.equals(json)) {
            after = json.replaceFirst(",\\s*\"" + MIXIN + "\"", "");
        }
        if (after.equals(json) || after.contains("\"" + MIXIN + "\"")) {
            throw new IllegalStateException(MIXIN + " not found once in " + MIXINS);
        }
        return after;
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

    interface Body {
        void write(MethodNode m);
    }

    static byte[] patchClass(byte[] data, String method, String desc, Body body) {
        ClassNode node = new ClassNode();
        ClassReader reader = new ClassReader(data);
        reader.accept(node, 0);
        MethodNode target = null;
        for (MethodNode m : node.methods) {
            if (m.name.equals(method) && m.desc.equals(desc)) {
                target = m;
            }
        }
        if (target == null) {
            throw new IllegalStateException(node.name + "." + method + desc + " not found");
        }
        target.instructions = new InsnList();
        target.tryCatchBlocks.clear();
        if (target.localVariables != null) {
            target.localVariables.clear();
        }
        body.write(target);
        // Seeded with the reader: the constant pool keeps its order, so untouched methods stay byte-identical.
        ClassWriter cw = new ClassWriter(reader, 0);
        node.accept(cw);
        return cw.toByteArray();
    }

    static void returnFalse(MethodNode m) {
        m.instructions.add(new InsnNode(Opcodes.ICONST_0));
        m.instructions.add(new InsnNode(Opcodes.IRETURN));
        m.maxStack = 1;
        m.maxLocals = 0;
    }

    static void logOff(MethodNode m) {
        m.instructions.add(new FieldInsnNode(Opcodes.GETSTATIC, "com/dmzultra/DMZUltra", "LOGGER", "Lorg/slf4j/Logger;"));
        m.instructions.add(new LdcInsnNode(CHECK_LINE));
        m.instructions.add(new MethodInsnNode(
                Opcodes.INVOKEINTERFACE, "org/slf4j/Logger", "info", "(Ljava/lang/String;)V", true));
        m.instructions.add(new InsnNode(Opcodes.RETURN));
        m.maxStack = 2;
        m.maxLocals = 0;
    }

    static byte[] read(ZipFile zip, ZipEntry e) throws IOException {
        try (InputStream s = zip.getInputStream(e)) {
            ByteArrayOutputStream b = new ByteArrayOutputStream();
            s.transferTo(b);
            return b.toByteArray();
        }
    }

    private NoScalePatch() {}
}
