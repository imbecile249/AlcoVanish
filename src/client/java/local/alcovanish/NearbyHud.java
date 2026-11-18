package local.alcovanish;

import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Player;

import java.util.List;

/** Small in-world list of the nearest client-known players. */
public final class NearbyHud {
	private static final int WIDTH = 170;

	record Bounds(int x, int y, int width, int height) {
		boolean contains(double mouseX, double mouseY) {
			return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
		}
	}
	private NearbyHud() { }

	public static void init() {
		HudElementRegistry.addLast(Identifier.fromNamespaceAndPath("alcovanish", "nearby_players"), NearbyHud::render);
	}

	private static void render(GuiGraphicsExtractor graphics, net.minecraft.client.DeltaTracker deltaTracker) {
		var config = NearbyWorkshopClient.CONFIG;
		if (!config.nearbyEnabled) return;
		Minecraft client = Minecraft.getInstance();
		if (client.player == null || client.level == null) return;

		List<? extends Player> players = NearbyWorkshopClient.nearbyPlayers(client, config.nearbyRadius, config.nearbyMaxEntries);
		if (players.isEmpty()) return;
		draw(graphics, client, players, client.getWindow().getGuiScaledWidth(), client.getWindow().getGuiScaledHeight());
	}

	static Bounds bounds(int screenWidth, int screenHeight, int rows) {
		var config = NearbyWorkshopClient.CONFIG;
		float scale = config.nearbyHudScale / 100f;
		int width = Math.round(WIDTH * scale);
		int height = Math.round((22 + Math.max(1, rows) * 14) * scale);
		int x = Math.clamp((screenWidth - width) / 2 + config.nearbyOffsetX, 0, Math.max(0, screenWidth - width));
		int y = Math.clamp(config.nearbyOffsetY, 0, Math.max(0, screenHeight - height));
		return new Bounds(x, y, width, height);
	}

	static void draw(GuiGraphicsExtractor graphics, Minecraft client, List<? extends Player> players,
			int screenWidth, int screenHeight) {
		var config = NearbyWorkshopClient.CONFIG;
		Bounds bounds = bounds(screenWidth, screenHeight, players.size());
		float scale = config.nearbyHudScale / 100f;
		graphics.pose().pushMatrix();
		try {
			graphics.pose().translate(bounds.x(), bounds.y()).scale(scale);
			drawPanel(graphics, client, players);
		} finally {
			graphics.pose().popMatrix();
		}
	}

	private static void drawPanel(GuiGraphicsExtractor graphics, Minecraft client, List<? extends Player> players) {
		var config = NearbyWorkshopClient.CONFIG;
		int x = 0;
		int y = 0;
		int width = WIDTH;
		int height = 18 + Math.max(1, players.size()) * 14 + 4;
		int alpha = Math.round(config.nearbyHudOpacity * 255 / 100f);
		graphics.fill(x, y, x + width, y + height, (alpha << 24) | 0x171020);
		graphics.fill(x, y, x + width, y + 2, 0xA0FF69C6);
		graphics.text(client.font, "Игроки рядом", x + 8, y + 7, 0xFFFFFFFF);
		if (players.isEmpty()) {
			graphics.text(client.font, "Никого в радиусе", x + 8, y + 22, 0xFFC0AFC0);
			return;
		}

		int rowY = y + 22;
		for (Player player : players) {
			String name = player.getGameProfile().name();
			graphics.text(client.font, name, x + 8, rowY, config.playerOutlineColor(name, 0xFFF2F6FA));
			String status = "";
			if (player.isInvisible()) status += "◉";
			if (client.getConnection() != null && client.getConnection().getPlayerInfo(player.getUUID()) == null) status += "?";
			if (!status.isEmpty()) {
				int rightPadding = 8;
				if (config.nearbyShowDistance) {
					int distance = (int) Math.ceil(Math.sqrt(client.player.distanceToSqr(player)));
					String value = distance + " м";
					rightPadding += client.font.width(value) + 5;
				}
				graphics.text(client.font, status, x + width - client.font.width(status) - rightPadding, rowY, 0xFFFF85D0);
			}
			if (config.nearbyShowDistance) {
				int distance = (int) Math.ceil(Math.sqrt(client.player.distanceToSqr(player)));
				String value = distance + " м";
				graphics.text(client.font, value, x + width - client.font.width(value) - 8, rowY, 0xFFDB85D1);
			}
			rowY += 14;
		}
	}
}

