package com.dmzlegacy.kaipath.ultra.consumers.legacymechanics;

import com.dmzultra.client.forms.FormParts;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.util.Map;
import java.util.Set;

/**
 * Offline checks for the form-preview figure (KaiPath 1.5.6 behaviour). No Minecraft runtime.
 *
 * <p>Made-up subject: what LM's {@code ModelPreview.formFigure} sends for a Saiyan owner previewing
 * the base form while wearing one Body Parts Shop piece ({@code wings_feather}). The catalog has no
 * {@code saiyan/base/base} entry, so {@code bodyParts} is the shop piece alone, and DMZUltra
 * {@code FormParts.of} returns null for the base form.
 * <ol>
 *   <li>{@link PreviewAppearance#changes} puts the Saiyan race and the base form (empty group and
 *       form) on the blank stand-in, without asking whether "base" is a form;</li>
 *   <li>{@link PreviewAppearance#apply} sets the fields and the scope puts them back;</li>
 *   <li>{@link BodyPartPreview#mergeLook} with a null look builds a Look that wears the piece and
 *       the sent colors, instead of returning null.</li>
 * </ol>
 */
public final class PreviewAppearanceTest {
    private PreviewAppearanceTest() {}

    static final String SAIYAN_BASE_PLUS_SHOP_PIECE =
            "{\"schema\":1,"
                    + "\"subject\":\"uuid:6f1d3c1e-0000-3000-8000-00000000b45e\","
                    + "\"form\":{\"race\":\"saiyan\",\"group\":\"\",\"form\":\"\",\"stackGroup\":\"\",\"stackForm\":\"\"},"
                    + "\"bodyParts\":[{\"id\":\"wings_feather\",\"source\":\"dmzultra\",\"slot\":\"wings\"}],"
                    + "\"suppressNaturalTail\":false,"
                    + "\"colors\":{\"skin\":15777936,\"second\":16777215,\"hair\":2106404,"
                    + "\"own\":15777936,\"trim\":16777215,\"horn\":2106404,\"light\":16777215},"
                    + "\"headBones\":{\"token\":\"\",\"pieces\":[]},"
                    + "\"skinOwner\":\"6f1d3c1e-0000-3000-8000-0000000000aa\","
                    + "\"extras\":[]}";

    /** Stand-in for DragonMineZ's Character: the private String fields PreviewAppearance sets. */
    static final class FakeCharacter {
        private String race = "";
        private String activeFormGroup = "";
        private String activeForm = "";
        private String activeStackFormGroup = "";
        private String activeStackForm = "";
        private String activeHeadBone = "";
    }

    public static void main(String[] args) throws Exception {
        JsonObject parts = JsonParser.parseString(SAIYAN_BASE_PLUS_SHOP_PIECE).getAsJsonObject();

        // 1. A blank stand-in (no race) gets the Saiyan race and the base form.
        Map<String, String> changes = PreviewAppearance.changes(
                "", parts,
                race -> "saiyan".equals(race),
                form -> {
                    fail("the base form must not be looked up as a form: " + form);
                    return false;
                },
                (group, form) -> {
                    fail("an empty stack form must not be looked up");
                    return false;
                });
        expect("saiyan".equals(changes.get("race")), "race saiyan, got " + changes);
        expect("".equals(changes.get("activeFormGroup")) && "".equals(changes.get("activeForm")),
                "base form = empty group and form, got " + changes);
        expect("".equals(changes.get("activeStackFormGroup")), "stack form cleared, got " + changes);

        // An unknown race changes nothing about the form.
        expect(!PreviewAppearance.changes("", parts, race -> false, f -> true, (a, b) -> true).containsKey("race"),
                "an unloaded race is not applied");

        // 2. Fields go on and come back.
        FakeCharacter ch = new FakeCharacter();
        ch.race = "human";
        ch.activeForm = "kaioken";
        try (PreviewAppearance.Scope scope = PreviewAppearance.apply(ch, changes)) {
            expect("saiyan".equals(ch.race) && "".equals(ch.activeForm), "fields applied for the draw");
        }
        expect("human".equals(ch.race) && "kaioken".equals(ch.activeForm), "fields restored after the draw");

        // 3. FormParts.of is null for the base form: mergeLook builds a Look that wears the piece.
        JsonObject colors = parts.getAsJsonObject("colors");
        Object look = BodyPartPreview.mergeLook(null, Set.of("wings_feather"), colors, parts.toString());
        expect(look instanceof FormParts.Look, "a Look is built when FormParts.of is null, got " + look);
        FormParts.Look built = (FormParts.Look) look;
        expect(built.design().parts().contains("wings_feather"), "the shop piece is in the built design");
        expect(built.skin() == 15777936 && built.design().own() == 15777936, "sent colors are used");
        expect(BodyPartPreview.mergeLook(null, Set.of(), null, null) == null,
                "nothing to apply keeps the null look");

        System.out.println("PreviewAppearanceTest OK");
    }

    private static void expect(boolean ok, String what) {
        if (!ok) {
            fail(what);
        }
    }

    private static void fail(String message) {
        throw new AssertionError("PreviewAppearanceTest: " + message);
    }
}
