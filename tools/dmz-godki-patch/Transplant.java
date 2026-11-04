import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.MethodNode;

/**
 * Transplant <orig.class> <patched.class> <out.class> <method,method,...>
 *
 * Starts from the original class bytes. Each listed method (by name) is replaced with the patched
 * version; every method and field that exists only in the patched class is added. All other
 * methods, fields and attributes stay as the original compiled them.
 */
public class Transplant {
    public static void main(String[] args) throws Exception {
        ClassNode orig = read(Path.of(args[0]));
        ClassNode patched = read(Path.of(args[1]));
        Set<String> replace = new HashSet<>(List.of(args[3].split(",")));

        Set<String> origKeys = new HashSet<>();
        for (MethodNode m : orig.methods) {
            origKeys.add(m.name + m.desc);
        }
        int replaced = 0;
        for (int i = 0; i < orig.methods.size(); i++) {
            MethodNode m = orig.methods.get(i);
            if (!replace.contains(m.name)) {
                continue;
            }
            MethodNode p = find(patched, m.name, m.desc);
            if (p == null) {
                throw new IllegalStateException("patched class lacks " + m.name + m.desc);
            }
            orig.methods.set(i, p);
            replaced++;
            System.out.println("replaced " + m.name + m.desc);
        }
        if (replaced != replace.size()) {
            throw new IllegalStateException("replaced " + replaced + " of " + replace.size());
        }
        for (MethodNode p : patched.methods) {
            if (!origKeys.contains(p.name + p.desc)) {
                if (p.name.startsWith("lambda$")) {
                    throw new IllegalStateException("new lambda would need renumbering: " + p.name);
                }
                orig.methods.add(p);
                System.out.println("added " + p.name + p.desc);
            }
        }
        Set<String> fields = new HashSet<>();
        for (FieldNode f : orig.fields) {
            fields.add(f.name);
        }
        for (FieldNode f : patched.fields) {
            if (!fields.contains(f.name)) {
                orig.fields.add(f);
                System.out.println("added field " + f.name);
            }
        }
        ClassWriter w = new ClassWriter(0);
        orig.accept(w);
        Files.write(Path.of(args[2]), w.toByteArray());
    }

    private static ClassNode read(Path p) throws Exception {
        ClassNode n = new ClassNode();
        new ClassReader(Files.readAllBytes(p)).accept(n, 0);
        return n;
    }

    private static MethodNode find(ClassNode n, String name, String desc) {
        for (MethodNode m : n.methods) {
            if (m.name.equals(name) && m.desc.equals(desc)) {
                return m;
            }
        }
        return null;
    }
}
