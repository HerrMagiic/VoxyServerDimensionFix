package me.voxy.dynamic;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import com.mojang.blaze3d.platform.InputConstants;
import me.voxy.dynamic.gui.VoxyDynamicAreasScreen;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import org.lwjgl.glfw.GLFW;
import me.cortex.voxy.common.Logger;
import me.cortex.voxy.client.core.IGetVoxyRenderSystem;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

public class DynamicVoxyManager implements ClientModInitializer {
    private static final int LOAD_ACTIVATION_DELAY_SECONDS = 5;
    private static final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path CONFIG_PATH = FabricLoader.getInstance().getConfigDir().resolve("voxy_dynamic_addon.json");
    private static final KeyMapping.Category CATEGORY = KeyMapping.Category.register(Identifier.fromNamespaceAndPath("voxy_dynamic", "voxy_dynamic"));

    private static DynamicVoxyConfig config;
    private static String currentServerId = null;
    private static String currentAreaId = null;
    private static String forcedAreaId = null;
    private static String pinnedName = null;
    private static String pinnedDimension = null;
    private static String pendingLoadName = null;
    private static KeyMapping openAreasKey;
    private static ScheduledFuture<?> pendingReload = null;
    private static ScheduledFuture<?> pendingLoadActivation = null;

    @Override
    public void onInitializeClient() {
        config = loadConfig();
        registerKeyBindings();

        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> client.execute(() -> onJoin(client)));
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> client.execute(this::onDisconnect));
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (openAreasKey != null && openAreasKey.consumeClick()) {
                openAreasScreen(client);
            }
            if (client.level == null || client.player == null || currentServerId == null) {
                return;
            }
            checkAutoUnpin(client);
            String areaId = deriveAreaId(client);
            if (!Objects.equals(areaId, currentAreaId)) {
                currentAreaId = areaId;
                if (areaId != null && !areaId.isEmpty()) {
                    scheduleReload(areaId);
                }
            }
        });
    }

    private static void openAreasScreen(Minecraft client) {
        if (currentServerId == null) {
            return;
        }
        client.setScreen(new VoxyDynamicAreasScreen(currentServerId));
    }

    private static void onJoin(Minecraft client) {
        ServerData serverData = client.getCurrentServer();
        if (serverData != null && serverData.ip != null && !serverData.ip.isEmpty()) {
            String ip = serverData.ip.toLowerCase(Locale.ROOT);
            currentServerId = ip.replaceFirst(":.*", "");
            Logger.info(String.format("[Voxy Dynamic] Connected to server: %s", currentServerId));
        } else {
            currentServerId = null;
        }
        currentAreaId = null;
        clearPin();
        cancelPendingLoadActivation();
    }

    private void onDisconnect() {
        currentServerId = null;
        currentAreaId = null;
        clearPin();
        cancelPendingReload();
        cancelPendingLoadActivation();
    }

    private static void clearPin() {
        forcedAreaId = null;
        pinnedName = null;
        pinnedDimension = null;
    }

    private static void checkAutoUnpin(Minecraft client) {
        if (pinnedDimension == null) {
            return;
        }
        String currentDimension = client.level.dimension().identifier().toString();
        if (!currentDimension.equals(pinnedDimension)) {
            sendStatusMessage(client, "Left pinned area \"" + pinnedName + "\" (dimension changed) - using default storage.");
            clearPin();
        }
    }

    private static String deriveAreaId(Minecraft client) {
        if (forcedAreaId != null && !forcedAreaId.isEmpty()) {
            return forcedAreaId;
        }
        if (currentServerId == null) {
            return null;
        }

        String computed = computeCurrentAreaValue(client);
        return sanitizeAreaId(currentServerId + "_" + computed);
    }

    private static String computeCurrentAreaValue(Minecraft client) {
        return client.level.dimension().identifier().toString();
    }

    private static String sanitizeAreaId(String input) {
        return input.replaceAll("[^a-zA-Z0-9_.-]", "_").replaceAll("_+", "_");
    }

    private static void scheduleReload(String areaId) {
        cancelPendingReload();
        pendingReload = scheduler.schedule(() -> Minecraft.getInstance().execute(() -> {
            try {
                Logger.info(String.format("[Voxy Dynamic] Reloading renderer for area: %s", areaId));
                var renderer = Minecraft.getInstance().levelRenderer;
                if (renderer instanceof IGetVoxyRenderSystem getter) {
                    getter.voxy$shutdownRenderer();
                    getter.voxy$createRenderer();
                }
            } catch (Exception ignored) {
            }
        }), 200, TimeUnit.MILLISECONDS);
    }

    private static void registerKeyBindings() {
        openAreasKey = KeyBindingHelper.registerKeyBinding(new KeyMapping(
                "key.voxy_dynamic.open_areas",
                InputConstants.Type.KEYSYM,
                GLFW.GLFW_KEY_O,
                CATEGORY
        ));
    }

    public static Map<String, String> getNamedAreas(String serverId) {
        return config.getNamedAreas(serverId);
    }

    public static String getPinnedName(String serverId) {
        return Objects.equals(serverId, currentServerId) ? pinnedName : null;
    }

    public static void saveCurrentAreaAs(String name) {
        Minecraft client = Minecraft.getInstance();
        if (currentServerId == null) {
            sendStatusMessage(client, "Not connected to a multiplayer server.");
            return;
        }
        if (client.player == null || client.level == null) {
            sendStatusMessage(client, "No world available to save area from.");
            return;
        }

        String area = sanitizeAreaId(name);
        if (area.isEmpty()) {
            sendStatusMessage(client, "Could not determine an area to save.");
            return;
        }

        config.saveNamedArea(currentServerId, name, area);
        saveConfig(config);
        activateNamedArea(client, name, area, "Saved");
    }

    public static void applyNamedArea(String name) {
        Minecraft client = Minecraft.getInstance();
        if (currentServerId == null || client.level == null) {
            return;
        }
        String area = config.getNamedAreas(currentServerId).get(name);
        if (area == null) {
            return;
        }

        String server = currentServerId;
        cancelPendingLoadActivation();
        pendingLoadName = name;
        sendStatusMessage(client, "Will load \"" + name + "\" in " + LOAD_ACTIVATION_DELAY_SECONDS
                + "s - click Load on another save to change your mind.");
        pendingLoadActivation = scheduler.schedule(() -> Minecraft.getInstance().execute(() -> {
            pendingLoadName = null;
            Minecraft mc = Minecraft.getInstance();
            if (!server.equals(currentServerId) || mc.level == null) {
                return;
            }
            activateNamedArea(mc, name, area, "Loaded");
        }), LOAD_ACTIVATION_DELAY_SECONDS, TimeUnit.SECONDS);
    }

    private static void activateNamedArea(Minecraft client, String name, String area, String verb) {
        pinnedName = name;
        pinnedDimension = client.level.dimension().identifier().toString();
        forcedAreaId = sanitizeAreaId(currentServerId + "_" + area);
        sendStatusMessage(client, verb + " area \"" + name + "\" for " + currentServerId + ".");
        scheduleReload(forcedAreaId);
    }

    public static void deleteNamedArea(String name) {
        if (currentServerId == null) {
            return;
        }
        config.deleteNamedArea(currentServerId, name);
        saveConfig(config);
        if (name.equals(pinnedName)) {
            clearPin();
        }
        if (name.equals(pendingLoadName)) {
            cancelPendingLoadActivation();
        }
    }

    public static boolean renameNamedArea(String oldName, String newName) {
        Minecraft client = Minecraft.getInstance();
        if (currentServerId == null) {
            return false;
        }
        newName = newName.trim();
        if (newName.isEmpty() || newName.equals(oldName)) {
            return false;
        }
        if (!config.renameNamedArea(currentServerId, oldName, newName)) {
            sendStatusMessage(client, "Could not rename - a save named \"" + newName + "\" already exists.");
            return false;
        }
        saveConfig(config);
        if (oldName.equals(pinnedName)) {
            pinnedName = newName;
        }
        if (oldName.equals(pendingLoadName)) {
            pendingLoadName = newName;
        }
        sendStatusMessage(client, "Renamed \"" + oldName + "\" to \"" + newName + "\".");
        return true;
    }

    public static void loadDefaultArea() {
        Minecraft client = Minecraft.getInstance();
        if (currentServerId == null || client.level == null) {
            return;
        }
        cancelPendingLoadActivation();
        clearPin();
        String defaultAreaId = sanitizeAreaId(currentServerId + "_" + computeCurrentAreaValue(client));
        currentAreaId = defaultAreaId;
        sendStatusMessage(client, "Switched to default storage for this dimension.");
        scheduleReload(defaultAreaId);
    }

    private static void sendStatusMessage(Minecraft client, String message) {
        if (client.player != null) {
            client.player.displayClientMessage(Component.literal("[Voxy Dynamic] " + message), false);
        }
    }

    private static void cancelPendingReload() {
        if (pendingReload != null && !pendingReload.isDone()) {
            pendingReload.cancel(false);
        }
    }

    private static void cancelPendingLoadActivation() {
        if (pendingLoadActivation != null && !pendingLoadActivation.isDone()) {
            pendingLoadActivation.cancel(false);
        }
        pendingLoadName = null;
    }

    public static String getCurrentAreaId() {
        return currentAreaId;
    }

    private static DynamicVoxyConfig loadConfig() {
        try {
            if (!Files.exists(CONFIG_PATH)) {
                DynamicVoxyConfig defaultConfig = DynamicVoxyConfig.defaultConfig();
                saveConfig(defaultConfig);
                return defaultConfig;
            }
            try (Reader reader = Files.newBufferedReader(CONFIG_PATH, StandardCharsets.UTF_8)) {
                Type type = new TypeToken<DynamicVoxyConfig>() {}.getType();
                DynamicVoxyConfig parsed = GSON.fromJson(reader, type);
                if (parsed == null) {
                    return DynamicVoxyConfig.defaultConfig();
                }
                parsed.initializeDefaults();
                return parsed;
            }
        } catch (IOException e) {
            return DynamicVoxyConfig.defaultConfig();
        }
    }

    private static void saveConfig(DynamicVoxyConfig config) {
        try {
            if (!Files.exists(CONFIG_PATH.getParent())) {
                Files.createDirectories(CONFIG_PATH.getParent());
            }
            try (Writer writer = Files.newBufferedWriter(CONFIG_PATH, StandardCharsets.UTF_8)) {
                GSON.toJson(config, writer);
            }
        } catch (IOException ignored) {
        }
    }
}
