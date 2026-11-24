package local.alcovanish;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

final class ContainerCounterSettingsScreen extends Screen {
	private final Screen parent;
	private boolean moving;
	private double grabX;
	private double grabY;

	ContainerCounterSettingsScreen(Screen parent) {
		super(Component.literal("Настройка HUD счётчика контейнеров"));
		this.parent = parent;
	}

	@Override
	protected void init() {
		var config = NearbyWorkshopClient.CONFIG;
		int controlWidth = Math.min(172, (width - 24) / 2);
		int left = width / 2 - controlWidth - 4;
		int right = width / 2 + 4;
		int y = height - 54;
		addRenderableWidget(new HudSettingsSlider(left, y, controlWidth, 100, config.brokenContainerHudScale - 50,
				step -> "Размер: " + (step + 50) + "%", step -> config.brokenContainerHudScale = step + 50));
		addRenderableWidget(new HudSettingsSlider(right, y, controlWidth, 100, config.brokenContainerHudOpacity,
				step -> "Непрозрачность фона: " + step + "%", step -> config.brokenContainerHudOpacity = step));
		addRenderableWidget(AlcoButton.styled(Component.literal("Сбросить положение"), button -> {
			config.brokenContainerHudRight = NearbyWorkshopClient.Config.DEFAULT_CONTAINER_HUD_RIGHT;
			config.brokenContainerHudTop = NearbyWorkshopClient.Config.DEFAULT_CONTAINER_HUD_TOP;
		}).bounds(left, y + 24, controlWidth, 20).build());
		addRenderableWidget(AlcoButton.styled(Component.literal("Готово"), button -> onClose())
				.bounds(right, y + 24, controlWidth, 20).build());
	}

	@Override
	public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
		super.extractBackground(graphics, mouseX, mouseY, delta);
		graphics.centeredText(font, "Перетащи счётчик мышью", width / 2, 9, 0xFFE9BDFF);
		BrokenContainerCounter.draw(graphics, minecraft, width, height);
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		if (super.mouseClicked(event, doubleClick)) return true;
		var bounds = BrokenContainerCounter.bounds(minecraft, width, height);
		if (event.button() != 0 || !bounds.contains(event.x(), event.y())) return false;
		moving = true;
		grabX = event.x() - bounds.x();
		grabY = event.y() - bounds.y();
		return true;
	}

	@Override
	public boolean mouseDragged(MouseButtonEvent event, double dx, double dy) {
		if (!moving || event.button() != 0) return super.mouseDragged(event, dx, dy);
		var bounds = BrokenContainerCounter.bounds(minecraft, width, height);
		var config = NearbyWorkshopClient.CONFIG;
		int freeWidth = width - bounds.width();
		int freeHeight = height - bounds.height();
		if (freeWidth > 0) {
			config.brokenContainerHudRight = 100 * (1 - Math.clamp((event.x() - grabX) / freeWidth, 0, 1));
		}
		if (freeHeight > 0) {
			config.brokenContainerHudTop = 100 * Math.clamp((event.y() - grabY) / freeHeight, 0, 1);
		}
		return true;
	}

	@Override
	public boolean mouseReleased(MouseButtonEvent event) {
		if (!moving || event.button() != 0) return super.mouseReleased(event);
		moving = false;
		return true;
	}

	@Override
	public void onClose() {
		NearbyWorkshopClient.saveConfig();
		minecraft.gui.setScreen(parent);
	}
}
