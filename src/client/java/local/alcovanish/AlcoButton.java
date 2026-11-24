package local.alcovanish;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.network.chat.Component;

/** Draws the mod's bevelled buttons independently of the active resource packs. */
final class AlcoButton extends Button {
	private record Palette(int border, int face, int light, int shade) { }

	private static final Palette NORMAL = new Palette(0xFF000000, 0xFF574757, 0xFF6F5B72, 0xFF332333);
	private static final Palette HIGHLIGHTED = new Palette(0xFFF6B3CE, 0xFFBD5C71, 0xFFE48399, 0xFF923647);
	private static final int DISABLED_FACE = 0xFF332333;

	private AlcoButton(int x, int y, int width, int height, Component message, OnPress onPress) {
		super(x, y, width, height, message, onPress, DEFAULT_NARRATION);
	}

	static Builder styled(Component message, OnPress onPress) {
		return new Builder(message, onPress);
	}

	@Override
	protected void extractContents(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
		int left = getX();
		int top = getY();
		int right = left + getWidth();
		int bottom = top + getHeight();
		Palette palette = active && isHoveredOrFocused() ? HIGHLIGHTED : NORMAL;

		graphics.fill(left + 1, top, right - 1, top + 1, palette.border());
		graphics.fill(left + 1, bottom - 1, right - 1, bottom, palette.border());
		graphics.fill(left, top + 1, left + 1, bottom - 1, palette.border());
		graphics.fill(right - 1, top + 1, right, bottom - 1, palette.border());
		if (active) {
			graphics.fill(left + 1, top + 1, right - 1, bottom - 1, palette.shade());
			graphics.fill(left + 1, top + 1, right - 2, top + 2, palette.light());
			graphics.fill(left + 1, top + 2, right - 3, top + 3, palette.light());
			graphics.fill(left + 1, top + 3, left + 3, bottom - 3, palette.light());
			graphics.fill(left + 3, top + 3, right - 3, bottom - 3, palette.face());
			graphics.fill(left + 1, bottom - 3, left + 2, bottom - 2, palette.light());
			graphics.fill(left + 2, bottom - 3, left + 3, bottom - 2, palette.face());
			graphics.fill(left + 1, bottom - 2, left + 2, bottom - 1, palette.face());
			graphics.fill(left + 1, top + 1, left + 2, top + 2,
					palette == NORMAL ? palette.face() : palette.light());
		} else {
			graphics.fill(left + 1, top + 1, right - 1, bottom - 1, DISABLED_FACE);
		}
		extractDefaultLabel(graphics.textRendererForWidget(this, GuiGraphicsExtractor.HoveredTextEffects.NONE));
	}

	static final class Builder {
		private final Component message;
		private final OnPress onPress;
		private Tooltip tooltip;
		private int x;
		private int y;
		private int width = DEFAULT_WIDTH;
		private int height = DEFAULT_HEIGHT;

		private Builder(Component message, OnPress onPress) {
			this.message = message;
			this.onPress = onPress;
		}

		Builder bounds(int x, int y, int width, int height) {
			this.x = x;
			this.y = y;
			this.width = width;
			this.height = height;
			return this;
		}

		Builder tooltip(Tooltip tooltip) {
			this.tooltip = tooltip;
			return this;
		}

		AlcoButton build() {
			AlcoButton button = new AlcoButton(x, y, width, height, message, onPress);
			button.setTooltip(tooltip);
			return button;
		}
	}
}
