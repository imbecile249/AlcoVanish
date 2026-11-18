package local.alcovanish;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;

import java.util.Map;
import java.util.function.IntConsumer;
import java.util.function.IntFunction;

final class MobCounterSettingsScreen extends Screen {
	private final Screen parent;
	private boolean moving;
	private double grabX;
	private double grabY;

	MobCounterSettingsScreen(Screen parent) {
		super(Component.literal("Настройка HUD счётчика мобов"));
		this.parent = parent;
	}

	@Override
	protected void init() {
		var config = NearbyWorkshopClient.CONFIG;
		int w = Math.min(172, (width - 24) / 2);
		int left = width / 2 - w - 4;
		int right = width / 2 + 4;
		addRenderableWidget(Button.builder(filterLabel(), b -> {
			var filters = MobHighlights.Filter.values();
			config.mobCounterFilter = filters[(config.mobCounterFilter.ordinal() + 1) % filters.length];
			b.setMessage(filterLabel());
		}).bounds((width - w) / 2, 24, w, 20).build());
		addRenderableWidget(new Slider(left, 49, w, 100, config.mobCounterScale - 50,
				n -> "Размер: " + (n + 50) + "%", n -> config.mobCounterScale = n + 50));
		addRenderableWidget(new Slider(right, 49, w, 100, config.mobCounterOpacity,
				n -> "Непрозрачность фона: " + n + "%", n -> config.mobCounterOpacity = n));
		addRenderableWidget(new Slider(left, 74, w, 8, config.mobCounterSoftness,
				n -> "Размытие краёв: " + n, n -> config.mobCounterSoftness = n));
		addRenderableWidget(Button.builder(Component.literal("Сбросить положение"), b -> {
			config.mobCounterX = 8;
			config.mobCounterBottom = 8;
		}).bounds(right, 74, w, 20).build());
		addRenderableWidget(Button.builder(Component.literal("Готово"), b -> onClose())
				.bounds(width / 2 - 55, 99, 110, 20).build());
	}

	private Component filterLabel() {
		var filter = NearbyWorkshopClient.CONFIG.mobCounterFilter;
		return Component.literal("В счётчике: ").append(Component.literal(filter.label).withColor(filter.color));
	}

	@Override
	public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
		super.extractBackground(graphics, mouseX, mouseY, delta);
		Map<EntityType<?>, Integer> sample = switch (NearbyWorkshopClient.CONFIG.mobCounterFilter) {
			case ALL -> Map.of(EntityTypes.ZOMBIE, 6, EntityTypes.COW, 3, EntityTypes.WARDEN, 1);
			case FRIENDLY -> Map.of(EntityTypes.COW, 3, EntityTypes.SHEEP, 4);
			case HOSTILE -> Map.of(EntityTypes.ZOMBIE, 6, EntityTypes.WARDEN, 1);
			case BOSSES -> Map.of(EntityTypes.WARDEN, 1, EntityTypes.ELDER_GUARDIAN, 2);
		};
		MobCounterHud.draw(graphics, minecraft, sample, width, height);
		String hint = "Перетащи предпросмотр мышью";
		graphics.text(font, hint, (width - font.width(hint)) / 2, 9, 0xFFE9BDFF);
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		if (super.mouseClicked(event, doubleClick)) return true;
		var bounds = MobCounterHud.bounds(width, height);
		if (event.button() != 0 || !bounds.contains(event.x(), event.y())) return false;
		moving = true;
		grabX = event.x() - bounds.x();
		grabY = event.y() - bounds.y();
		return true;
	}

	@Override
	public boolean mouseDragged(MouseButtonEvent event, double dx, double dy) {
		if (!moving) return super.mouseDragged(event, dx, dy);
		var bounds = MobCounterHud.bounds(width, height);
		var config = NearbyWorkshopClient.CONFIG;
		config.mobCounterX = Math.clamp((int) Math.round(event.x() - grabX), 0, width - bounds.width());
		int y = Math.clamp((int) Math.round(event.y() - grabY), 0, height - bounds.height());
		config.mobCounterBottom = height - y - bounds.height();
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

	private static final class Slider extends AbstractSliderButton {
		private final int max;
		private final IntFunction<String> label;
		private final IntConsumer setter;

		Slider(int x, int y, int width, int max, int initial, IntFunction<String> label, IntConsumer setter) {
			super(x, y, width, 20, Component.empty(), initial / (double) max);
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
