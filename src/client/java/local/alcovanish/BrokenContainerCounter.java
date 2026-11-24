package local.alcovanish;

import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

import java.util.HashMap;
import java.util.Map;

public final class BrokenContainerCounter {
	private static final Map<BlockPos, PendingBreak> pending = new HashMap<>();
	private static final long CONFIRMATION_WINDOW_NANOS = 10_000_000_000L;
	private static ClientLevel pendingLevel;

	private record PendingBreak(Block block, long startedAt) { }
	private record HudLayout(NearbyHud.Bounds bounds, float scale) { }

	private BrokenContainerCounter() { }

	static void init() {
		HudElementRegistry.addLast(Identifier.fromNamespaceAndPath("alcovanish", "broken_containers"),
				BrokenContainerCounter::render);
	}

	public static void expectBreak(ClientLevel level, BlockPos pos, Block block) {
		if (pendingLevel != level) {
			pending.clear();
			pendingLevel = level;
		}
		pending.put(pos.immutable(), new PendingBreak(block, System.nanoTime()));
	}

	public static void confirmBlockUpdate(ClientLevel level, BlockPos pos, BlockState state) {
		if (pendingLevel != level) return;
		var candidate = pending.remove(pos);
		if (candidate == null || state.is(candidate.block())
				|| System.nanoTime() - candidate.startedAt() >= CONFIRMATION_WINDOW_NANOS) return;
		var config = NearbyWorkshopClient.CONFIG;
		if (config.brokenContainers < Long.MAX_VALUE) {
			config.brokenContainers++;
			NearbyWorkshopClient.saveConfig();
		}
	}

	static void tick(ClientLevel level) {
		if (pendingLevel != level) {
			pending.clear();
			pendingLevel = null;
		} else if (!pending.isEmpty()) {
			long now = System.nanoTime();
			pending.values().removeIf(candidate -> now - candidate.startedAt() >= CONFIRMATION_WINDOW_NANOS);
		}
	}

	static void reset() {
		pending.clear();
		NearbyWorkshopClient.CONFIG.brokenContainers = 0;
		NearbyWorkshopClient.saveConfig();
	}

	static String label() {
		return "Сломано контейнеров: " + NearbyWorkshopClient.CONFIG.brokenContainers;
	}

	private static void render(GuiGraphicsExtractor graphics, DeltaTracker delta) {
		var client = Minecraft.getInstance();
		var config = NearbyWorkshopClient.CONFIG;
		if (!config.brokenContainerHud || client.level == null || client.player == null
				|| client.gui.screen() instanceof ContainerCounterSettingsScreen) return;
		draw(graphics, client, client.getWindow().getGuiScaledWidth(), client.getWindow().getGuiScaledHeight());
	}

	static void draw(GuiGraphicsExtractor graphics, Minecraft client, int width, int height) {
		String text = label();
		int panelWidth = client.font.width(text) + 12;
		var layout = layout(panelWidth, width, height);
		var bounds = layout.bounds();
		int alpha = NearbyWorkshopClient.CONFIG.brokenContainerHudOpacity * 255 / 100;
		graphics.pose().pushMatrix();
		graphics.pose().translate(bounds.x(), bounds.y());
		graphics.pose().scale(layout.scale());
		if (alpha > 0) graphics.fill(0, 0, panelWidth, 19, (alpha << 24) | 0x21152E);
		graphics.text(client.font, text, 6, 5, 0xFFE9BDFF);
		graphics.pose().popMatrix();
	}

	static NearbyHud.Bounds bounds(Minecraft client, int width, int height) {
		return layout(client.font.width(label()) + 12, width, height).bounds();
	}

	private static HudLayout layout(int panelWidth, int width, int height) {
		var config = NearbyWorkshopClient.CONFIG;
		float scale = Math.min(config.brokenContainerHudScale / 100f,
				Math.min(width / (float) panelWidth, height / 19f));
		int scaledWidth = Math.min(width, (int) Math.ceil(panelWidth * scale));
		int scaledHeight = Math.min(height, (int) Math.ceil(19 * scale));
		int x = (int) Math.round((width - scaledWidth) * (100 - config.brokenContainerHudRight) / 100);
		int y = (int) Math.round((height - scaledHeight) * config.brokenContainerHudTop / 100);
		return new HudLayout(new NearbyHud.Bounds(x, y, scaledWidth, scaledHeight), scale);
	}
}
