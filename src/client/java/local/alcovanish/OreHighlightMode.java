package local.alcovanish;

import net.minecraft.resources.Identifier;

/** Selects one ore family for the independent ore highlighter. */
public enum OreHighlightMode {
	OFF("Выкл", 0xFFFF62DB),
	ALL("Все руды", 0xFFE9BDFF),
	COAL("Уголь", 0xFF000000),
	IRON("Железо", 0xFFD3D0CA),
	COPPER("Медная руда", 0xFFE48758),
	GOLD("Золото", 0xFFFFD84D),
	REDSTONE("Редстоун", 0xFFFF4747),
	DIAMOND("Алмазы", 0xFF55F0E1),
	ANCIENT_DEBRIS("Незеритовые обломки", 0xFF593626),
	QUARTZ("Кварц", 0xFFFFF1D4),
	EMERALD("Изумруд", 0xFF55FF79),
	LAPIS("Лазурит", 0xFF579BFF);

	private final String label;

	private final int color;

	OreHighlightMode(String label, int color) { this.label = label; this.color = color; }

	public int color() { return color; }

	Identifier icon() {
		String texture = switch (this) {
			case ALL, DIAMOND, OFF -> "diamond_ore";
			case ANCIENT_DEBRIS -> "ancient_debris_side";
			case QUARTZ -> "nether_quartz_ore";
			default -> name().toLowerCase(java.util.Locale.ROOT) + "_ore";
		};
		return Identifier.withDefaultNamespace("textures/block/" + texture + ".png");
	}

	private static final java.util.List<OreHighlightMode> FAMILIES = java.util.Arrays.stream(values())
			.filter(mode -> mode != OFF && mode != ALL).toList();

	static java.util.List<OreHighlightMode> families() { return FAMILIES; }

	public String label() { return label; }

	public OreHighlightMode next() {
		OreHighlightMode[] values = values();
		return values[(ordinal() + 1) % values.length];
	}
}
