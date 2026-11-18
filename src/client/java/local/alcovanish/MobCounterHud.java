package local.alcovanish;

import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;

import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;

final class MobCounterHud {
	private MobCounterHud() { }

	static void init() {
		HudElementRegistry.addLast(Identifier.fromNamespaceAndPath("alcovanish", "mob_counter"), MobCounterHud::render);
	}

	private static void render(GuiGraphicsExtractor graphics, DeltaTracker delta) {
		var config = NearbyWorkshopClient.CONFIG;
		var client = Minecraft.getInstance();
		if (!config.mobCounterEnabled || client.level == null || client.player == null) return;
		double radius = config.mobHighlightRadius;
		Map<EntityType<?>, Integer> counts = new HashMap<>();
		for (Mob mob : client.level.getEntitiesOfClass(Mob.class, client.player.getBoundingBox().inflate(radius),
				entity -> entity.isAlive() && !entity.isInvisibleTo(client.player)
						&& entity.distanceToSqr(client.player) <= radius * radius
						&& MobHighlights.matches(entity, config.mobCounterFilter))) {
			counts.merge(mob.getType(), 1, Integer::sum);
		}
		draw(graphics, client, counts, client.getWindow().getGuiScaledWidth(), client.getWindow().getGuiScaledHeight());
	}

	static NearbyHud.Bounds bounds(int w, int h) {
		var config = NearbyWorkshopClient.CONFIG;
		float scale = config.mobCounterScale / 100f;
		int panelHeight = Math.min(h, Math.max(30, Math.round(h * 0.5f * scale)));
		int panelWidth = Math.min(w, Math.round(132 * scale));
		int x = Math.clamp(config.mobCounterX, 0, w - panelWidth);
		int top = Math.clamp(h - panelHeight - config.mobCounterBottom, 0, h - panelHeight);
		return new NearbyHud.Bounds(x, top, panelWidth, panelHeight);
	}

	static void draw(GuiGraphicsExtractor graphics, Minecraft client, Map<EntityType<?>, Integer> counts, int w, int h) {
		var config = NearbyWorkshopClient.CONFIG;
		var rows = counts.entrySet().stream().sorted(Map.Entry.<EntityType<?>, Integer>comparingByValue(Comparator.reverseOrder())
				.thenComparing(entry -> entry.getKey().getDescription().getString())).toList();
		var box = bounds(w, h);
		int panelHeight = box.height();
		int panelWidth = box.width();
		int top = box.y();
		int x = box.x();
		float scale = config.mobCounterScale / 100f;
		int alpha = Math.round(config.mobCounterOpacity * 255 / 100f);
		if (alpha > 0) {
			int fade = config.mobCounterSoftness;
			for (int i = 0; i < fade; i++) {
				int color = (alpha * (i + 1) / (fade + 1) << 24) | 0x21152E;
				graphics.fill(x + i, top + i, x + panelWidth - i, top + i + 1, color);
				graphics.fill(x + i, top + panelHeight - i - 1, x + panelWidth - i, top + panelHeight - i, color);
				graphics.fill(x + i, top + i + 1, x + i + 1, top + panelHeight - i - 1, color);
				graphics.fill(x + panelWidth - i - 1, top + i + 1, x + panelWidth - i, top + panelHeight - i - 1, color);
			}
			graphics.fill(x + fade, top + fade, x + panelWidth - fade, top + panelHeight - fade, (alpha << 24) | 0x21152E);
		}
		var font = client.font;
		float rowScale = Math.min(scale, Math.max(0.75f, (panelHeight - 28 * scale) / Math.max(1, rows.size()) / 11f));
		int capacity = Math.max(1, (int) ((panelHeight - 22 * scale) / (11 * rowScale)));
		graphics.pose().pushMatrix();
		graphics.pose().translate(x + 5, top + 5);
		graphics.pose().scale(scale);
		graphics.text(font, "Мобы рядом: " + counts.values().stream().mapToInt(Integer::intValue).sum(), 0, 0, 0xFFE9BDFF);
		graphics.pose().popMatrix();
		graphics.pose().pushMatrix();
		graphics.pose().translate(x + 5, top + 19 * scale);
		graphics.pose().scale(rowScale);
		int textWidth = (int) ((panelWidth - 10) / rowScale);
		int visible = Math.min(rows.size(), capacity);
		if (rows.size() > capacity) visible--;
		for (int i = 0; i < visible; i++) {
			var entry = rows.get(i);
			String count = entry.getValue().toString();
			String name = entry.getKey().getDescription().getString();
			int available = Math.max(0, textWidth - font.width(count) - 6);
			if (font.width(name) > available) {
				name = font.plainSubstrByWidth(name, Math.max(0, available - font.width("…"))).stripTrailing() + "…";
			}
			graphics.text(font, name, 0, i * 11, 0xFFF2E5F5);
			graphics.text(font, count, textWidth - font.width(count), i * 11, 0xFFE9BDFF);
		}
		if (rows.size() > visible) graphics.text(font, "Ещё видов: " + (rows.size() - visible), 0, visible * 11, 0xFFE9BDFF);
		if (rows.isEmpty()) graphics.text(font, "Никого в радиусе", 0, 0, 0xFFF2E5F5);
		graphics.pose().popMatrix();
	}
}
