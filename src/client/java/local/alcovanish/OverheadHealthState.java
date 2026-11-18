package local.alcovanish;

import net.minecraft.network.chat.Component;

public interface OverheadHealthState {
	float NUMBER_SCALE = 0.75f;

	record Display(Component indicator, Component numbers, boolean player, float fraction, boolean seeThrough) {
		public static final int FILLED = 0xF5B6FF;
		static final int EMPTY = 0x806398;
		public static final int BAR_FILL = 0xFFCE9BF5;
		public static final int BAR_TRACK = 0xFF493359;
		public static final int BAR_WIDTH = 46;

		public static Display create(float health, float maxHealth, boolean player, boolean hearts) {
			return create(health, maxHealth, player, hearts, false);
		}

		public static Display create(float health, float maxHealth, boolean player, boolean hearts, boolean seeThrough) {
			float fraction = maxHealth > 0 ? Math.clamp(health / maxHealth, 0f, 1f) : 0f;
			int filled = Math.round(fraction * 10);
			Component indicator = hearts ? Component.empty()
					.append(Component.literal("♥".repeat(filled)).withColor(FILLED))
					.append(Component.literal("♥".repeat(10 - filled)).withColor(EMPTY)) : null;
			return new Display(indicator, Component.literal(String.format(java.util.Locale.ROOT,
					"%.1f / %.1f HP", health, maxHealth)), player, fraction, seeThrough);
		}
	}

	Display alcovanish$getHealthDisplay();
	void alcovanish$setHealthDisplay(Display display);
}
