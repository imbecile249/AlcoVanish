package local.alcovanish;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;
import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Player;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/** Client-only entry point. */
public final class NearbyWorkshopClient implements ClientModInitializer {
	static final int[] SEND_DELAYS = {100, 500, 1000, 2000, 3000, 5000, 10000, 15000, 20000, 30000, 45000, 60000};
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static final Path CONFIG_PATH = FabricLoader.getInstance().getConfigDir().resolve("alcovanish.json");
	private static final Path OLD_CONFIG_PATH = FabricLoader.getInstance().getConfigDir().resolve("nearby-workshop.json");
	public static Config CONFIG = loadConfig();
	private static KeyMapping toggleHighlightKey;
	private static KeyMapping toggleMobHighlightKey;
	private static KeyMapping openSettingsKey;
	private static KeyMapping toggleBlockHighlightsKey;
	private static KeyMapping toggleSignsKey;
	private static KeyMapping toggleNearbyKey;
	private static KeyMapping toggleBroadcastKey;

	@Override
	public void onInitializeClient() {
		KeyMapping.Category category = KeyMapping.Category.register(Identifier.fromNamespaceAndPath("alcovanish", "general"));
		openSettingsKey = KeyMappingHelper.registerKeyMapping(new KeyMapping(
				"key.alcovanish.open_settings", InputConstants.Type.KEYSYM, InputConstants.KEY_K, category));
		toggleHighlightKey = KeyMappingHelper.registerKeyMapping(new KeyMapping(
				"key.alcovanish.toggle_highlight", InputConstants.Type.KEYSYM, InputConstants.KEY_G, category));
		toggleMobHighlightKey = KeyMappingHelper.registerKeyMapping(new KeyMapping(
				"key.alcovanish.toggle_mob_highlight", InputConstants.Type.KEYSYM, InputConstants.UNKNOWN.getValue(), category));
		toggleBlockHighlightsKey = KeyMappingHelper.registerKeyMapping(new KeyMapping(
				"key.alcovanish.toggle_block_highlights", InputConstants.Type.KEYSYM, InputConstants.KEY_V, category));
		toggleSignsKey = KeyMappingHelper.registerKeyMapping(new KeyMapping(
				"key.alcovanish.toggle_sign_mode", InputConstants.Type.KEYSYM, InputConstants.KEY_H, category));
		toggleNearbyKey = KeyMappingHelper.registerKeyMapping(new KeyMapping(
				"key.alcovanish.toggle_nearby_list", InputConstants.Type.KEYSYM, InputConstants.KEY_N, category));
		toggleBroadcastKey = KeyMappingHelper.registerKeyMapping(new KeyMapping(
				"key.alcovanish.toggle_pm_broadcast", InputConstants.Type.KEYSYM, InputConstants.UNKNOWN.getValue(), category));
		NearbyHud.init();
		TargetHealthHud.init();
		BlockHighlights.init();
		MobCounterHud.init();
		BrokenContainerCounter.init();

		ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> { CONFIG.signEnabled = false; SignAutomation.reset(); });
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> SignAutomation.reset());
		ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> dispatcher.register(
				ClientCommands.literal("signs")
						.executes(context -> { message(Minecraft.getInstance(), "Режим табличек: " + (CONFIG.signEnabled ? "вкл." : "выкл.")); return 1; })
						.then(ClientCommands.literal("on").executes(context -> { setSignModeEnabled(true); return 1; }))
						.then(ClientCommands.literal("off").executes(context -> { setSignModeEnabled(false); return 1; }))
						.then(ClientCommands.literal("reset").executes(context -> { SignAutomation.reset(); message(Minecraft.getInstance(), "Состояние обработки табличек сброшено."); return 1; }))
						.then(ClientCommands.literal("reload").executes(context -> { reloadConfig(); message(Minecraft.getInstance(), "Настройки AlcoVanish перечитаны."); return 1; }))
		));
		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			ExtraFeatures.tickTotem(client);
			BrokenContainerCounter.tick(client.level);
			while (openSettingsKey.consumeClick()) {
				if (client.player != null && client.gui.screen() == null) {
					client.gui.setScreen(new NearbyWorkshopSettingsScreen(null));
				}
			}
			PrivateMessageBroadcast.tick(client);
			while (toggleBroadcastKey.consumeClick()) {
				if (PrivateMessageBroadcast.isRunning()) PrivateMessageBroadcast.stop(client);
				else PrivateMessageBroadcast.start(client);
			}
			while (toggleMobHighlightKey.consumeClick()) {
				CONFIG.mobHighlightEnabled = !CONFIG.mobHighlightEnabled;
				saveConfig();
				message(client, "Подсветка мобов: " + (CONFIG.mobHighlightEnabled ? "вкл." : "выкл."));
			}
			while (toggleHighlightKey.consumeClick()) {
				CONFIG.highlightEnabled = !CONFIG.highlightEnabled;
				saveConfig();
				message(client, "Подсветка игроков: " + (CONFIG.highlightEnabled ? "вкл." : "выкл."));
			}
			while (toggleBlockHighlightsKey.consumeClick()) {
				CONFIG.blockHighlightEnabled = !CONFIG.blockHighlightEnabled;
				BlockHighlights.invalidate();
				saveConfig();
				message(client, "Подсветка блоков: " + (CONFIG.blockHighlightEnabled ? "вкл." : "выкл."));
			}
			while (toggleSignsKey.consumeClick()) setSignModeEnabled(!CONFIG.signEnabled);
			while (toggleNearbyKey.consumeClick()) {
				CONFIG.nearbyEnabled = !CONFIG.nearbyEnabled;
				saveConfig();
				message(client, "Список игроков рядом: " + (CONFIG.nearbyEnabled ? "вкл." : "выкл."));
			}

			SignAutomation.tick(client);
		});
	}

	public static void setHighlightingEnabled(boolean enabled) {
		CONFIG.highlightEnabled = enabled;
		saveConfig();
	}

	public static void setSignModeEnabled(boolean enabled) {
		CONFIG.signEnabled = enabled;
		SignAutomation.reset();
		saveConfig();
		Minecraft client = Minecraft.getInstance();
		if (client.player != null) message(client, "Режим табличек: " + (enabled ? "вкл." : "выкл."));
	}

	public static void saveConfig() {
		CONFIG.sanitize();
		try {
			Files.createDirectories(CONFIG_PATH.getParent());
			Files.writeString(CONFIG_PATH, GSON.toJson(CONFIG));
		} catch (IOException ignored) { }
	}

	public static void reloadConfig() {
		CONFIG = loadConfig();
		SignAutomation.reset();
		BlockHighlights.invalidate();
	}

	public static KeyMapping signKey() { return toggleSignsKey; }
	public static KeyMapping playerHighlightKey() { return toggleHighlightKey; }
	public static KeyMapping mobHighlightKey() { return toggleMobHighlightKey; }
	public static KeyMapping settingsKey() { return openSettingsKey; }
	public static KeyMapping nearbyListKey() { return toggleNearbyKey; }
	public static KeyMapping blockHighlightKey() { return toggleBlockHighlightsKey; }
	public static KeyMapping broadcastKey() { return toggleBroadcastKey; }
	public static void persistKeyMappings() {
		KeyMapping.resetMapping();
		Minecraft.getInstance().options.save();
	}

	public static List<? extends Player> nearbyPlayers(Minecraft client, double radius, int maxEntries) {
		if (client.level == null || client.player == null || maxEntries <= 0) return List.of();
		return client.level.players().stream()
				.filter(player -> player != client.player)
				.filter(player -> client.player.distanceToSqr(player) <= radius * radius)
				.sorted(Comparator.<Player, Boolean>comparing(player ->
						CONFIG.playerOutlineColor(player.getGameProfile().name(), 0) == 0)
						.thenComparingDouble(client.player::distanceToSqr))
				.limit(maxEntries)
				.toList();
	}

	private static void message(Minecraft client, String text) {
		if (client.player != null) client.player.sendOverlayMessage(Component.literal(text));
	}

	private static Config loadConfig() {
		try {
			Path source = Files.exists(CONFIG_PATH) ? CONFIG_PATH : OLD_CONFIG_PATH;
			if (Files.exists(source)) {
				Config loaded = GSON.fromJson(Files.readString(source), Config.class);
				if (loaded != null) {
					loaded.sanitize();
					if (!source.equals(CONFIG_PATH)) {
						Files.createDirectories(CONFIG_PATH.getParent());
						Files.writeString(CONFIG_PATH, GSON.toJson(loaded));
					}
					return loaded;
				}
			}
		} catch (IOException | JsonParseException ignored) { }
		Config defaults = new Config().sanitize();
		try {
			Files.createDirectories(CONFIG_PATH.getParent());
			Files.writeString(CONFIG_PATH, GSON.toJson(defaults));
		} catch (IOException ignored) { }
		return defaults;
	}

	public static final class Config {
		static final int DEFAULT_CONTAINER_HUD_RIGHT = 2;
		static final int DEFAULT_CONTAINER_HUD_TOP = 8;
		public boolean autoTool;
		public boolean noFog;
		public boolean nightVision;
		public boolean noDarkness;
		public boolean autoTotem;
		public long brokenContainers;
		public boolean brokenContainerHud;
		public double brokenContainerHudRight = DEFAULT_CONTAINER_HUD_RIGHT;
		public double brokenContainerHudTop = DEFAULT_CONTAINER_HUD_TOP;
		public int brokenContainerHudScale = 100;
		public int brokenContainerHudOpacity = 60;
		public boolean highlightEnabled;
		public String redOutlineNames = "";
		public String greenOutlineNames = "";
		public String purpleOutlineNames = "";
		public static final int RED_OUTLINE = 0xFFFF2424;
		public static final int GREEN_OUTLINE = 0xFF8FBE9D;
		public static final int PURPLE_OUTLINE = 0xFFC69AF3;
		private transient Map<String, Integer> outlineColors;

		public int playerOutlineColor(String name, int fallback) {
			return outlineColors == null ? fallback : outlineColors.getOrDefault(name.toLowerCase(java.util.Locale.ROOT), fallback);
		}

		private void addOutlineNames(String names, int color) {
			for (String name : names.split("[,;\\s]+")) {
				if (!name.isEmpty()) outlineColors.putIfAbsent(name.toLowerCase(java.util.Locale.ROOT), color);
			}
		}
		public boolean mobHighlightEnabled;
		public boolean mobCounterEnabled;
		public MobHighlights.Filter mobCounterFilter = MobHighlights.Filter.ALL;
		public int mobCounterScale = 100;
		public int mobCounterOpacity;
		public int mobCounterSoftness = 3;
		public int mobCounterX = 8;
		public int mobCounterBottom = 8;
		public MobHighlights.Filter mobHighlightFilter = MobHighlights.Filter.ALL;
		public double mobHighlightRadius;
		public double highlightRadius = 48.0;
		public boolean blockHighlightEnabled;
		public boolean highlightMechanismBlocks;
		private BlockGroupMode blockGroupMode;
		public Boolean highlightChests;
		public Boolean highlightRedstone;
		private OreHighlightMode oreHighlightMode;
		public java.util.Set<OreHighlightMode> oreHighlightModes;
		public static final int MAX_BLOCK_HIGHLIGHT_RANGE = 128;
		/** 0 means unlimited within loaded/rendered chunks; otherwise 1..64 blocks. */
		public int blockHighlightRange;
		/** 0 means unlimited within loaded/rendered chunks; otherwise 1..64 blocks for ore targets. */
		public int oreHighlightRange;
		public transient boolean signEnabled;
		public boolean signReapply = true;
		public int signDelayMinMs = 7;
		public int signDelayMaxMs = 12;
		public int signOpenTimeoutTicks = 20;
		public int signMaxAttempts = 2;
		public String[][] signTemplates = {{"", "", "", ""}};
		public String activeSignPreset = "Основной";
		public Map<String, String[][]> savedSignPresets = new java.util.LinkedHashMap<>();
		public boolean nearbyEnabled;
		public double nearbyRadius = 48.0;
		public int nearbyMaxEntries = 8;
		public boolean nearbyShowDistance = true;
		public int nearbyOffsetX;
		public int nearbyOffsetY;
		public int nearbyHudScale = 100;
		public int nearbyHudOpacity;
		public Boolean showPlayerHealth;
		public Boolean showMobHealth;
		public HealthMode playerHealthMode;
		public HealthMode mobHealthMode;
		public int overheadHealthHeight = 65;
		public OverheadStyle playerOverheadStyle;
		public OverheadStyle mobOverheadStyle;

		public static final class OverheadStyle {
			public int height;
			public int scale = 100;

			OverheadStyle(int height) {
				this.height = height;
			}

			void sanitize() {
				height = Math.clamp(height, 0, 200);
				scale = Math.clamp(scale, 50, 150);
			}
		}
		public enum HealthMode {
			OFF("Выкл"), TARGET("При наведении"), OVERHEAD("Постоянно");

			public final String label;
			HealthMode(String label) { this.label = label; }
		}
		public boolean healthHudHearts = true;
		public int healthHudScale = 75;
		public int healthHudOpacity;
		public int healthHudOffsetX;
		public int healthHudBottom = 64;
		public int nearbyPositionVersion;
		public String pmMessage = "";
		public String pmExcludedPlayers = "";
		public int pmDelayMs = 1500;

		public enum BlockGroupMode {
			OFF("Выкл"), CHESTS("Сундуки"), MECHANISMS("Механизмы и редстоун");

			private final String label;
			BlockGroupMode(String label) { this.label = label; }
			public String label() { return label; }
		}

		private static HealthMode migrateHealthMode(Boolean legacyEnabled) {
			if (legacyEnabled == null) return HealthMode.OVERHEAD;
			return legacyEnabled ? HealthMode.TARGET : HealthMode.OFF;
		}

		public Config sanitize() {
			if (mobCounterFilter == null) mobCounterFilter = MobHighlights.Filter.ALL;
			mobCounterScale = Math.clamp(mobCounterScale, 50, 150);
			brokenContainers = Math.max(0, brokenContainers);
			brokenContainerHudRight = Math.clamp(brokenContainerHudRight, 0, 100);
			brokenContainerHudTop = Math.clamp(brokenContainerHudTop, 0, 100);
			brokenContainerHudScale = Math.clamp(brokenContainerHudScale, 50, 150);
			brokenContainerHudOpacity = Math.clamp(brokenContainerHudOpacity, 0, 100);
			mobCounterOpacity = Math.clamp(mobCounterOpacity, 0, 100);
			mobCounterSoftness = Math.clamp(mobCounterSoftness, 0, 8);
			if (playerOverheadStyle == null) playerOverheadStyle = new OverheadStyle(overheadHealthHeight);
			if (mobOverheadStyle == null) mobOverheadStyle = new OverheadStyle(overheadHealthHeight);
			playerOverheadStyle.sanitize();
			mobOverheadStyle.sanitize();
			if (mobHighlightFilter == null) mobHighlightFilter = MobHighlights.Filter.ALL;
			if (!(mobHighlightRadius > 0)) mobHighlightRadius = highlightRadius;
			mobHighlightRadius = Math.clamp(mobHighlightRadius, 1, 128);
			if (redOutlineNames == null) redOutlineNames = "";
			if (greenOutlineNames == null) greenOutlineNames = "";
			if (purpleOutlineNames == null) purpleOutlineNames = "";
			outlineColors = new java.util.HashMap<>();
			addOutlineNames(redOutlineNames, RED_OUTLINE);
			addOutlineNames(greenOutlineNames, GREEN_OUTLINE);
			addOutlineNames(purpleOutlineNames, PURPLE_OUTLINE);
			if (playerHealthMode == null) playerHealthMode = migrateHealthMode(showPlayerHealth);
			if (mobHealthMode == null) mobHealthMode = migrateHealthMode(showMobHealth);
			overheadHealthHeight = Math.clamp(overheadHealthHeight, 0, 200);
			if (activeSignPreset == null || activeSignPreset.isBlank()) activeSignPreset = "Основной";
			if (savedSignPresets == null) savedSignPresets = new java.util.LinkedHashMap<>();
			nearbyHudScale = Math.clamp(nearbyHudScale, 50, 150);
			healthHudScale = Math.clamp(healthHudScale, 50, 150);
			healthHudOpacity = Math.clamp(healthHudOpacity, 0, 100);
			healthHudBottom = Math.max(0, healthHudBottom);
			nearbyHudOpacity = Math.clamp(nearbyHudOpacity, 0, 100);
			if (blockGroupMode == null) {
				blockGroupMode = blockHighlightEnabled
						? (highlightMechanismBlocks ? BlockGroupMode.MECHANISMS : BlockGroupMode.CHESTS)
						: BlockGroupMode.OFF;
			}
			if (highlightChests == null) highlightChests = blockGroupMode == BlockGroupMode.CHESTS;
			if (highlightRedstone == null) highlightRedstone = blockGroupMode == BlockGroupMode.MECHANISMS;
			highlightRadius = Math.max(1.0, Math.min(128.0, highlightRadius));
			signDelayMinMs = Math.max(0, Math.min(60000, signDelayMinMs));
			signDelayMaxMs = Math.max(signDelayMinMs, Math.min(60000, signDelayMaxMs));
			signOpenTimeoutTicks = Math.max(5, Math.min(400, signOpenTimeoutTicks));
			signMaxAttempts = Math.max(1, Math.min(10, signMaxAttempts));
			nearbyRadius = Math.max(1.0, Math.min(128.0, nearbyRadius));
			nearbyMaxEntries = Math.max(1, Math.min(32, nearbyMaxEntries));
			if (nearbyPositionVersion == 0) {
				nearbyOffsetX = 0;
				nearbyOffsetY = 0;
				nearbyPositionVersion = 1;
			}
			nearbyOffsetX = Math.max(-600, Math.min(600, nearbyOffsetX));
			nearbyOffsetY = Math.max(0, Math.min(600, nearbyOffsetY));
			if (pmMessage == null) pmMessage = "";
			if (pmMessage.length() > 220) pmMessage = pmMessage.substring(0, 220);
			if (pmExcludedPlayers == null) pmExcludedPlayers = "";
			if (pmExcludedPlayers.length() > 1024) pmExcludedPlayers = pmExcludedPlayers.substring(0, 1024);
			int nearestDelay = SEND_DELAYS[0];
			for (int delay : SEND_DELAYS) {
				if (Math.abs((long) delay - pmDelayMs) < Math.abs((long) nearestDelay - pmDelayMs)) nearestDelay = delay;
			}
			pmDelayMs = nearestDelay;
			blockHighlightRange = Math.clamp(blockHighlightRange, 0, MAX_BLOCK_HIGHLIGHT_RANGE);
			oreHighlightRange = Math.clamp(oreHighlightRange, 0, MAX_BLOCK_HIGHLIGHT_RANGE);
			if (oreHighlightModes == null) {
				oreHighlightModes = java.util.EnumSet.noneOf(OreHighlightMode.class);
				if (oreHighlightMode == OreHighlightMode.ALL) oreHighlightModes.addAll(OreHighlightMode.families());
				else if (oreHighlightMode != null && oreHighlightMode != OreHighlightMode.OFF) oreHighlightModes.add(oreHighlightMode);
			}
			oreHighlightModes.retainAll(OreHighlightMode.families());
			oreHighlightMode = null;
			if (signTemplates == null || signTemplates.length == 0) signTemplates = new String[][]{{"", "", "", ""}};
			for (int i = 0; i < signTemplates.length; i++) {
				if (signTemplates[i] == null) signTemplates[i] = new String[0];
				for (int line = 0; line < signTemplates[i].length; line++) {
					if (signTemplates[i][line] == null) signTemplates[i][line] = "";
					if (signTemplates[i][line].length() > 384) signTemplates[i][line] = signTemplates[i][line].substring(0, 384);
				}
			}
			return this;
		}
	}
}

