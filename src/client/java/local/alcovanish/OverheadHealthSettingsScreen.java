package local.alcovanish;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

import java.util.Locale;
import java.util.function.IntConsumer;
import java.util.function.IntFunction;

final class OverheadHealthSettingsScreen extends Screen {
	private static final Identifier ALEX = Identifier.withDefaultNamespace("textures/entity/player/slim/alex.png");
	private static final Identifier SKELETON = Identifier.withDefaultNamespace("textures/entity/skeleton/wither_skeleton.png");
	private final Screen parent;
	private boolean players = true;
	private int left;
	private int top;
	private int panelWidth;
	private int previewHeight;

	OverheadHealthSettingsScreen(Screen parent) {
		super(Component.literal("Постоянное отображение HP"));
		this.parent = parent;
	}

	@Override
	protected void init() {
		panelWidth = Math.min(330, width - 12);
		previewHeight = Math.clamp(height - 100, 48, 122);
		left = (width - panelWidth) / 2;
		top = Math.max(2, (height - previewHeight - 96) / 2);
		int tabWidth = (panelWidth - 28) / 2;
		addRenderableWidget(AlcoButton.styled(Component.literal("Игроки"), b -> select(true))
				.bounds(left + 10, top + 8, tabWidth, 20).build()).active = !players;
		addRenderableWidget(AlcoButton.styled(Component.literal("Мобы"), b -> select(false))
				.bounds(left + 18 + tabWidth, top + 8, tabWidth, 20).build()).active = players;
		var config = NearbyWorkshopClient.CONFIG;
		var style = players ? config.playerOverheadStyle : config.mobOverheadStyle;
		int y = top + previewHeight + 30;
		int w = panelWidth - 20;
		addRenderableWidget(new Slider(left + 10, y, w, 200, style.height,
				n -> String.format(Locale.ROOT, "Высота: %.2f бл.", n / 100.0), n -> style.height = n));
		addRenderableWidget(new Slider(left + 10, y + 21, w, 100, style.scale - 50,
				n -> "Размер: " + (n + 50) + "%", n -> style.scale = n + 50));
		addRenderableWidget(AlcoButton.styled(Component.literal("Готово"), b -> onClose())
				.bounds(width / 2 - 55, y + 43, 110, 18).build());
	}

	private void select(boolean playerTab) {
		players = playerTab;
		clearWidgets();
		init();
	}

	@Override
	public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
		super.extractBackground(graphics, mouseX, mouseY, delta);
		int bottom = top + previewHeight + 96;
		graphics.fill(left, top, left + panelWidth, bottom, 0xFF665078);
		graphics.fill(left + 1, top + 1, left + panelWidth - 1, bottom - 1, 0xFF21152E);
		int mid = width / 2;
		int previewTop = top + 31;
		int previewBottom = previewTop + previewHeight - 3;
		graphics.fill(left + 10, previewTop, left + panelWidth - 10, previewBottom, 0xFF171020);
		graphics.enableScissor(left + 10, previewTop, left + panelWidth - 10, previewBottom);
		var config = NearbyWorkshopClient.CONFIG;
		var style = players ? config.playerOverheadStyle : config.mobOverheadStyle;
		var display = OverheadHealthState.Display.create(13, 20, players, config.healthHudHearts);
		float entityHeight = players ? 1.8f : 2.4f;
		float labelScale = 0.025f * style.scale / 100f;
		float pixelsPerBlock = Math.min(26f, (previewBottom - previewTop - 6f)
				/ (entityHeight + 2f + 24 * labelScale));
		float characterHeight = entityHeight * pixelsPerBlock;
		float headY = previewBottom - 3 - characterHeight;
		float characterScale = characterHeight / 46f;
		graphics.pose().pushMatrix();
		graphics.pose().translate(mid, headY);
		graphics.pose().scale(characterScale);
		drawCharacter(graphics, 0, 0);
		graphics.pose().popMatrix();
		if (players) {
			graphics.pose().pushMatrix();
			graphics.pose().translate(mid, headY - 0.25f * pixelsPerBlock);
			graphics.pose().scale(0.025f * pixelsPerBlock);
			graphics.text(font, "Alex", -font.width("Alex") / 2, 0, 0xFFFFFFFF);
			graphics.pose().popMatrix();
		}
		float scale = labelScale * pixelsPerBlock;
		float indicatorY = headY - ((players ? 0.25f : 0f) + style.height / 100f) * pixelsPerBlock;
		graphics.pose().pushMatrix();
		graphics.pose().translate(mid, indicatorY);
		graphics.pose().scale(scale);
		graphics.pose().pushMatrix();
		graphics.pose().translate(0, -22);
		graphics.pose().scale(OverheadHealthState.NUMBER_SCALE);
		drawLine(graphics, display.numbers(), 0);
		graphics.pose().popMatrix();
		if (display.indicator() != null) {
			drawLine(graphics, display.indicator(), -9);
		} else {
			int left = -OverheadHealthState.Display.BAR_WIDTH / 2;
			int right = left + OverheadHealthState.Display.BAR_WIDTH;
			int fill = left + Math.round(OverheadHealthState.Display.BAR_WIDTH * display.fraction());
			graphics.fill(left, -7, right, -4, OverheadHealthState.Display.BAR_TRACK);
			if (fill > left) graphics.fill(left, -7, fill, -4, OverheadHealthState.Display.BAR_FILL);
		}
		graphics.pose().popMatrix();
		graphics.disableScissor();
	}

	private void drawLine(GuiGraphicsExtractor graphics, Component text, int y) {
		int x = -font.width(text) / 2;
		graphics.text(font, text, x, y, 0xFFFFF0FF);
	}

	private void drawCharacter(GuiGraphicsExtractor graphics, int x, int y) {
		Identifier texture = players ? ALEX : SKELETON;
		int textureHeight = players ? 64 : 32;
		part(graphics, texture, textureHeight, x - 6, y, 12, 12, 8, 8, 8, 8);
		part(graphics, texture, textureHeight, x - 6, y + 12, 12, 17, 20, 20, 8, 12);
		int arm = players ? 3 : 2;
		part(graphics, texture, textureHeight, x - 10, y + 12, 4, 17, 44, 20, arm, 12);
		part(graphics, texture, textureHeight, x + 6, y + 12, 4, 17, players ? 36 : 44, players ? 52 : 20, arm, 12);
		part(graphics, texture, textureHeight, x - 6, y + 29, 6, 17, 4, 20, players ? 4 : 2, 12);
		part(graphics, texture, textureHeight, x, y + 29, 6, 17, players ? 20 : 4, players ? 52 : 20, players ? 4 : 2, 12);
	}

	private static void part(GuiGraphicsExtractor graphics, Identifier texture, int textureHeight,
			int x, int y, int width, int height, int u, int v, int regionWidth, int regionHeight) {
		graphics.blit(texture, x, y, x + width, y + height, u / 64f, (u + regionWidth) / 64f,
				v / (float) textureHeight, (v + regionHeight) / (float) textureHeight);
	}

	@Override
	public void onClose() {
		NearbyWorkshopClient.saveConfig();
		minecraft.gui.setScreen(parent);
	}

	private static final class Slider extends AbstractSliderButton {
		private final int max;
		private final IntFunction<String> label;
		private final IntConsumer setter;

		Slider(int x, int y, int width, int max, int initial, IntFunction<String> label, IntConsumer setter) {
			super(x, y, width, 19, Component.empty(), initial / (double) max);
			this.max = max;
			this.label = label;
			this.setter = setter;
			updateMessage();
		}

		@Override
		protected void updateMessage() { setMessage(Component.literal(label.apply((int) Math.round(value * max)))); }

		@Override
		protected void applyValue() { setter.accept((int) Math.round(value * max)); }
	}
}
