import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import javax.imageio.ImageIO;

/** GuiKit's test: the font's real widths, Minecraft's GUI scales, and each fault it promises planted and caught. Takes an empty work folder. */
public final class GuiKitTest {
    private static int passed, failed;
    private static File work;

    private record Result(int code, String text) {}

    private static void ok(boolean good, String what, Object detail) {
        if (good) passed++;
        else {
            failed++;
            System.out.println("FAILED: " + what + " -> " + detail);
        }
    }

    private static Result kit(String... a) throws Exception {
        PrintStream was = System.out;
        ByteArrayOutputStream got = new ByteArrayOutputStream();
        int code;
        try (PrintStream to = new PrintStream(got, true, StandardCharsets.UTF_8)) {
            System.setOut(to);
            try {
                code = GuiKit.run(a);
            } catch (IllegalArgumentException e) {
                to.println(e.getMessage());
                code = 2;
            }
        } finally {
            System.setOut(was);
        }
        return new Result(code, got.toString(StandardCharsets.UTF_8));
    }

    private static void text(String path, String s) throws Exception {
        File f = new File(work, path);
        Files.createDirectories(f.getParentFile().toPath());
        Files.writeString(f.toPath(), s.replace('\'', '"'));
    }

    private static Result make(String name, String spec, String... more) throws Exception {
        text("mod/tools/guikit.json", spec);
        List<String> a = new ArrayList<>(List.of("make", new File(work, "mod").getPath(), "-out", new File(work, "out/" + name).getPath()));
        a.addAll(List.of(more));
        return kit(a.toArray(String[]::new));
    }

    private static String screen(String elements) {
        return "{'sizes':['1280x720'],'scales':[3],'screens':[{'id':'s','elements':[" + elements + "]}]}";
    }

    private static void says(String name, String elements, int code, String... words) throws Exception {
        Result r = make(name, screen(elements));
        boolean all = r.code() == code;
        for (String w : words) all &= r.text().contains(w);
        ok(all, name, r);
    }

    private static String box(String extra) {
        return "{'type':'panel','anchor':'center','w':200,'h':120,'children':[" + extra + "]}";
    }

    public static void main(String[] args) throws Exception {
        System.setProperty("java.awt.headless", "true");
        work = new File(args[0]).getAbsoluteFile();
        Files.createDirectories(work.toPath());

        Result r0;
        McFont font = McFont.load(new File(System.getProperty("user.home"), "curseforge/minecraft/Install"));
        String[] one = {"A", "i", "l", " ", "I", "t", "f", "k", "@", "!", "é"};
        int[] wide = {6, 2, 3, 4, 4, 4, 5, 5, 7, 2, 6};
        for (int i = 0; i < one.length; i++) ok(font.width(one[i]) == wide[i], "width of '" + one[i] + "'", font.width(one[i]));
        ok(font.width("Minecraft") == 47, "width of Minecraft", font.width("Minecraft"));
        ok(font.width("§lA") == 7, "bold is a pixel wider", font.width("§lA"));
        ok(font.width("§cRed") == font.width("Red"), "a colour code takes no room", font.width("§cRed"));
        ok(font.width("忍") == 9, "kanji come from unifont", font.width("忍"));
        ok(font.missing.isEmpty(), "no glyph missing", font.missing);
        List<String> wrapped = font.wrap("one two three four five six seven", 50);
        boolean fits = true;
        for (String l : wrapped) fits &= font.width(l) <= 50;
        ok(fits && wrapped.size() > 2 && String.join(" ", wrapped).equals("one two three four five six seven"), "wrapping keeps every word", wrapped);
        ok(font.wrap("Supercalifragilistic", 30).stream().allMatch(l -> font.width(l) <= 30), "a long word is cut", "wider");

        int[][] scales = {{1920, 1080, 0, 4}, {1920, 1080, 2, 2}, {1280, 720, 0, 3}, {854, 480, 3, 2}, {2560, 1440, 0, 6}, {1366, 768, 4, 3}, {3840, 2160, 0, 9}};
        for (int[] s : scales) ok(GuiKit.scale(s[0], s[1], s[2]) == s[3], "GUI scale " + s[0] + "x" + s[1] + " wanted " + s[2], GuiKit.scale(s[0], s[1], s[2]));

        GuiPaint paint = new GuiPaint(40, 40, 2);
        paint.fill(5, 5, 10, 10, 0xFFFF0000);
        BufferedImage painted = paint.image();
        ok((painted.getRGB(10, 10) & 0xFFFFFF) == 0xFF0000 && (painted.getRGB(19, 19) & 0xFFFFFF) == 0xFF0000 && (painted.getRGB(20, 20) & 0xFFFFFF) == 0,
                "whole pixels at the scale", Integer.toHexString(painted.getRGB(10, 10)));

        Result demo = kit("demo", new File(work, "demo").getPath());
        ok(demo.code() == 0 && demo.text().contains("5 screens") && new File(work, "demo/journal_sheet.png").isFile() && new File(work, "demo/index.html").isFile(), "the demo", demo);

        text("mod/tools/toolkit.json", "{'modid':'testmod','name':'Test'}");
        text("mod/src/main/resources/assets/testmod/lang/en_us.json", "{'quest.testmod.a.name':'Short','quest.testmod.b.name':'A very very very long quest name','gui.testmod.cost':'%s%% Mana','gui.testmod.two':'%2$s of %1$s'}");
        BufferedImage icon = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
        for (int y = 2; y < 14; y++) for (int x = 2; x < 14; x++) icon.setRGB(x, y, 0xFF50C0F0);
        File png = new File(work, "mod/src/main/resources/assets/testmod/textures/gui/icon.png");
        Files.createDirectories(png.getParentFile().toPath());
        ImageIO.write(icon, "png", png);

        says("good", box("{'type':'text','text':'Hello','x':6,'y':6},{'type':'button','text':'Done','anchor':'bottom_right','x':-6,'y':-6},"
                + "{'type':'slot','x':6,'y':20,'icon':'testmod:textures/gui/icon.png'},{'type':'image','texture':'testmod:textures/gui/icon.png','x':30,'y':20,'w':32,'h':32}"), 0, "0 errors, 0 warnings");
        Result pic = make("good2", screen("{'type':'box','anchor':'top_left','w':10,'h':10,'colour':'#ff0000'}"));
        BufferedImage shot = ImageIO.read(new File(work, "out/good2/s_1280x720_s3.png"));
        ok(pic.code() == 0 && shot.getWidth() == 1280 && (shot.getRGB(29, 29) & 0xFFFFFF) == 0xFF0000 && (shot.getRGB(30, 30) & 0xFFFFFF) != 0xFF0000,
                "GUI pixels land on the screen's", pic);

        // placed as the game would
        says("off the screen", "{'type':'text','text':'Hello','anchor':'center','y':-130}", 1, "off the screen");
        says("only off when small", "{'type':'text','text':'Hello','anchor':'center','y':-100}", 0);
        says("out of its parent", box("{'type':'button','text':'Done','x':150,'y':6,'w':80}"), 1, "reaches out of");
        says("scrolling may run on", "{'type':'panel','anchor':'center','w':200,'h':60,'scroll':true,'children':[{'type':'text','text':'below','x':4,'y':80}]}", 0);
        says("on top of each other", box("{'type':'button','text':'One','x':6,'y':6,'w':80},{'type':'button','text':'Two','x':60,'y':10,'w':80}"), 1, "on top of each other");
        says("over is allowed", box("{'type':'button','text':'One','x':6,'y':6,'w':80},{'type':'text','text':'!','x':60,'y':10,'over':true}"), 0);
        says("too wide", box("{'type':'text','text':'This line is much too long for it','x':6,'y':6,'w':60}"), 1, "wider than its box");
        says("wrapping fits it", box("{'type':'text','text':'This line is much too long for it','x':6,'y':6,'w':60,'wrap':true}"), 0);
        says("too many lines", box("{'type':'text','text':'This line is much too long for it','x':6,'y':6,'w':60,'wrap':true,'maxLines':2}"), 1, "room for 2");
        says("button text", box("{'type':'button','text':'Accept the quest','x':6,'y':6,'w':50}"), 1, "doesn't fit the button");
        says("tab text", box("{'type':'tabs','items':['Attributes','Spellbook'],'x':2,'y':2,'w':80}"), 1, "doesn't fit");
        says("list rows", box("{'type':'list','items':['a','b','c','d','e'],'x':2,'y':2,'w':80,'h':30}"), 1, "room for 2");
        says("list scrolls", box("{'type':'list','items':['a','b','c','d','e'],'x':2,'y':2,'w':80,'h':30,'scroll':true}"), 0);
        says("list row width", box("{'type':'list','items':['A rather long row of text'],'x':2,'y':2,'w':50,'h':30}"), 1, "too wide");
        says("wheel crowded", "{'type':'wheel','anchor':'center','rings':[{'inner':10,'outer':30,'count':12}]}", 1, "icons don't fit");
        says("wheel thin", "{'type':'wheel','anchor':'center','rings':[{'inner':40,'outer':50,'count':4}]}", 1, "thinner than an icon");
        says("wheel fine", "{'type':'wheel','anchor':'center','rings':[{'inner':30,'outer':70,'count':10}]}", 0);
        says("small to click", box("{'type':'slot','x':4,'y':4,'w':10,'h':10}"), 0, "under 12 px to click");
        says("unreadable", "{'type':'panel','style':'parchment','anchor':'center','w':150,'h':60,'children':[{'type':'text','text':'pale','x':6,'y':6,'colour':'#e8e0c8'}]}", 0, "hard to read");
        says("uneven picture", box("{'type':'image','texture':'testmod:textures/gui/icon.png','x':4,'y':4,'w':24,'h':24}"), 0, "uneven pixels");

        // LegacyMechanics skins (style "lm"): its own rules, planted and caught
        String lm = "{'type':'panel','style':'lm','w':300,'h':150,'title':'Sparring','accent':'spar','children':[";
        says("lm good", lm + "{'type':'text','text':'§6§lHeader §fbody','x':10,'y':46,'w':280},"
                + "{'type':'button','text':'Back to Spar Board','x':10,'y':100,'w':135},{'type':'button','text':'Main menu','x':155,'y':100,'w':135},"
                + "{'type':'bar','x':10,'y':60,'w':120,'value':0.5},{'type':'field','placeholder':'Name','x':10,'y':76,'w':135},"
                + "{'type':'swatch','skin':'zeno_omni','text':'Sample text','x':155,'y':76,'w':135,'h':16}]}", 0, "0 errors, 0 warnings");
        says("lm padding", lm + "{'type':'button','text':'Accept the quest now','x':10,'y':60,'w':100}]}", 1, "inside its 8 px padding");
        says("lm 80 px", lm + "{'type':'button','text':'Go','x':10,'y':60,'w':60}]}", 0, "under LM's 80 px");
        says("lm Hub", lm + "{'type':'button','text':'Back to Hub','x':10,'y':60,'w':135}]}", 1, "says Hub");
        says("lm bare Back", lm + "{'type':'button','text':'Back','x':10,'y':60,'w':135}]}", 1, "bare Back");
        says("lm raw id", lm + "{'type':'button','text':'open spar_board','x':10,'y':60,'w':135}]}", 1, "raw id");
        says("lm raw id in text", lm + "{'type':'text','text':'pot=ON','x':10,'y':60}]}", 1, "raw id");
        says("lm footer", lm + "{'type':'button','text':'Main menu','x':10,'y':100,'w':135},{'type':'button','text':'Back to Home','x':155,'y':100,'w':135}]}", 1,
                "is not bottom right beside");
        says("lm lone Back", lm + "{'type':'button','text':'Back to main menu','x':10,'y':100,'w':135}]}", 1, "not at the bottom right");
        says("lm under the footer", lm + "{'type':'button','text':'Back to main menu','x':155,'y':100,'w':135},{'type':'button','text':'Extra','x':10,'y':120,'w':135}]}", 1,
                "under the Back footer");
        says("lm border margin", lm + "{'type':'button','text':'Wide','x':200,'y':60,'w':96}]}", 1, "margin of the panel's right border");
        says("lm shrunk to click", "{'type':'panel','style':'lm','w':300,'h':400,'scroll':true,'children':[{'type':'button','text':'Tap','x':10,'y':60,'w':100}]}", 0,
                "the menu is shrunk");
        says("lm floor scrolls", "{'type':'panel','style':'lm','w':300,'h':400,'minScale':0.75,'scroll':true,'children':[{'type':'button','text':'Tap','x':10,'y':380,'w':100}]}", 0,
                "0 errors, 0 warnings");
        says("lm too tall", "{'type':'panel','style':'lm','w':300,'h':900,'children':[]}", 1, "taller than the window");
        says("lm no skin", lm + "{'type':'swatch','skin':'nope','x':10,'y':60,'w':100,'h':16}]}", 2, "no skin");
        r0 = make("lm include", "{'sizes':['1280x720'],'scales':[3],'include':['more.json'],'screens':[]}");
        ok(r0.code() == 2 && r0.text().contains("no include"), "a missing include", r0);
        text("mod/tools/more.json", "{'screens':[{'id':'inc','elements':[{'w':10,'h':10}]}]}");
        r0 = make("lm include2", "{'sizes':['1280x720'],'scales':[3],'include':['more.json'],'screens':[]}");
        ok(r0.code() == 0 && r0.text().contains("1 screens"), "an include adds its screens", r0);
        r0 = make("lmkeep", "{'sizes':['1280x720','854x480'],'scales':[2],'keep':['1280x720@2'],'format':'jpg','sheets':false,"
                + "'screens':[{'id':'k','background':'skin','skin':'namek_serenity','look':'lite','elements':[{'w':10,'h':10}]}]}");
        ok(r0.code() == 0 && new File(work, "out/lmkeep/k_1280x720_s2.jpg").isFile() && !new File(work, "out/lmkeep/k_854x480_s2.jpg").exists(),
                "keep / jpg / skin backdrop", r0);

        // words
        says("a key", box("{'type':'text','key':'gui.testmod.cost','args':[25],'x':4,'y':4,'w':60}"), 0, "0 errors, 0 warnings");
        says("numbered holes", box("{'type':'text','key':'gui.testmod.two','args':[8,3],'x':4,'y':4,'w':40}"), 0, "0 errors, 0 warnings");
        says("no args", box("{'type':'text','key':'gui.testmod.cost','x':4,'y':4}"), 0, "give \"args\"");
        says("no such key", box("{'type':'text','key':'gui.testmod.none','x':4,'y':4}"), 1, "no text for the key");
        says("the longest key", box("{'type':'text','keys':'quest.testmod.*.name','x':4,'y':4,'w':90}"), 1, "wider than its box");
        says("no key matches", box("{'type':'text','keys':'quest.other.*','x':4,'y':4}"), 1, "no text key matches");
        says("no texture", box("{'type':'image','texture':'testmod:textures/gui/none.png','x':4,'y':4,'w':16,'h':16}"), 1, "no texture");
        says("a texture of vanilla's", box("{'type':'slot','icon':'minecraft:textures/item/apple.png','x':4,'y':4}"), 0);
        says("no glyph", "{'type':'text','text':'\\udb80\\udc01','anchor':'center'}", 1, "no glyph");

        // a broken layout
        says("unknown key", "{'type':'text','text':'x','anchr':'center'}", 2, "unknown key \"anchr\"", "did you mean \"anchor\"");
        says("unknown type", "{'type':'buton','text':'x'}", 2, "did you mean \"button\"");
        says("unknown anchor", "{'type':'text','text':'x','anchor':'middle'}", 2, "no anchor");
        says("unknown style", "{'type':'panel','style':'drak'}", 2, "did you mean \"dark\"");
        says("no words", "{'type':'button'}", 2, "needs \"text\"");
        says("bad width", "{'type':'box','w':'wide'}", 2, "\"fill\"");
        says("bad colour", "{'type':'box','colour':'red'}", 2, "#rrggbb");
        says("bad ring", "{'type':'wheel','rings':[{'inner':30,'outer':20}]}", 2, "\"inner\" < \"outer\"");
        Result r = make("twice", "{'screens':[{'id':'a','elements':[{}]},{'id':'a','elements':[{}]}]}");
        ok(r.code() == 2 && r.text().contains("is there twice"), "screen twice", r);
        r = make("size", "{'sizes':['big'],'screens':[{'id':'a','elements':[{}]}]}");
        ok(r.code() == 2 && r.text().contains("a size is like"), "bad size", r);
        r = make("none", "{}");
        ok(r.code() == 2 && r.text().contains("no \"screens\""), "nothing in it", r);
        r = make("only", "{'sizes':['1280x720'],'screens':[{'id':'a','elements':[{'w':10,'h':10}]},{'id':'b','elements':[{'w':10,'h':10}]}]}", "-only", "^b$");
        ok(r.code() == 0 && r.text().contains("1 screens") && !new File(work, "out/only/a_sheet.png").exists(), "-only", r);
        r = make("variants", "{'screens':[{'id':'a','elements':[{'w':10,'h':10}]}]}");
        ok(r.code() == 0 && r.text().contains("11 mockups"), "the default windows, each scale once", r);
        r = kit("make", new File(work, "mod").getPath(), "-out", new File(work, "mod/src/main/resources/x").getPath());
        ok(r.code() == 2 && r.text().contains("never into src"), "never into src", r);
        r = kit("make", new File(work, "mod").getPath(), "-install", new File(work, "nogame").getPath());
        ok(r.code() == 2 && r.text().contains("NOT PROVEN"), "no game installed", r);
        ok(kit("make", new File(work, "none").getPath()).code() == 2 && kit("nonsense").code() == 2 && kit("measure", "abc").code() == 0, "usage", "wrong code");

        System.out.println("GuiKitTest: " + passed + " passed, " + failed + " failed");
        System.exit(failed == 0 ? 0 : 1);
    }
}
