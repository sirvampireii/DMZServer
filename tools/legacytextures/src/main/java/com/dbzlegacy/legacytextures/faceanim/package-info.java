/**
 * Face animation for DragonMineZ faces (Legacy Textures 0.2.3): eyes blink, and the mouth opens into a
 * scream while charging ki or charging a non-fusion action.
 *
 * <p>Absorbed from DMZ Face Anim 0.2.0 ({@code com.dmzfaceanim}, dmzfaceanim-0.2.0.jar, sha256
 * 7260a2b09e44b3f7e068a70fcffc8a3b8710ade64038df7331bddeb334f92a0d). That jar was published as
 * GPL-3.0-only; its owner (the DMZ Legacy server owner) relicensed it into Legacy Textures on
 * 2026-11-05. Mechanism unchanged: {@link FaceAnimator} keeps the 90-tick blink cycle with a per-UUID
 * offset and the scream ramp, and {@code mixin.DMZSkinLayerFaceMixin} reroutes face layer paths through
 * DMZSkinLayer.renderColoredLayer (never binding a texture or caching a GL id itself). The frames ship
 * under {@code dragonminez:textures/entity/races/<race>/faces/anim/} (import/import_faceanim.py).
 *
 * <p>New in Legacy Textures: a player can turn their own face animation off ({@code /faceanim}; stored on
 * the server, synced to everyone, {@link FaceFlags}), and a viewer can turn blinking or screaming off for
 * themselves ({@link FaceAnimConfig}, {@code [faceanim]} in legacytextures-client.toml). The Angel frames
 * are under {@code angel/} (the ZoneMC art used {@code anjo/}).
 */
package com.dbzlegacy.legacytextures.faceanim;
