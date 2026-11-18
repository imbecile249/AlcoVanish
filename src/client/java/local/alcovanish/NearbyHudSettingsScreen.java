package local.alcovanish;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

import java.util.List;

final class NearbyHudSettingsScreen extends Screen {
	private final Screen parent;
	private final boolean health;
	private boolean moving;
	private double grabX;
	private double grabY;

	NearbyHudSettingsScreen(Screen parent) {
		this(parent, false);
	}

	NearbyHudSettingsScreen(Screen parent, boolean health) {
		super(Component.literal(health ? "Настройка панели HP" : "Настройка HUD игроков"));
		this.parent = parent;
		this.health = health;
	}

	@Override
	protected void init() {
		int controlWidth = Math.min(190, (width - 30) / 2);
		addRenderableWidget(new HudSlider(width / 2 - controlWidth - 4, controlWidth, true));
		addRenderableWidget(new HudSlider(width / 2 + 4, controlWidth, false));
		addRenderableWidget(Button.builder(Component.literal("Готово"), button -> onClose())
				.bounds(width / 2 - 55, health ? 59 : height - 28, 110, 20).build());
		if (health) {
			addRenderableWidget(Button.builder(Component.literal("Сбросить положение"), button -> {
				NearbyWorkshopClient.CONFIG.healthHudOffsetX = 0;
				NearbyWorkshopClient.CONFIG.healthHudBottom = 64;
			}).bounds(width / 2 - 75, 85, 150, 20).build());
		}
	}

	@Override
	public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
		super.extractBackground(graphics, mouseX, mouseY, delta);
		if (health) TargetHealthHud.draw(graphics, minecraft, "Пример цели", 13, 20, width, height);
		else NearbyHud.draw(graphics, minecraft, List.of(), width, height);
		graphics.text(font, "Перетащи панель мышью", 12, health ? 12 : height - 76, 0xFFF0D6FA);
	}

	private NearbyHud.Bounds panelBounds() {
		return health ? TargetHealthHud.bounds(width, height) : NearbyHud.bounds(width, height, 1);
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		if (super.mouseClicked(event, doubleClick)) return true;
		var bounds = panelBounds();
		if (event.button() != 0 || !bounds.contains(event.x(), event.y())) return false;
		grabX = event.x() - bounds.x();
		grabY = event.y() - bounds.y();
		moving = true;
		return true;
	}

	@Override
	public boolean mouseDragged(MouseButtonEvent event, double dx, double dy) {
		if (!moving) return super.mouseDragged(event, dx, dy);
		var config = NearbyWorkshopClient.CONFIG;
		var bounds = panelBounds();
		int x = Math.clamp((int) Math.round(event.x() - grabX), 0, Math.max(0, width - bounds.width()));
		int y = Math.clamp((int) Math.round(event.y() - grabY), 0, Math.max(0, height - bounds.height()));
		int offsetX = x - (width - bounds.width()) / 2;
		if (health) {
			config.healthHudOffsetX = offsetX;
			config.healthHudBottom = height - y - bounds.height();
		} else {
			config.nearbyOffsetX = offsetX;
			config.nearbyOffsetY = y;
		}
		return true;
	}

	@Override
	public boolean mouseReleased(MouseButtonEvent event) {
		if (!moving) return super.mouseReleased(event);
		moving = false;
		return true;
	}

	@Override
	public void onClose() {
		NearbyWorkshopClient.saveConfig();
		minecraft.gui.setScreen(parent);
	}

	private final class HudSlider extends AbstractSliderButton {
		private final boolean scale;

		HudSlider(int x, int width, boolean scale) {
			super(x, health ? 33 : NearbyHudSettingsScreen.this.height - 55, width, 20, Component.empty(),
					sliderValue(scale));
			this.scale = scale;
			updateMessage();
		}

		@Override
		protected void updateMessage() {
			int percent = (int) Math.round(value * 100) + (scale ? 50 : 0);
			setMessage(Component.literal((scale ? "Масштаб: " : "Непрозрачность фона: ") + percent + "%"));
		}

		@Override
		protected void applyValue() {
			int percent = (int) Math.round(value * 100);
			var config = NearbyWorkshopClient.CONFIG;
			if (health) {
				if (scale) config.healthHudScale = 50 + percent;
				else config.healthHudOpacity = percent;
			} else if (scale) config.nearbyHudScale = 50 + percent;
			else config.nearbyHudOpacity = percent;
		}
	}

	private double sliderValue(boolean scale) {
		var config = NearbyWorkshopClient.CONFIG;
		if (scale) return ((health ? config.healthHudScale : config.nearbyHudScale) - 50) / 100.0;
		return (health ? config.healthHudOpacity : config.nearbyHudOpacity) / 100.0;
	}
}
