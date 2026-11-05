import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;

public class RunCheck {
    public static void main(String[] args) throws Exception {
        Path game = Files.createTempDirectory("dukgame");
        Method load = Class.forName("net.minecraftforge.fml.loading.FMLPaths").getMethod("loadAbsolutePaths", Path.class);
        load.invoke(null, game);
        for (String name : args) {
            Class<?> c = Class.forName("com.dmzlegacy.ultimatekey.empires." + name);
            Method run = c.getDeclaredMethod("run");
            run.setAccessible(true);
            System.out.println(name + " assertions passed: " + run.invoke(null));
        }
    }
}
