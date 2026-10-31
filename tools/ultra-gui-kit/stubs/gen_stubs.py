#!/usr/bin/env python3
"""Generate minimal compile stubs for Minecraft/Forge client APIs (SRG names)."""
from pathlib import Path

ROOT = Path(__file__).resolve().parent

FILES = {
    "net/minecraft/network/chat/Component.java": """
package net.minecraft.network.chat;
public interface Component {
  static MutableComponent m_237113_(String s) { return new MutableComponent(s); }
  static MutableComponent m_237119_() { return new MutableComponent(""); }
}
""",
    "net/minecraft/network/chat/MutableComponent.java": """
package net.minecraft.network.chat;
public class MutableComponent implements Component {
  public MutableComponent(String s) {}
}
""",
    "net/minecraft/util/FormattedCharSequence.java": """
package net.minecraft.util;
public interface FormattedCharSequence {}
""",
    "net/minecraft/client/gui/Font.java": """
package net.minecraft.client.gui;
import net.minecraft.util.FormattedCharSequence;
public class Font {
  public FormattedCharSequence m_92834_(String text, int width) { return new FormattedCharSequence() {}; }
  public int m_92852_(FormattedCharSequence text) { return 0; }
}
""",
    "net/minecraft/client/gui/GuiGraphics.java": """
package net.minecraft.client.gui;
import net.minecraft.util.FormattedCharSequence;
public class GuiGraphics {
  public void m_280509_(int x1, int y1, int x2, int y2, int color) {}
  public void m_280488_(Font font, FormattedCharSequence text, int x, int y, int color) {}
  public void m_280588_(int x1, int y1, int x2, int y2) {}
  public void m_280618_() {}
}
""",
    "net/minecraft/client/gui/components/events/GuiEventListener.java": """
package net.minecraft.client.gui.components.events;
public interface GuiEventListener {}
""",
    "net/minecraft/client/gui/components/AbstractWidget.java": """
package net.minecraft.client.gui.components;
import net.minecraft.client.gui.components.events.GuiEventListener;
public abstract class AbstractWidget implements GuiEventListener {
  public boolean m_93696_() { return false; }
}
""",
    "net/minecraft/client/gui/components/EditBox.java": """
package net.minecraft.client.gui.components;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;
public class EditBox extends AbstractWidget {
  private String value = "";
  private int max = 32;
  public EditBox(Font font, int x, int y, int w, int h, Component title) {}
  public void m_94199_(int maxLen) { this.max = maxLen; }
  public void m_94144_(String v) { this.value = v == null ? "" : v; }
  public String m_94155_() { return value; }
  public boolean m_93696_() { return false; }
}
""",
    "net/minecraft/client/gui/screens/Screen.java": """
package net.minecraft.client.gui.screens;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.network.chat.Component;
public abstract class Screen {
  public int f_96543_;
  public int f_96544_;
  public Font f_96547_ = new Font();
  protected Screen(Component title) {}
  protected void m_7856_() {}
  protected void m_169413_() {}
  public void m_7379_() {}
  public <T extends GuiEventListener> T m_142416_(T w) { return w; }
  public void m_280273_(GuiGraphics g) {}
  public void m_88315_(GuiGraphics g, int mouseX, int mouseY, float tick) {}
  public boolean m_7043_() { return false; }
  public boolean m_7933_(int keyCode, int scanCode, int modifiers) { return false; }
}
""",
    "net/minecraft/world/entity/LivingEntity.java": """
package net.minecraft.world.entity;
public class LivingEntity {
  private final java.util.UUID id = java.util.UUID.randomUUID();
  public java.util.UUID m_20148_() { return id; }
}
""",
    "software/bernie/geckolib/cache/object/BakedGeoModel.java": """
package software.bernie.geckolib.cache.object;
/** Test-only stand-in: GeckoLib's model handle, which DragonMineZ hides bones on. */
public class BakedGeoModel {
  public java.util.List<Object> searchForChildBone(String name) { return java.util.List.of(); }
}
""",
    "software/bernie/geckolib/renderer/layer/GeoRenderLayer.java": """
package software.bernie.geckolib.renderer.layer;
/** Test-only stand-in: the GeckoLib render-layer type DragonMineZ passes bone visibility. */
public abstract class GeoRenderLayer<T> {
}
""",
    "com/dragonminez/client/util/BoneVisibilityHandler.java": """
package com.dragonminez.client.util;
import net.minecraft.client.player.AbstractClientPlayer;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.renderer.layer.GeoRenderLayer;
/**
 * Test-only stand-in for DragonMineZ's bone visibility pass. Signatures mirror the real
 * class so RaceTailSuppressMixin compiles; the mixin targets the real one by name.
 */
public class BoneVisibilityHandler {
  public static void updateVisibility(BakedGeoModel model, AbstractClientPlayer player, GeoRenderLayer<?> layer) {}
  public static void setBonesHidden(BakedGeoModel model, boolean hidden, String... bones) {}
  public static void hideBone(BakedGeoModel model, String bone, boolean hide) {}
}
""",
    "com/dmzultra/client/forms/FormParts.java": """
package com.dmzultra.client.forms;
import java.util.Set;
/**
 * Test-only stand-in for DMZUltra's FormParts. The class itself is the marker that lets
 * BodyPartPreview.piecesFor resolve the DMZUltra fallback catalog offline; Design/Look
 * mirror the reflective contract BodyPartPreview.mergeLook binds to (constructor shapes
 * + accessor names), so the kit can verify the server-colors merge without the mod.
 */
public class FormParts {
  public static final class Design {
    private final Set<String> parts;
    private final int own, trim, horn, light;
    private final float front, back;
    public Design(Set<String> parts, int own, int trim, int horn, int light, float front, float back) {
      this.parts = parts;
      this.own = own;
      this.trim = trim;
      this.horn = horn;
      this.light = light;
      this.front = front;
      this.back = back;
    }
    public Set<String> parts() { return parts; }
    public int own() { return own; }
    public int trim() { return trim; }
    public int horn() { return horn; }
    public int light() { return light; }
    public float front() { return front; }
    public float back() { return back; }
  }
  public static final class Look {
    private final Design design;
    private final int skin, second, hair;
    public Look(Design design, int skin, int second, int hair) {
      this.design = design;
      this.skin = skin;
      this.second = second;
      this.hair = hair;
    }
    public Design design() { return design; }
    public int skin() { return skin; }
    public int second() { return second; }
    public int hair() { return hair; }
  }
}
""",
    "net/minecraft/client/player/LocalPlayer.java": """
package net.minecraft.client.player;
import net.minecraft.world.entity.LivingEntity;
public class LocalPlayer extends LivingEntity {}
""",
    "net/minecraft/client/player/AbstractClientPlayer.java": """
package net.minecraft.client.player;
import net.minecraft.world.entity.LivingEntity;
public class AbstractClientPlayer extends LivingEntity {}
""",
    "net/minecraft/client/multiplayer/ClientPacketListener.java": """
package net.minecraft.client.multiplayer;
import net.minecraft.network.Connection;
public class ClientPacketListener {
  public Connection m_104910_() { return new Connection(); }
}
""",
    "net/minecraft/network/Connection.java": """
package net.minecraft.network;
public class Connection {}
""",
    "net/minecraft/client/Minecraft.java": """
package net.minecraft.client;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.player.LocalPlayer;
public class Minecraft {
  private static final Minecraft INSTANCE = new Minecraft();
  public LocalPlayer f_91074_;
  public static Minecraft m_91087_() { return INSTANCE; }
  public ClientPacketListener m_91403_() { return new ClientPacketListener(); }
  public void m_91152_(Screen screen) {}
}
""",
    "net/minecraft/network/FriendlyByteBuf.java": """
package net.minecraft.network;
import java.util.ArrayList;
import java.util.List;
/** Minimal in-memory buffer for compile stubs + encode/decode self-tests. */
public class FriendlyByteBuf {
  private final List<Object> values = new ArrayList<>();
  private int idx = 0;
  public FriendlyByteBuf m_130070_(String s) { values.add(s == null ? "" : s); return this; }
  public FriendlyByteBuf m_130072_(String s, int max) { return m_130070_(s); }
  public String m_130277_() {
    if (idx >= values.size()) throw new IndexOutOfBoundsException("utf");
    Object v = values.get(idx++);
    return v == null ? "" : String.valueOf(v);
  }
  public String m_130136_(int max) { return m_130277_(); }
  public FriendlyByteBuf m_130130_(int v) { values.add(v); return this; }
  public int m_130242_() {
    if (idx >= values.size()) throw new IndexOutOfBoundsException("varint");
    return ((Number) values.get(idx++)).intValue();
  }
  public FriendlyByteBuf writeBytes(byte[] b) {
    values.add(b == null ? new byte[0] : b);
    return this;
  }
  public void readBytes(byte[] out) {
    if (idx >= values.size()) throw new IndexOutOfBoundsException("bytes");
    Object v = values.get(idx++);
    byte[] src = v instanceof byte[] ? (byte[]) v : String.valueOf(v).getBytes(java.nio.charset.StandardCharsets.UTF_8);
    System.arraycopy(src, 0, out, 0, Math.min(src.length, out.length));
  }
  public boolean isReadable() { return idx < values.size(); }
  /** Approximate remaining payload size (bytes), not entry count. */
  public int readableBytes() {
    int n = 0;
    for (int i = idx; i < values.size(); i++) {
      Object v = values.get(i);
      if (v instanceof byte[]) n += ((byte[]) v).length;
      else if (v instanceof String) n += ((String) v).getBytes(java.nio.charset.StandardCharsets.UTF_8).length;
      else n += 4;
    }
    return n;
  }
  public void writeExtraMarker(String s) { values.add(s); }
}
""",
    "net/minecraft/resources/ResourceLocation.java": """
package net.minecraft.resources;
public class ResourceLocation {
  public ResourceLocation(String ns, String path) {}
}
""",
    "net/minecraftforge/api/distmarker/Dist.java": """
package net.minecraftforge.api.distmarker;
public enum Dist {
  CLIENT, DEDICATED_SERVER;
  public boolean isClient() { return this == CLIENT; }
}
""",
    "net/minecraftforge/eventbus/api/Event.java": """
package net.minecraftforge.eventbus.api;
public class Event {}
""",
    "net/minecraftforge/eventbus/api/EventBus.java": """
package net.minecraftforge.eventbus.api;
import java.util.function.Consumer;
public class EventBus {
  public <T extends Event> void addListener(Consumer<T> consumer) {}
  public boolean post(Event event) { return false; }
}
""",
    "net/minecraftforge/common/MinecraftForge.java": """
package net.minecraftforge.common;
import net.minecraftforge.eventbus.api.EventBus;
public class MinecraftForge {
  public static final EventBus EVENT_BUS = new EventBus();
}
""",
    "net/minecraftforge/client/event/ClientPlayerNetworkEvent.java": """
package net.minecraftforge.client.event;
import net.minecraftforge.eventbus.api.Event;
public class ClientPlayerNetworkEvent extends Event {
  public static class LoggingIn extends ClientPlayerNetworkEvent {}
  public static class LoggingOut extends ClientPlayerNetworkEvent {}
}
""",
    "net/minecraftforge/fml/loading/FMLEnvironment.java": """
package net.minecraftforge.fml.loading;
import net.minecraftforge.api.distmarker.Dist;
public class FMLEnvironment {
  public static Dist dist = Dist.CLIENT;
}
""",
    "net/minecraftforge/network/NetworkDirection.java": """
package net.minecraftforge.network;
public enum NetworkDirection { PLAY_TO_CLIENT, PLAY_TO_SERVER }
""",
    "net/minecraftforge/network/NetworkEvent.java": """
package net.minecraftforge.network;
import java.util.concurrent.CompletableFuture;
public class NetworkEvent {
  public static class Context {
    public void setPacketHandled(boolean v) {}
    public CompletableFuture<Void> enqueueWork(Runnable r) { r.run(); return CompletableFuture.completedFuture(null); }
  }
}
""",
    "net/minecraftforge/network/NetworkRegistry.java": """
package net.minecraftforge.network;
import java.util.function.Predicate;
import java.util.function.Supplier;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.network.simple.SimpleChannel;
public class NetworkRegistry {
  public static Predicate<String> acceptMissingOr(String protocol) { return r -> true; }
  public static SimpleChannel newSimpleChannel(ResourceLocation name, Supplier<String> protocol,
      Predicate<String> clientAcceptedVersions, Predicate<String> serverAcceptedVersions) {
    return new SimpleChannel();
  }
}
""",
    "net/minecraftforge/network/simple/SimpleChannel.java": """
package net.minecraftforge.network.simple;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.function.Supplier;
import net.minecraft.network.Connection;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkEvent;
public class SimpleChannel {
  public <MSG> MessageBuilder<MSG> messageBuilder(Class<MSG> type, int id, NetworkDirection dir) {
    return new MessageBuilder<>();
  }
  public boolean isRemotePresent(Connection c) { return true; }
  public void sendToServer(Object msg) {}
  public static class MessageBuilder<MSG> {
    public MessageBuilder<MSG> encoder(BiConsumer<MSG, FriendlyByteBuf> enc) { return this; }
    public MessageBuilder<MSG> decoder(Function<FriendlyByteBuf, MSG> dec) { return this; }
    public MessageBuilder<MSG> consumerMainThread(BiConsumer<MSG, Supplier<NetworkEvent.Context>> c) { return this; }
    public void add() {}
  }
}
""",
    "org/apache/logging/log4j/Logger.java": """
package org.apache.logging.log4j;
public interface Logger {
  void info(String msg, Object... args);
  void warn(String msg, Object... args);
  void error(String msg, Object... args);
  void error(String msg, Throwable t);
  void error(String msg, Object arg, Throwable t);
  void debug(String msg, Object... args);
}
""",
    "org/apache/logging/log4j/LogManager.java": """
package org.apache.logging.log4j;
public class LogManager {
  public static Logger getLogger(String name) {
    return new Logger() {
      public void info(String msg, Object... args) {}
      public void warn(String msg, Object... args) {}
      public void error(String msg, Object... args) {}
      public void error(String msg, Throwable t) {}
      public void error(String msg, Object arg, Throwable t) {}
      public void debug(String msg, Object... args) {}
    };
  }
}
""",
}


def main() -> None:
    for rel, body in FILES.items():
        path = ROOT / rel
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text(body.lstrip(), encoding="utf-8")
        print("wrote", rel)


if __name__ == "__main__":
    main()
