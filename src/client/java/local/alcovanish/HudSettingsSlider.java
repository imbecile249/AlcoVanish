package local.alcovanish;

import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.network.chat.Component;

import java.util.function.IntConsumer;
import java.util.function.IntFunction;

final class HudSettingsSlider extends AbstractSliderButton {
	private final int max;
	private final IntFunction<String> label;
	private final IntConsumer setter;

	HudSettingsSlider(int x, int y, int width, int max, int initial, IntFunction<String> label, IntConsumer setter) {
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
