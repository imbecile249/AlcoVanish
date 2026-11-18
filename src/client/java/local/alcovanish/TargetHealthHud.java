package local.alcovanish;

import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.EntityHitResult;

import java.util.Locale;
import local.alcovanish.NearbyWorkshopClient.Config.HealthMode;

final class TargetHealthHud {
	private static final int WIDTH = 164;
	private static final int HEIGHT = 37;
	private static final String[] HEART = {"0110110", "1111111", "1111111", "0111110", "0011100", "0001000"};
	private TargetHealthHud() { }

	static void init() {
		HudElementRegistry.addLast(Identifier.fromNamespaceAndPath("alcovanish", "target_health"), TargetHealthHud::render);
	}

	private static void render(GuiGraphicsExtractor graphics, DeltaTracker delta) {
		var config = NearbyWorkshopClient.CONFIG;
		if (config.playerHealthMode != HealthMode.TARGET && config.mobHealthMode != HealthMode.TARGET) return;
		Minecraft client = Minecraft.getInstance();
		if (client.gui.screen() != null || !(client.hitResult instanceof EntityHitResult hit)) return;
		if (!(hit.getEntity() instanceof LivingEntity target) || !target.isAlive()) return;
		if (!(target instanceof Player && config.playerHealthMode == HealthMode.TARGET)
				&& !(target instanceof Mob && config.mobHealthMode == HealthMode.TARGET)) return;

		draw(graphics, client, target.getDisplayName().getString(), target.getHealth(), target.getMaxHealth(),
				client.getWindow().getGuiScaledWidth(), client.getWindow().getGuiScaledHeight());
	}

	static NearbyHud.Bounds bounds(int width, int height) {
		var config = NearbyWorkshopClient.CONFIG;
		float scale = config.healthHudScale / 100f;
		int panelWidth = Math.round(WIDTH * scale);
		int panelHeight = Math.round(HEIGHT * scale);
		int x = Math.clamp((width - panelWidth) / 2 + config.healthHudOffsetX, 0, Math.max(0, width - panelWidth));
		int y = Math.clamp(height - panelHeight - config.healthHudBottom, 0, Math.max(0, height - panelHeight));
		return new NearbyHud.Bounds(x, y, panelWidth, panelHeight);
	}

	static void draw(GuiGraphicsExtractor graphics, Minecraft client, String targetName, float health,
			float maxHealth, int width, int height) {
		var config = NearbyWorkshopClient.CONFIG;
		var bounds = bounds(width, height);
		graphics.pose().pushMatrix();
		try {
			graphics.pose().translate(bounds.x(), bounds.y()).scale(config.healthHudScale / 100f);
			drawPanel(graphics, client, targetName, health, maxHealth);
		} finally {
			graphics.pose().popMatrix();
		}
	}

	private static void drawPanel(GuiGraphicsExtractor graphics, Minecraft client, String targetName,
			float health, float maxHealth) {
		var config = NearbyWorkshopClient.CONFIG;
		String healthText = String.format(Locale.ROOT, "%.1f / %.1f HP", health, maxHealth);
		var font = client.font;
		int barWidth = WIDTH - 16;
		String name = font.plainSubstrByWidth(targetName, barWidth);
		float fraction = maxHealth > 0 ? Math.clamp(health / maxHealth, 0f, 1f) : 0f;
		int alpha = Math.round(config.healthHudOpacity * 255 / 100f) << 24;
		graphics.fill(0, 0, WIDTH, HEIGHT, alpha | 0x21152E);
		graphics.fill(0, 0, WIDTH, 1, alpha | 0xB28AE7);
		graphics.text(font, name, (WIDTH - font.width(name)) / 2, 5, 0xFFF0D6FA);
		graphics.text(font, healthText, (WIDTH - font.width(healthText)) / 2, 16, 0xFFE9BDFF);
		if (config.healthHudHearts) {
			drawHearts(graphics, fraction);
		} else {
			int filled = Math.round(barWidth * fraction);
			graphics.fill(8, 29, 8 + barWidth, 32, 0xFF493359);
			if (filled > 0) graphics.fill(8, 29, 8 + filled, 32, 0xFFCE9BF5);
		}
	}

	private static void drawHearts(GuiGraphicsExtractor graphics, float fraction) {
		for (int heart = 0; heart < 10; heart++) {
			int filled = Math.round(Math.clamp(fraction * 10 - heart, 0f, 1f) * 7);
			for (int row = 0; row < HEART.length; row++) {
				for (int column = 0; column < 7; column++) {
					if (HEART[row].charAt(column) != '1') continue;
					int x = 38 + heart * 9 + column;
					int y = 28 + row;
					graphics.fill(x, y, x + 1, y + 1, column < filled ? 0xFFCE9BF5 : 0xFF493359);
				}
			}
		}
	}
}
