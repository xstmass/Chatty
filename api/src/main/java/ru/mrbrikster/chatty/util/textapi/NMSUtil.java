package ru.mrbrikster.chatty.util.textapi;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import lombok.experimental.UtilityClass;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.UUID;

import static ru.mrbrikster.chatty.util.textapi.NMSUtil.ServerPackage.*;

@UtilityClass
public class NMSUtil {

    private static final HashMap<String, Class<?>> NMS_CLASSES = new HashMap<>();

    static {
        NMS_CLASSES.put("IChatBaseComponent", resolveSuitableClass(MINECRAFT + ".IChatBaseComponent",
                NETWORK + ".chat.IChatBaseComponent"));
        NMS_CLASSES.put("ChatMessageType", resolveSuitableClass(MINECRAFT + ".ChatMessageType",
                NETWORK + ".chat.ChatMessageType"));
        NMS_CLASSES.put("IChatBaseComponent$ChatSerializer", resolveSuitableClass(MINECRAFT + ".IChatBaseComponent$ChatSerializer",
                NETWORK + ".chat.IChatBaseComponent$ChatSerializer"));

        NMS_CLASSES.put("PacketPlayOutChat", resolveSuitableClass(MINECRAFT + ".PacketPlayOutChat",
                NETWORK + ".protocol.game.PacketPlayOutChat"));
        NMS_CLASSES.put("Packet", resolveSuitableClass(MINECRAFT + ".Packet",
                NETWORK + ".protocol.Packet"));

        // Legacy title packets
        NMS_CLASSES.put("PacketPlayOutTitle", resolveSuitableClass(MINECRAFT + ".PacketPlayOutTitle"));
        NMS_CLASSES.put("PacketPlayOutTitle$EnumTitleAction", resolveSuitableClass(MINECRAFT + ".PacketPlayOutTitle$EnumTitleAction"));

        // New (>= 1.17) title packets
        NMS_CLASSES.put("ClientboundSetTitleTextPacket", resolveSuitableClass(NETWORK + ".protocol.game.ClientboundSetTitleTextPacket"));
        NMS_CLASSES.put("ClientboundSetSubtitleTextPacket", resolveSuitableClass(NETWORK + ".protocol.game.ClientboundSetSubtitleTextPacket"));
        NMS_CLASSES.put("ClientboundSetTitlesAnimationPacket", resolveSuitableClass(NETWORK + ".protocol.game.ClientboundSetTitlesAnimationPacket"));

        // 1.19 chat packet
        NMS_CLASSES.put("ClientboundPlayerChatPacket", resolveSuitableClass(NETWORK + ".protocol.game.ClientboundPlayerChatPacket"));
        NMS_CLASSES.put("IChatMutableComponent", resolveSuitableClass(NETWORK + ".protocol.game.IChatMutableComponent"));
        NMS_CLASSES.put("PlayerChatMessage", resolveSuitableClass(NETWORK + ".chat.PlayerChatMessage"));
        NMS_CLASSES.put("ServerPlayer", resolveSuitableClass("net.minecraft.server.level.ServerPlayer"));
        NMS_CLASSES.put("ChatSender", resolveSuitableClass(NETWORK + ".chat.ChatSender"));
    }

    public Class<?> getClass(String key) {
        return NMS_CLASSES.get(key);
    }

    private Class<?> resolveSuitableClass(String... paths) {
        for (String path : paths) {
            try {
                return Class.forName(path);
            } catch (ClassNotFoundException ignored) {}
        }

        return null;
    }

    @NotNull
    public Field resolveField(Class<?> clazz, String... names) {
        for (String name : names) {
            try {
                return clazz.getField(name);
            } catch (NoSuchFieldException ignored) {}
        }

        throw new IllegalStateException();
    }

    public void sendChatPacket(Player player, String type, String text, @Nullable Player sender) {
        try {
            Class<?> clsIChatBaseComponent = NMS_CLASSES.get("IChatBaseComponent");
            Object chatBaseComponent = NMS_CLASSES.get("IChatBaseComponent$ChatSerializer").getMethod("a", String.class).invoke(null, text);

            Class<?> clsClientboundPlayerChatPacket = NMS_CLASSES.get("ClientboundPlayerChatPacket");

            if (clsClientboundPlayerChatPacket == null) {
                // < 1.19
                Class<?> clsChatMessageType = NMS_CLASSES.get("ChatMessageType");
                Object entityPlayer = player.getClass().getMethod("getHandle").invoke(player);
                Object playerConnection = resolveField(entityPlayer.getClass(), "b", "playerConnection").get(entityPlayer);
                Object chatMessageType = clsChatMessageType.getMethod("valueOf", String.class).invoke(null, type);

                Object packetPlayOutChat = null;
                Class<?> packetPlayOutChatClass = NMS_CLASSES.get("PacketPlayOutChat");

                // Legacy versions (< 1.16)
                try {
                    packetPlayOutChat = packetPlayOutChatClass.getConstructor(clsIChatBaseComponent, clsChatMessageType)
                            .newInstance(chatBaseComponent, chatMessageType);
                } catch (Throwable ignored) {}

                // New versions (>= 1.16)
                if (packetPlayOutChat == null) {
                    try {
                        packetPlayOutChat = packetPlayOutChatClass.getConstructor(clsIChatBaseComponent, clsChatMessageType, UUID.class)
                                .newInstance(chatBaseComponent, chatMessageType, sender.getUniqueId());
                    } catch (Throwable ignored) {}
                }

                if (packetPlayOutChat == null) {
                    throw new IllegalStateException();
                }

                Method sendPacketMethod;
                try {
                    sendPacketMethod = playerConnection.getClass().getMethod("sendPacket", NMS_CLASSES.get("Packet"));
                } catch (Exception ignored) {
                    // 1.18+
                    sendPacketMethod = playerConnection.getClass().getMethod("a", NMS_CLASSES.get("Packet"));
                }

                sendPacketMethod.invoke(playerConnection, packetPlayOutChat);
            } else {
                // 1.19+
                Class<?> clsChatSender = NMS_CLASSES.get("ChatSender");
                Class<?> clsPlayerChatMessage = NMS_CLASSES.get("PlayerChatMessage");

                // ChatMessageType chatMessageType = type.equals("CHAT") ? ChatMessageType.b : ChatMessageType.d;
                Object chatMessageType = NMS_CLASSES.get("ChatMessageType")
                        // b: 'system', d: 'game_info'
                        .getDeclaredField(type.equals("CHAT") ? "c" : "d")
                        .get(null);

                Object senderName = NMS_CLASSES.get("IChatBaseComponent$ChatSerializer")
                        .getMethod("a", String.class)
                        .invoke(null, "{\"text\":\"" + player.getDisplayName() + "\"}");

                // PlayerChatMessage playerChatMessage = PlayerChatMessage.a(chatBaseComponent);
                Object playerChatMessage = clsPlayerChatMessage.getMethod("a", clsIChatBaseComponent)
                        .invoke(null, chatBaseComponent);

                // EntityPlayer entityPlayer = ((CraftPlayer) player).getHandle();
                Object entityPlayer = player.getClass().getMethod("getHandle").invoke(player);

                if (sender == null) {
                    // entityPlayer.a(chatBaseComponent, chatMessageType);
                    entityPlayer.getClass().getMethod("a", clsIChatBaseComponent, chatMessageType.getClass())
                            .invoke(entityPlayer, chatBaseComponent, chatMessageType);
                } else {
                    // ChatSender chatSender = new ChatSender(sender, senderName);
                    Object chatSender = clsChatSender.getConstructor(UUID.class, clsIChatBaseComponent)
                            .newInstance(sender.getUniqueId(), senderName);
                    // entityPlayer.a(playerChatMessage, chatSender, chatMessageType);
                    entityPlayer.getClass().getMethod("a", clsPlayerChatMessage, clsChatSender, chatMessageType.getClass())
                            .invoke(entityPlayer, playerChatMessage, chatSender, chatMessageType);
                }
            }
        } catch (Throwable e) {
            throw new RuntimeException("NMS features is not supported by Chatty on your server version (" + getServerVersion() + ")", e);
        }
    }

    public enum ServerPackage {

        MINECRAFT("net.minecraft.server." + getServerVersion()),
        NETWORK("net.minecraft.network");

        private final String path;

        ServerPackage(String path) {
            this.path = path;
        }

        public static String getServerVersion() {
            return Bukkit.getServer().getClass().getPackage().getName().substring(23);
        }

        @Override
        public String toString() {
            return path;
        }

    }

}
