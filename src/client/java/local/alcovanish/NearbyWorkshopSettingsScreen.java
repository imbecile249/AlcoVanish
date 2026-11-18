package local.alcovanish;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Checkbox;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.IntConsumer;

/** Original compact settings panel opened from Mod Menu. */
public final class NearbyWorkshopSettingsScreen extends Screen {
	
	private static final int PANEL_WIDTH = 520;
	private static final int PANEL_HEIGHT = 478;
	private static final int EDGE = 4;
	private static final int SIGN_FIELD_WIDTH = 98;
	private static final int SIGN_PREVIEW_X = 473;
	private static final net.minecraft.resources.Identifier OAK_SIGN_TEXTURE =
			net.minecraft.resources.Identifier.withDefaultNamespace("textures/item/oak_sign.png");
	private enum Tab { SIGNS, PLAYERS, MOBS, BLOCKS, MESSAGES, HEALTH }

	private final Screen parent;
	private Tab tab = Tab.SIGNS;
	private final Map<String, EditBox> fields = new LinkedHashMap<>();
	private final Map<String, DropdownSpec> dropdowns = new LinkedHashMap<>();
	private int templatePage;
	private String signTransferStatus = "";
	private int panelLeft;
	private int panelTop;
	private int frameTop;
	private int frameBottom;
	private int headerCount;
	private int bodyEnd;
	private float menuScale;
	private int menuWidth, menuHeight;
	private int scrollY;
	private int previewEntry = -1;
	private int previewLeft;
	private int previewTop;
	private String[] previewTemplate = {};
	private KeyMapping capturingKey;
	private String openDropdown;

	private record DropdownSpec(String id, String[] options, int x, int y, int width, IntConsumer onSelect) { }

	public NearbyWorkshopSettingsScreen(Screen parent) {
		super(Component.literal("AlcoVanish"));
		this.parent = parent;
	}

	@Override
	protected void init() {
		fields.clear();
		dropdowns.clear();
		menuScale = Math.min(1f, (float) width / (PANEL_WIDTH + EDGE * 2));
		menuWidth = Math.round(width / menuScale);
		menuHeight = Math.round(height / menuScale);
		frameTop = panelOrigin(menuHeight, PANEL_HEIGHT);
		frameBottom = Math.min(menuHeight - EDGE, frameTop + PANEL_HEIGHT);
		scrollY = Math.clamp(scrollY, 0, verticalLimit());
		panelLeft = panelOrigin(menuWidth, PANEL_WIDTH);
		panelTop = frameTop;
		if (previewEntry >= 0) {
			addPreviewCloseButton();
			return;
		}
		addKeybind("Открыть настройки", NearbyWorkshopClient.settingsKey(), panelLeft + 270, panelTop + 14, 228);
		int tabY = panelTop + 48;
		addTabButton("Таблички", Tab.SIGNS, panelLeft + 22, tabY, 87);
		addTabButton("Игроки", Tab.PLAYERS, panelLeft + 115, tabY, 76);
		addTabButton("Мобы", Tab.MOBS, panelLeft + 197, tabY, 66);
		addTabButton("Блоки", Tab.BLOCKS, panelLeft + 269, tabY, 68);
		addTabButton("Рассылка", Tab.MESSAGES, panelLeft + 343, tabY, 89);
		addTabButton("HP", Tab.HEALTH, panelLeft + 438, tabY, 60);

		headerCount = children().size();
		panelTop -= scrollY;
		switch (tab) {
			case SIGNS -> buildSignsTab();
			case PLAYERS -> buildPlayersTab();
			case MOBS -> buildMobsTab();
			case BLOCKS -> buildBlockHighlightsTab();
			case MESSAGES -> buildMessagesTab();
			case HEALTH -> buildHealthTab();
		}

		bodyEnd = children().size();
		addRenderableWidget(Button.builder(Component.literal("Готово"), button -> onClose())
				.bounds((menuWidth - 150) / 2, frameBottom - 30, 150, 24).build());
	}

	private void buildSignsTab() {
		var config = NearbyWorkshopClient.CONFIG;
		addToggle("РЕЖИМ ТАБЛИЧЕК", config.signEnabled, NearbyWorkshopClient::setSignModeEnabled,
				panelLeft + 22, panelTop + 82, 220);
		addKeybind("Клавиша режима табличек", NearbyWorkshopClient.signKey(), panelLeft + 263, panelTop + 82, 232);
		addToggle("Перезаписывать готовый текст", config.signReapply, value -> config.signReapply = value,
				panelLeft + 22, panelTop + 108, 232);
		addRenderableWidget(Button.builder(Component.literal("‹"), b -> switchPreset(-1))
				.bounds(panelLeft + 112, panelTop + 140, 21, 21).build());
		addInput("presetName", "Имя набора", config.activeSignPreset, panelLeft + 136, panelTop + 140, 235);
		addRenderableWidget(Button.builder(Component.literal("›"), b -> switchPreset(1))
				.bounds(panelLeft + 374, panelTop + 140, 21, 21).build());
		addRenderableWidget(Button.builder(Component.literal("+"), b -> createPreset())
				.tooltip(Tooltip.create(Component.literal("Создать пустой набор с именем из поля.")))
				.bounds(panelLeft + 398, panelTop + 140, 25, 21).build());
		addRenderableWidget(Button.builder(Component.literal("✓"), b -> renamePreset())
				.tooltip(Tooltip.create(Component.literal("Переименовать текущий набор.")))
				.bounds(panelLeft + 426, panelTop + 140, 25, 21).build());
		addRenderableWidget(Button.builder(Component.literal("Набор ✕"), b -> confirmDeletePreset())
				.bounds(panelLeft + 454, panelTop + 140, 44, 21).build()).active = !config.savedSignPresets.isEmpty();
		config.sanitize();
		templatePage = Math.max(0, Math.min(templatePage, signPageCount(config.signTemplates.length) - 1));
		int start = templatePage * 7;
		for (int row = 0; row < 7; row++) {
			int entryRow = row;
			int index = start + row;
			String[] value = index < config.signTemplates.length ? config.signTemplates[index] : new String[0];
			int y = panelTop + 167 + row * 23;
			for (int line = 0; line < 4; line++) {
				addSignLine(row, line, line < value.length ? value[line] : "",
						panelLeft + 72 + line * (SIGN_FIELD_WIDTH + 2), y);
			}
			addRenderableWidget(Button.builder(Component.empty(), button -> {
				commitFields();
				NearbyWorkshopClient.saveConfig();
				previewTemplate = readSignRow(entryRow);
				previewEntry = templatePage * 7 + entryRow;
				rebuildTab();
			}).tooltip(Tooltip.create(Component.literal("Предпросмотр текста на табличке.")))
					.bounds(panelLeft + SIGN_PREVIEW_X, y, 19, 21).build());
			addRenderableWidget(Button.builder(Component.literal("✕").withStyle(Style.EMPTY.withColor(0xB46BFF)), button -> {
				commitFields();
				for (int line = 0; line < 4; line++) fields.get(signField(entryRow, line)).setValue("");
				commitFields();
				NearbyWorkshopClient.saveConfig();
			}).tooltip(Tooltip.create(Component.literal("Очистить текст этой записи.")))
					.bounds(panelLeft + 495, y, 19, 21).build());
		}
		addRenderableWidget(Button.builder(Component.literal("‹"), button -> changeTemplatePage(-1))
				.bounds(panelLeft + 22, panelTop + 331, 30, 22).build());
		addRenderableWidget(Button.builder(Component.literal("›"), button -> changeTemplatePage(1))
				.bounds(panelLeft + 55, panelTop + 331, 30, 22).build());
		addRenderableWidget(Button.builder(Component.literal("Добавить"), button -> addTemplate())
				.tooltip(Tooltip.create(Component.literal("Добавить запись: одна запись соответствует одной табличке.")))
				.bounds(panelLeft + 101, panelTop + 331, 100, 22).build());
		addRenderableWidget(Button.builder(Component.literal("Удалить"), button -> removeTemplate())
				.tooltip(Tooltip.create(Component.literal("Удаляет последнюю запись на этой странице.")))
				.bounds(panelLeft + 207, panelTop + 331, 100, 22).build());
		addRenderableWidget(Button.builder(Component.literal("Импорт"), button -> transferSignPresets(true))
				.tooltip(Tooltip.create(Component.literal("Добавить наборы из config/" + SignPresetTransfer.FILE_NAME)))
				.bounds(panelLeft + 263, panelTop + 108, 112, 22).build());
		addRenderableWidget(Button.builder(Component.literal("Экспорт"), button -> transferSignPresets(false))
				.tooltip(Tooltip.create(Component.literal("Записать все наборы в config/" + SignPresetTransfer.FILE_NAME
						+ ". Предыдущий экспорт будет заменён.")))
				.bounds(panelLeft + 383, panelTop + 108, 112, 22).build());
		addInput("signDelayMinMs", "Задержка от, мс", String.valueOf(config.signDelayMinMs), panelLeft + 112, panelTop + 362, 112);
		addInput("signDelayMaxMs", "Задержка до, мс", String.valueOf(config.signDelayMaxMs), panelLeft + 353, panelTop + 362, 112);
		addInput("signOpenTimeoutTicks", "Таймаут, тики", String.valueOf(config.signOpenTimeoutTicks), panelLeft + 112, panelTop + 388, 112);
		addInput("signMaxAttempts", "Попыток на знак", String.valueOf(config.signMaxAttempts), panelLeft + 353, panelTop + 388, 112);
	}

	private void transferSignPresets(boolean importing) {
		commitFields();
		try {
			if (importing) {
				int count = SignPresetTransfer.importPresets();
				NearbyWorkshopClient.saveConfig();
				signTransferStatus = count == 0 ? "Все наборы из файла уже есть." : "Добавлено наборов: " + count;
				rebuildTab();
			} else {
				int count = SignPresetTransfer.exportPresets();
				signTransferStatus = "Экспортировано наборов: " + count;
			}
		} catch (java.nio.file.NoSuchFileException exception) {
			signTransferStatus = "Файл импорта не найден";
		} catch (com.google.gson.JsonParseException exception) {
			signTransferStatus = "Некорректный файл импорта";
		} catch (java.io.IOException exception) {
			signTransferStatus = "Не удалось " + (importing ? "прочитать" : "записать") + " файл наборов.";
		}
	}

	private void addPreviewCloseButton() {
		previewLeft = (menuWidth - 300) / 2;
		previewTop = (menuHeight - 184) / 2;
		addRenderableWidget(Button.builder(Component.literal("✕").withStyle(Style.EMPTY.withColor(0xFFFF85D0)), button -> closePreview())
				.tooltip(Tooltip.create(Component.literal("Закрыть предпросмотр.")))
				.bounds(previewLeft + 264, previewTop + 8, 26, 22).build());
	}

	private void closePreview() {
		previewEntry = -1;
		previewTemplate = new String[0];
		rebuildTab();
	}

	private static int signPageCount(int entryCount) {
		return Math.max(1, (entryCount + 6) / 7);
	}

	private void switchPreset(int direction) {
		commitFields();
		var config = NearbyWorkshopClient.CONFIG;
		List<String> names = new ArrayList<>(config.savedSignPresets.keySet());
		names.add(config.activeSignPreset);
		names.sort(String.CASE_INSENSITIVE_ORDER);
		String next = names.get(Math.floorMod(names.indexOf(config.activeSignPreset) + direction, names.size()));
		if (next.equals(config.activeSignPreset)) return;
		config.savedSignPresets.put(config.activeSignPreset, config.signTemplates);
		config.signTemplates = config.savedSignPresets.remove(next);
		config.activeSignPreset = next;
		finishPresetChange();
	}

	private String newPresetName() {
		String name = fields.get("presetName").getValue().strip();
		var config = NearbyWorkshopClient.CONFIG;
		if (name.isBlank() || name.equalsIgnoreCase(config.activeSignPreset)
				|| config.savedSignPresets.keySet().stream().anyMatch(name::equalsIgnoreCase)) {
			fields.get("presetName").setTooltip(Tooltip.create(Component.literal("Введи новое уникальное имя набора.")));
			fields.get("presetName").setTextColor(0xFFFF7777);
			return "";
		}
		return name;
	}

	private void createPreset() {
		String name = newPresetName();
		if (name.isEmpty()) return;
		commitFields();
		var config = NearbyWorkshopClient.CONFIG;
		config.savedSignPresets.put(config.activeSignPreset, config.signTemplates);
		config.activeSignPreset = name;
		config.signTemplates = new String[][]{{"", "", "", ""}};
		finishPresetChange();
	}

	private void renamePreset() {
		String name = newPresetName();
		if (name.isEmpty()) return;
		commitFields();
		NearbyWorkshopClient.CONFIG.activeSignPreset = name;
		finishPresetChange();
	}

	private void confirmDeletePreset() {
		commitFields();
		NearbyWorkshopClient.saveConfig();
		var config = NearbyWorkshopClient.CONFIG;
		minecraft.gui.setScreen(new net.minecraft.client.gui.screens.ConfirmScreen(confirmed -> {
			if (confirmed && !config.savedSignPresets.isEmpty()) {
				var next = config.savedSignPresets.entrySet().iterator().next();
				config.activeSignPreset = next.getKey();
				config.signTemplates = config.savedSignPresets.remove(config.activeSignPreset);
				templatePage = 0;
				SignAutomation.reset();
				NearbyWorkshopClient.saveConfig();
			}
			minecraft.gui.setScreen(this);
		}, Component.literal("Удалить набор «" + config.activeSignPreset + "»?"),
				Component.literal("Все шаблоны этого набора будут удалены.")));
	}

	private void finishPresetChange() {
		templatePage = 0;
		SignAutomation.reset();
		NearbyWorkshopClient.saveConfig();
		rebuildTab();
	}

	private static String signField(int row, int line) {
		return "signEntry" + row + "." + line;
	}

	private void addSignLine(int row, int line, String value, int x, int y) {
		EditBox input = new EditBox(font, x, y, SIGN_FIELD_WIDTH, 21, Component.literal("Строка " + (line + 1))) {
			@Override
			public void setFocused(boolean focused) {
				super.setFocused(focused);
				if (!focused) moveCursorToStart(false);
			}

			@Override
			public void insertText(String text) {
				int cursor = getCursorPosition();
				String previous = getValue();
				super.insertText(text);
				if (signWidth(getValue()) > 90 && signWidth(getValue()) >= signWidth(previous)) {
					setValue(previous);
					setCursorPosition(cursor);
				}
			}
		};
		input.setMaxLength(384);
		input.setValue(value);
		input.moveCursorToStart(false);
		input.setHint(Component.literal(String.valueOf(line + 1)));
		input.setTooltip(Tooltip.create(Component.literal("Строка " + (line + 1) + " · до 90 пикселей текста")));
		input.setResponder(text -> input.setTooltip(Tooltip.create(Component.literal(
				font.width(text) > input.getInnerWidth() ? "Продолжение: " + text : "Строка " + (line + 1) + " · до 90 пикселей текста"))));
		if (font.width(value) > input.getInnerWidth()) {
			input.setTooltip(Tooltip.create(Component.literal("Продолжение: " + value)));
		}
		fields.put(signField(row, line), input);
		addRenderableWidget(input);
	}

	private int signWidth(String value) {
		String clean = net.minecraft.ChatFormatting.stripFormatting(value);
		return clean == null ? 0 : font.width(clean);
	}

	private String[] readSignRow(int row) {
		String[] lines = new String[4];
		for (int line = 0; line < 4; line++) lines[line] = fields.get(signField(row, line)).getValue();
		return lines;
	}

	private void buildPlayersTab() {
		var config = NearbyWorkshopClient.CONFIG;
		addToggle("Подсветка сквозь стены", config.highlightEnabled, NearbyWorkshopClient::setHighlightingEnabled,
				panelLeft + 22, panelTop + 99, 232);
		addDistanceSlider(panelLeft + 22, config.highlightRadius, blocks -> config.highlightRadius = blocks);
		addInput("redOutlineNames", "Ники через запятую", config.redOutlineNames,
				panelLeft + 22, panelTop + 190, 232);
		addInput("greenOutlineNames", "Ники через запятую", config.greenOutlineNames,
				panelLeft + 22, panelTop + 226, 232);
		addInput("purpleOutlineNames", "Ники через запятую", config.purpleOutlineNames,
				panelLeft + 22, panelTop + 262, 232);
		addKeybind("Клавиша вкл/выкл", NearbyWorkshopClient.playerHighlightKey(), panelLeft + 22, panelTop + 290, 232);
		addToggle("Список игроков рядом", config.nearbyEnabled, value -> {
			config.nearbyEnabled = value;
			NearbyWorkshopClient.saveConfig();
		}, panelLeft + 265, panelTop + 99, 232);
		addRenderableWidget(Button.builder(Component.literal("Настроить HUD игроков"), b -> {
			commitFields();
			NearbyWorkshopClient.saveConfig();
			minecraft.gui.setScreen(new NearbyHudSettingsScreen(this));
		}).bounds(panelLeft + 265, panelTop + 128, 232, 22).build());
		addRenderableWidget(new SettingSlider(panelLeft + 265, panelTop + 173, 232, 127,
				(int) Math.round(config.nearbyRadius) - 1, step -> (step + 1) + " блоков",
				step -> config.nearbyRadius = step + 1));
		addInput("nearbyMaxEntries", "", String.valueOf(config.nearbyMaxEntries), panelLeft + 265,
				panelTop + 218, 232);
		addToggle("Показывать расстояние", config.nearbyShowDistance, value -> config.nearbyShowDistance = value,
				panelLeft + 265, panelTop + 252, 232);
		addKeybind("Клавиша вкл/выкл", NearbyWorkshopClient.nearbyListKey(), panelLeft + 265, panelTop + 278, 232);

	}

	private void buildMobsTab() {
		var config = NearbyWorkshopClient.CONFIG;
		addToggle("Подсветка мобов", config.mobHighlightEnabled, value -> config.mobHighlightEnabled = value,
				panelLeft + 22, panelTop + 104, 232);
		int controlsY = panelTop + ("mobFilter".equals(openDropdown) ? 251 : 174);
		var filters = MobHighlights.Filter.values();
		addDropdownButton("mobFilter", "Мобы", config.mobHighlightFilter.label,
				Arrays.stream(filters).map(filter -> filter.label).toArray(String[]::new),
				panelLeft + 22, panelTop + 140, 232, index -> config.mobHighlightFilter = filters[index]);
		addRenderableWidget(new SettingSlider(panelLeft + 22, controlsY, 232, 127,
				(int) Math.round(config.mobHighlightRadius) - 1, step -> "Дистанция: " + (step + 1) + " бл.",
				step -> config.mobHighlightRadius = step + 1));
		addKeybind("Клавиша вкл/выкл", NearbyWorkshopClient.mobHighlightKey(), panelLeft + 22, controlsY + 29, 232);
		addToggle("Список мобов", config.mobCounterEnabled, value -> config.mobCounterEnabled = value,
				panelLeft + 265, panelTop + 104, 232);
		addRenderableWidget(Button.builder(Component.literal("Настроить HUD счётчика мобов"), button ->
				minecraft.gui.setScreen(new MobCounterSettingsScreen(this)))
				.bounds(panelLeft + 265, panelTop + 133, 232, 22).build());
		buildOpenDropdown();
	}

	private void addDistanceSlider(int x, double initial, IntConsumer setter) {
		addRenderableWidget(new SettingSlider(x, panelTop + 145, 232, 127,
				(int) Math.round(initial) - 1, step -> (step + 1) + " блоков", step -> setter.accept(step + 1)));
	}

	private final class SettingSlider extends AbstractSliderButton {
		private final int maxStep;
		private final java.util.function.IntFunction<String> label;
		private final IntConsumer setter;

		SettingSlider(int x, int y, int width, int maxStep, int initial,
				java.util.function.IntFunction<String> label, IntConsumer setter) {
			super(x, y, width, 22, Component.empty(), Math.clamp(initial, 0, maxStep) / (double) maxStep);
			this.maxStep = maxStep;
			this.label = label;
			this.setter = setter;
			updateMessage();
		}

		@Override
		protected void updateMessage() {
			setMessage(Component.literal(label.apply((int) Math.round(value * maxStep))));
		}

		@Override
		protected void applyValue() {
			setter.accept((int) Math.round(value * maxStep));
			NearbyWorkshopClient.saveConfig();
		}
	}

	private void buildHealthTab() {
		var config = NearbyWorkshopClient.CONFIG;
		var modes = NearbyWorkshopClient.Config.HealthMode.values();
		String[] labels = Arrays.stream(modes).map(mode -> mode.label).toArray(String[]::new);
		addDropdownButton("playerHealth", "Игроки", config.playerHealthMode.label, labels,
				panelLeft + 22, panelTop + 104, 232, step -> config.playerHealthMode = modes[step]);
		addDropdownButton("mobHealth", "Мобы", config.mobHealthMode.label, labels,
				panelLeft + 263, panelTop + 104, 232, step -> config.mobHealthMode = modes[step]);
		addRenderableWidget(Button.builder(Component.empty(), button -> {
			config.healthHudHearts = !config.healthHudHearts;
			NearbyWorkshopClient.saveConfig();
			rebuildTab();
		}).tooltip(Tooltip.create(Component.literal(config.healthHudHearts ? "Вид: сердца" : "Вид: полоса")))
				.bounds(panelLeft + 104, panelTop + 225, 312, 22).build());
		addRenderableWidget(Button.builder(Component.literal("При наведении"), button ->
				minecraft.gui.setScreen(new NearbyHudSettingsScreen(this, true)))
				.bounds(panelLeft + 104, panelTop + 253, 312, 22).build());
		addRenderableWidget(Button.builder(Component.literal("Постоянное отображение"), button ->
				minecraft.gui.setScreen(new OverheadHealthSettingsScreen(this)))
				.bounds(panelLeft + 104, panelTop + 281, 312, 22).build());
		buildOpenDropdown();
	}

	private void buildMessagesTab() {
		var config = NearbyWorkshopClient.CONFIG;
		addInput("pmMessage", "Текст личного сообщения", config.pmMessage, panelLeft + 22, panelTop + 116, 473);
		int delayStep = 0;
		for (int i = 1; i < NearbyWorkshopClient.SEND_DELAYS.length; i++) {
			if (Math.abs(NearbyWorkshopClient.SEND_DELAYS[i] - config.pmDelayMs) < Math.abs(NearbyWorkshopClient.SEND_DELAYS[delayStep] - config.pmDelayMs)) delayStep = i;
		}
		var delay = new SettingSlider(panelLeft + 22, panelTop + 170, 232, NearbyWorkshopClient.SEND_DELAYS.length - 1, delayStep,
				step -> java.math.BigDecimal.valueOf(NearbyWorkshopClient.SEND_DELAYS[step], 3).stripTrailingZeros().toPlainString() + " с",
				step -> config.pmDelayMs = NearbyWorkshopClient.SEND_DELAYS[step]);
		delay.setTooltip(Tooltip.create(Component.literal("Пауза между командами рассылки измеряется в секундах.")));
		addRenderableWidget(delay);
		addKeybind("Клавиша вкл/выкл", NearbyWorkshopClient.broadcastKey(), panelLeft + 263, panelTop + 170, 232);
		addInput("pmExcludedPlayers", "Исключения: ники через запятую", config.pmExcludedPlayers,
				panelLeft + 22, panelTop + 226, 473);
		addRenderableWidget(Button.builder(Component.literal("Начать для игроков онлайн"), button -> startPrivateMessageBroadcast())
				.bounds(panelLeft + 22, panelTop + 266, 230, 24).build());
		addRenderableWidget(Button.builder(Component.literal("Остановить"), button -> PrivateMessageBroadcast.stop(Minecraft.getInstance()))
				.bounds(panelLeft + 263, panelTop + 266, 232, 24).build());
	}

	private static final int BLOCK_RANGE_Y = 157;
	private static final int MAX_HIGHLIGHT_RANGE = NearbyWorkshopClient.Config.MAX_BLOCK_HIGHLIGHT_RANGE;
	private static final net.minecraft.resources.Identifier CHEST_TEXTURE =
			net.minecraft.resources.Identifier.withDefaultNamespace("textures/entity/chest/normal.png");
	private static final net.minecraft.resources.Identifier REDSTONE_TEXTURE =
			net.minecraft.resources.Identifier.withDefaultNamespace("textures/item/redstone.png");
	private static final int ORE_RANGE_Y = 110 + (OreHighlightMode.families().size() + 1) * 20;
	private static final int BLOCK_KEY_Y = ORE_RANGE_Y + 32;

	private void buildBlockHighlightsTab() {
		var config = NearbyWorkshopClient.CONFIG;
		addBlockCheckbox("Сундуки", 106, config.highlightChests, value -> config.highlightChests = value);
		addBlockCheckbox("Редстоун и механизмы", 130, config.highlightRedstone, value -> config.highlightRedstone = value);
		int step = config.blockHighlightRange == 0 ? MAX_HIGHLIGHT_RANGE : config.blockHighlightRange - 1;
		addRenderableWidget(new HighlightRangeSlider(panelLeft + 22, panelTop + BLOCK_RANGE_Y, 232, 22, step, false));
		int oreStep = config.oreHighlightRange == 0 ? MAX_HIGHLIGHT_RANGE : config.oreHighlightRange - 1;
		addRenderableWidget(new HighlightRangeSlider(panelLeft + 265, panelTop + ORE_RANGE_Y, 232, 22, oreStep, true));
		addKeybind("Клавиша вкл/выкл", NearbyWorkshopClient.blockHighlightKey(),
				panelLeft + (PANEL_WIDTH - 232) / 2, panelTop + BLOCK_KEY_Y, 232);
		buildOreCheckboxes();
	}

	private void addBlockCheckbox(String label, int y, boolean checked, java.util.function.Consumer<Boolean> change) {
		addRenderableWidget(Checkbox.builder(Component.literal(label), font)
				.pos(panelLeft + 45, panelTop + y).maxWidth(208).selected(checked)
				.onValueChange((checkbox, value) -> {
					change.accept(value);
					saveHighlightSelection();
				}).build());
	}

	private void saveHighlightSelection() {
		var config = NearbyWorkshopClient.CONFIG;
		config.blockHighlightEnabled = config.highlightChests || config.highlightRedstone || !config.oreHighlightModes.isEmpty();
		BlockHighlights.invalidate();
		NearbyWorkshopClient.saveConfig();
	}


	private void buildOreCheckboxes() {
		var config = NearbyWorkshopClient.CONFIG;
		var selected = config.oreHighlightModes;
		var families = OreHighlightMode.families();
		addOreCheckbox(OreHighlightMode.ALL, 0, selected.containsAll(families), checked -> {
			selected.clear();
			if (checked) selected.addAll(families);
		});
		for (int i = 0; i < families.size(); i++) {
			var ore = families.get(i);
			addOreCheckbox(ore, i + 1, selected.contains(ore), checked -> {
				if (checked) selected.add(ore);
				else selected.remove(ore);
			});
		}
	}

	private void drawOreIcon(GuiGraphicsExtractor graphics, OreHighlightMode ore, int row) {
		int x = panelLeft + 266;
		int y = panelTop + 106 + row * 20;
		graphics.blit(ore.icon(), x, y, x + 16, y + 16, 0, 1, 0, 1);
	}

	private void addOreCheckbox(OreHighlightMode ore, int row, boolean checked, java.util.function.Consumer<Boolean> change) {
		var label = Component.literal(ore.label()).withColor(ore.color() & 0xFFFFFF);
		addRenderableWidget(Checkbox.builder(label, font).pos(panelLeft + 288, panelTop + 106 + row * 20)
				.maxWidth(208).selected(checked).onValueChange((checkbox, value) -> {
					change.accept(value);
					saveHighlightSelection();
					rebuildTab();
				}).build());
	}

	private void addKeybind(String label, KeyMapping mapping, int x, int y, int width) {
		if (mapping == null) return;
		String key = capturingKey == mapping ? "Нажми клавишу…" : mapping.getTranslatedKeyMessage().getString();
		addRenderableWidget(Button.builder(Component.literal(label + ": " + key), button -> {
			capturingKey = mapping;
			button.setMessage(Component.literal(label + ": Нажми клавишу…"));
		}).tooltip(Tooltip.create(Component.literal("Нажми кнопку и затем клавишу. Escape снимает назначение.")))
				.bounds(x, y, width, 22).build());
	}

	private void addDropdownButton(String id, String label, String selected, String[] options,
			int x, int y, int buttonWidth, IntConsumer onSelect) {
		DropdownSpec spec = new DropdownSpec(id, options, x, y, buttonWidth, onSelect);
		dropdowns.put(id, spec);
		addRenderableWidget(Button.builder(Component.literal(label + ": " + selected + "  ▾"), button -> {
			commitFields();
			NearbyWorkshopClient.saveConfig();
			openDropdown = id.equals(openDropdown) ? null : id;
			rebuildTab();
		}).tooltip(Tooltip.create(Component.literal("Нажми, чтобы раскрыть список вариантов.")))
				.bounds(x, y, buttonWidth, 24).build());
	}

	private void buildOpenDropdown() {
		DropdownSpec spec = dropdowns.get(openDropdown);
		if (spec == null) return;
		for (int i = 0; i < spec.options().length; i++) {
			int selectedIndex = i;
			Component option = Component.literal(spec.options()[i]);
			if ("mobFilter".equals(spec.id())) {
				option = Component.literal(spec.options()[i]).withColor(MobHighlights.Filter.values()[i].color);
			}
			addRenderableWidget(Button.builder(option, button -> {
				commitFields();
				spec.onSelect().accept(selectedIndex);
				NearbyWorkshopClient.CONFIG.sanitize();
				BlockHighlights.invalidate();
				NearbyWorkshopClient.saveConfig();
				openDropdown = null;
				rebuildTab();
			}).bounds(spec.x(), spec.y() + 24 + i * 20, spec.width(), 20).build());
		}
	}

	private final class HighlightRangeSlider extends AbstractSliderButton {
		private final boolean ores;
		private HighlightRangeSlider(int x, int y, int width, int height, int step, boolean ores) {
			super(x, y, width, height, Component.empty(), step / (double) MAX_HIGHLIGHT_RANGE);
			this.ores = ores;
			setTooltip(Tooltip.create(Component.literal("Дальность от 1 до 64 блоков. «Бесконечно» ограничено загруженными чанками в радиусе прорисовки.")));
			updateMessage();
		}

		@Override protected void updateMessage() {
			int step = (int) Math.round(value * MAX_HIGHLIGHT_RANGE);
			String label = ores ? "Дистанция подсветки руд: " : "Дистанция подсветки блоков: ";
			setMessage(Component.literal(label + (step == MAX_HIGHLIGHT_RANGE ? "Бесконечно" : (step + 1) + " блок.")));
		}

		@Override protected void applyValue() {
			int step = (int) Math.round(value * MAX_HIGHLIGHT_RANGE);
			int range = step == MAX_HIGHLIGHT_RANGE ? 0 : step + 1;
			if (ores) NearbyWorkshopClient.CONFIG.oreHighlightRange = range;
			else NearbyWorkshopClient.CONFIG.blockHighlightRange = range;
			BlockHighlights.invalidate();
			NearbyWorkshopClient.saveConfig();
		}
	}

	private void startPrivateMessageBroadcast() {
		commitFields();
		NearbyWorkshopClient.saveConfig();
		PrivateMessageBroadcast.start(Minecraft.getInstance());
	}

	private void addTabButton(String label, Tab target, int x, int y, int buttonWidth) {
		boolean selected = tab == target;
		String text = (selected ? "◆ " : "") + label;
		Component styled = Component.literal(text).withStyle(Style.EMPTY.withBold(true)
				.withColor(selected ? 0xFFE9BDFF : 0xFFE7D5E6));
		addRenderableWidget(Button.builder(styled, button -> switchTab(target))
				.bounds(x, y, buttonWidth, 26).build());
	}

	private void addToggle(String label, boolean value, java.util.function.Consumer<Boolean> setter,
			int x, int y, int buttonWidth) {
		String status = value ? "ВКЛ" : "ВЫКЛ";
		addRenderableWidget(Button.builder(Component.literal(label + ": " + status), button -> {
			commitFields();
			setter.accept(!value);
			NearbyWorkshopClient.saveConfig();
			rebuildTab();
		}).tooltip(Tooltip.create(Component.literal(toggleHelp(label))))
				.bounds(x, y, buttonWidth, 22).build());
	}

	private String toggleHelp(String label) {
		if (label.equals("Подсветка мобов")) return "Обводит силуэты загруженных мобов выбранной категории в пределах заданной дистанции, в том числе за стенами. Зелёный — дружелюбные, красный — враждебные, фиолетовый — боссы.";
		if (label.equals("Здоровье игроков") || label.equals("Здоровье мобов")) {
			return "При наведении прицела на цель показывает её текущее и максимальное здоровье в очках HP.";
		}
		if (label.contains("ТАБЛИЧЕК")) return "Включает автоматическое заполнение доступных незапечатанных табличек. Нужна хотя бы одна пустая рука; клавишу можно переназначить ниже.";
		if (label.contains("Подсветка сквозь стены")) return "Подсвечивает загруженных игроков в установленном радиусе, даже если между вами есть блоки.";
		if (label.contains("игроков")) return "Подсветка показывает только игроков, уже известных клиенту, в заданном радиусе.";
		if (label.contains("список")) return "Показывает имена загруженных игроков в выбранном радиусе.";
		if (label.contains("блоков")) return "Подсвечивает выбранную группу блоков; контур рисуется сквозь стены.";
		return "Включить или выключить этот модуль.";
	}

	private void addInput(String key, String label, String value, int x, int y, int inputWidth) {
		EditBox input = new EditBox(font, x, y, inputWidth, 21, Component.literal(label));
		input.setMaxLength(switch (key) {
			case "pmMessage" -> 220;
			case "pmExcludedPlayers", "redOutlineNames", "greenOutlineNames", "purpleOutlineNames" -> 1024;
			default -> 120;
		});
		input.setValue(value);
		input.setHint(Component.literal(label));
		input.setTooltip(Tooltip.create(Component.literal(inputHelp(key))));
		fields.put(key, input);
		addRenderableWidget(input);
	}

	private String inputHelp(String key) {
		return switch (key) {
			case "presetName" -> "Стрелки переключают наборы. Введи новое имя и нажми + для создания или ✓ для переименования.";
			case "signDelayMinMs", "signDelayMaxMs" -> "Случайная пауза между попытками взаимодействия с табличками.";
			case "signOpenTimeoutTicks" -> "Сколько ждать разрешения сервера на изменение таблички перед повторной попыткой.";



			case "pmMessage" -> "Один текст, который отправляется каждому выбранному получателю.";
			case "redOutlineNames", "greenOutlineNames", "purpleOutlineNames" ->
					"Вводи ники через запятую. Регистр не важен. При совпадении действует верхний список.";
			case "pmExcludedPlayers" -> "Ники, которым не отправлять сообщение. Разделяй запятыми, пробелами или точкой с запятой; регистр не важен.";
			default -> "Настройка: " + key;
		};
	}

	private void switchTab(Tab target) {
		commitFields();
		NearbyWorkshopClient.saveConfig();
		openDropdown = null;
		tab = target;
		scrollY = 0;
		rebuildTab();
	}

	private static int panelOrigin(int viewport, int panelSize) {
		return Math.max(EDGE, (viewport - panelSize) / 2);
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double horizontal, double vertical) {
		if (previewEntry >= 0) return super.mouseScrolled(mouseX, mouseY, horizontal, vertical);
		int nextY = scrollBy(scrollY, vertical, verticalLimit());
		int dy = scrollY - nextY;
		if (dy == 0) return false;
		scrollY = nextY;
		panelTop += dy;
		for (int i = headerCount; i < bodyEnd; i++) {
			if (children().get(i) instanceof AbstractWidget widget) widget.setY(widget.getY() + dy);
		}
		return true;
	}

	private static int scrollBy(int current, double wheel, int limit) {
		return Math.clamp(current - (int) Math.round(wheel * 28), 0, limit);
	}

	private void rebuildTab() {
		clearWidgets();
		init();
	}

	private void addTemplate() {
		commitFields();
		String[][] old = NearbyWorkshopClient.CONFIG.signTemplates;
		String[][] updated = Arrays.copyOf(old, old.length + 1);
		updated[old.length] = new String[]{"", "", "", ""};
		NearbyWorkshopClient.CONFIG.signTemplates = updated;
		templatePage = (updated.length - 1) / 7;
		NearbyWorkshopClient.saveConfig();
		rebuildTab();
	}

	private void removeTemplate() {
		commitFields();
		String[][] old = NearbyWorkshopClient.CONFIG.signTemplates;
		int pageStart = templatePage * 7;
		int pageEnd = Math.min(old.length, pageStart + 7);
		if (pageEnd <= pageStart) return;
		int removeIndex = pageEnd - 1;
		String[][] updated = new String[old.length - 1][];
		System.arraycopy(old, 0, updated, 0, removeIndex);
		if (removeIndex < updated.length) System.arraycopy(old, removeIndex + 1, updated, removeIndex, updated.length - removeIndex);
		if (updated.length == 0) updated = new String[][]{{"", "", "", ""}};
		NearbyWorkshopClient.CONFIG.signTemplates = updated;
		templatePage = Math.min(templatePage, signPageCount(updated.length) - 1);
		NearbyWorkshopClient.saveConfig();
		rebuildTab();
	}

	private void changeTemplatePage(int delta) {
		commitFields();
		NearbyWorkshopClient.saveConfig();
		templatePage = Math.floorMod(templatePage + delta, signPageCount(NearbyWorkshopClient.CONFIG.signTemplates.length));
		rebuildTab();
	}

	private void commitFields() {
		var config = NearbyWorkshopClient.CONFIG;
		for (var entry : fields.entrySet()) {
			String value = entry.getValue().getValue().trim();
			try {
				switch (entry.getKey()) {
					case "pmMessage" -> config.pmMessage = entry.getValue().getValue();
					case "pmExcludedPlayers" -> config.pmExcludedPlayers = entry.getValue().getValue();
					case "redOutlineNames" -> config.redOutlineNames = entry.getValue().getValue();
					case "greenOutlineNames" -> config.greenOutlineNames = entry.getValue().getValue();
					case "purpleOutlineNames" -> config.purpleOutlineNames = entry.getValue().getValue();

					case "signDelayMinMs" -> config.signDelayMinMs = Integer.parseInt(value);
					case "signDelayMaxMs" -> config.signDelayMaxMs = Integer.parseInt(value);
					case "signOpenTimeoutTicks" -> config.signOpenTimeoutTicks = Integer.parseInt(value);
					case "signMaxAttempts" -> config.signMaxAttempts = Integer.parseInt(value);


					case "nearbyMaxEntries" -> config.nearbyMaxEntries = Integer.parseInt(value);
				}
			} catch (NumberFormatException ignored) { }
		}
		if (tab == Tab.SIGNS) commitSignEntries();
		config.sanitize();
	}

	private void commitSignEntries() {
		if (!fields.containsKey(signField(0, 0))) return;
		String[][] old = NearbyWorkshopClient.CONFIG.signTemplates;
		List<String[]> entries = new ArrayList<>(Arrays.asList(old));
		for (int row = 0; row < 7; row++) {
			int index = templatePage * 7 + row;
			String[] lines = readSignRow(row);
			if (index >= entries.size() && Arrays.stream(lines).allMatch(String::isEmpty)) continue;
			while (entries.size() <= index) entries.add(new String[]{"", "", "", ""});
			entries.set(index, lines);
		}
		NearbyWorkshopClient.CONFIG.signTemplates = entries.toArray(String[][]::new);
	}

	@Override
	public void onClose() {
		commitFields();
		NearbyWorkshopClient.saveConfig();
		minecraft.gui.setScreen(parent);
	}

	@Override
	public boolean keyPressed(KeyEvent event) {
		if (previewEntry >= 0 && event.key() == 256) {
			closePreview();
			return true;
		}
		if (capturingKey != null) {
			commitFields();
			NearbyWorkshopClient.saveConfig();
			if (event.key() == 256) capturingKey.setKey(com.mojang.blaze3d.platform.InputConstants.UNKNOWN);
			else capturingKey.setKey(com.mojang.blaze3d.platform.InputConstants.getKey(event));
			capturingKey = null;
			NearbyWorkshopClient.persistKeyMappings();
			rebuildTab();
			return true;
		}
		return super.keyPressed(event);
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	@Override
	public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
		super.extractBackground(graphics, mouseX, mouseY, delta);
		graphics.pose().pushMatrix();
		graphics.pose().scale(menuScale);
		int left = frameLeft();
		int right = frameRight();
		graphics.fill(left, frameTop, right, frameBottom, 0xFF665078);
		graphics.fill(left + 1, frameTop + 2, right - 1, frameBottom - 1, 0xFB151020);
		graphics.fill(left + 1, frameTop + 2, right - 1, bodyTop(), 0xFF251735);
		graphics.fill(left + 1, bodyBottom(), right - 1, frameBottom - 1, 0xFF191222);
		graphics.fill(left, frameTop, right, frameTop + 2, 0xFFB28AE7);
		graphics.fill(left + 1, bodyBottom(), right - 1, bodyBottom() + 1, 0xFF382B46);
		int sparkleX = panelLeft + 233;
		int pulse = 92 + (int) (32 * Math.sin(System.nanoTime() / 600_000_000.0));
		graphics.enableScissor(left + 1, frameTop + 2, right - 1, bodyTop());
		graphics.fill(sparkleX, frameTop + 12, sparkleX + 3, frameTop + 19, (pulse << 24) | 0xC6A2F3);
		graphics.fill(sparkleX - 2, frameTop + 14, sparkleX + 5, frameTop + 17, (pulse << 24) | 0xC6A2F3);
		graphics.fill(sparkleX + 1, frameTop + 15, sparkleX + 2, frameTop + 16, 0xFFE9D2FF);
		graphics.fill(sparkleX + 15, frameTop + 29, sparkleX + 17, frameTop + 31, 0xFF80649B);
		graphics.disableScissor();
		graphics.text(font, "ALCOVANISH", left + 22, frameTop + 14, 0xFFF0D6FA);
		graphics.text(font, "Настройки модулей", left + 22, frameTop + 29, 0xFFF7ECF5);
		graphics.enableScissor(left + 1, bodyTop(), right - 1, bodyBottom());

		if (tab == Tab.SIGNS) {
			graphics.text(font, "Задержка от, мс", panelLeft + 22, panelTop + 368, 0xFFC0AFC0);
			graphics.text(font, "Задержка до, мс", panelLeft + 263, panelTop + 368, 0xFFC0AFC0);
			graphics.text(font, "Таймаут, тики", panelLeft + 22, panelTop + 394, 0xFFC0AFC0);
			graphics.text(font, "Попыток на знак", panelLeft + 263, panelTop + 394, 0xFFC0AFC0);
			graphics.text(font, "Набор шаблонов", panelLeft + 22, panelTop + 146, 0xFFC0AFC0);
			for (int i = 0; i < 7; i++) {
				boolean invalid = fields.containsKey(signField(i, 0))
						&& Arrays.stream(readSignRow(i)).anyMatch(line -> signWidth(line) > 90);
				int color = invalid ? 0xFFFF7777 : 0xFFC0AFC0;
				graphics.text(font, "№ " + (templatePage * 7 + i + 1), panelLeft + 22,
						panelTop + 174 + i * 23, color);
			}
			graphics.text(font, "Страница " + (templatePage + 1) + " / " + signPageCount(NearbyWorkshopClient.CONFIG.signTemplates.length),
					panelLeft + 325, panelTop + 338, 0xFFFF69C6);
			graphics.text(font, signTransferStatus, panelLeft + 263, panelTop + 141, 0xFFE9BDFF);
		} else if (tab == Tab.PLAYERS) {
			graphics.fill(panelLeft + 259, panelTop + 80, panelLeft + 260, panelTop + 359, 0x506D5584);
			graphics.text(font, "ПОДСВЕТКА ИГРОКОВ", panelLeft + 22, panelTop + 83, 0xFFFF69C6);
			graphics.text(font, "Дистанция подсветки", panelLeft + 22, panelTop + 132, 0xFFC0AFC0);
			graphics.text(font, "Красная подсветка · ники", panelLeft + 22, panelTop + 176, 0xFFFF7777);
			graphics.text(font, "Зелёная подсветка · ники", panelLeft + 22, panelTop + 212, NearbyWorkshopClient.Config.GREEN_OUTLINE);
			graphics.text(font, "Фиолетовая подсветка · ники", panelLeft + 22, panelTop + 248, NearbyWorkshopClient.Config.PURPLE_OUTLINE);
			graphics.text(font, "СПИСОК ИГРОКОВ", panelLeft + 265, panelTop + 83, 0xFFFF69C6);
			graphics.text(font, "Дистанция списка", panelLeft + 265, panelTop + 160, 0xFFC0AFC0);
			graphics.text(font, "Лимит имён", panelLeft + 265, panelTop + 205, 0xFFC0AFC0);
		} else if (tab == Tab.BLOCKS) {
			graphics.text(font, "БЛОКИ", panelLeft + 22, panelTop + 83, 0xFFFF69C6);
			graphics.text(font, "РУДЫ", panelLeft + 265, panelTop + 83, 0xFFFF69C6);
			graphics.fill(panelLeft + 259, panelTop + 80, panelLeft + 260, panelTop + ORE_RANGE_Y + 23, 0x506D5584);

			int x = panelLeft + 23;
			int y = panelTop + 107;
			graphics.blit(CHEST_TEXTURE, x, y, x + 14, y + 5, 14 / 64f, 28 / 64f, 14 / 64f, 19 / 64f);
			graphics.blit(CHEST_TEXTURE, x, y + 5, x + 14, y + 15, 14 / 64f, 28 / 64f, 33 / 64f, 43 / 64f);
			graphics.fill(x + 6, y + 4, x + 8, y + 8, 0xFFCCC1A2);
			graphics.blit(REDSTONE_TEXTURE, x - 1, y + 22, x + 15, y + 38, 0, 1, 0, 1);
			var ores = OreHighlightMode.families();
			for (int row = 0; row < ores.size(); row++) drawOreIcon(graphics, ores.get(row), row + 1);
		} else if (tab == Tab.MOBS) {
			graphics.text(font, "ПОДСВЕТКА МОБОВ", panelLeft + 22, panelTop + 83, 0xFFE9BDFF);
			graphics.text(font, "СПИСОК МОБОВ", panelLeft + 265, panelTop + 83, 0xFFE9BDFF);
			graphics.fill(panelLeft + 259, panelTop + 80, panelLeft + 260,
					panelTop + ("mobFilter".equals(openDropdown) ? 304 : 227), 0x506D5584);



		} else if (tab == Tab.HEALTH) {
			graphics.text(font, "ЗДОРОВЬЕ ЦЕЛИ", panelLeft + 22, panelTop + 83, 0xFFE9BDFF);
			graphics.text(font, "Настройки", panelLeft + (PANEL_WIDTH - font.width("Настройки")) / 2, panelTop + 209, 0xFFE9BDFF);
		} else {
			graphics.text(font, "Текст для личных сообщений", panelLeft + 22, panelTop + 103, 0xFFC0AFC0);
			graphics.text(font, "Пауза между командами, с", panelLeft + 22, panelTop + 157, 0xFFC0AFC0);
			graphics.text(font, "Исключения: ники через запятую", panelLeft + 22, panelTop + 213, 0xFFC0AFC0);
			String state = PrivateMessageBroadcast.isRunning()
					? "Отправлено " + PrivateMessageBroadcast.sentCount() + " / " + PrivateMessageBroadcast.totalCount()
					: "Готово к отправке игрокам из списка сервера";
			graphics.text(font, state, panelLeft + 22, panelTop + 306, 0xFFB7A4B8);
			graphics.text(font, "Рассылка идёт по очереди; её можно остановить в любой момент.",
					panelLeft + 22, panelTop + 329, 0xFFC0AFC0);
		}
		graphics.disableScissor();
		if (previewEntry >= 0) {
			renderSignPreview(graphics);
		} else if (verticalLimit() > 0) {
			int track = bodyBottom() - bodyTop();
			int thumb = Math.max(1, track * track / (track + verticalLimit()));
			int y = bodyTop() + (track - thumb) * scrollY / verticalLimit();
			graphics.fill(right - 4, bodyTop(), right - 1, bodyBottom(), 0xFF493359);
			graphics.fill(right - 4, y, right - 1, y + thumb, 0xFFB28AE7);
		}
		graphics.pose().popMatrix();
	}

	private int frameLeft() { return panelOrigin(menuWidth, PANEL_WIDTH); }
	private int frameRight() { return Math.min(menuWidth - EDGE, frameLeft() + PANEL_WIDTH); }
	private int bodyTop() { return Math.min(frameTop + 79, bodyBottom() - 1); }
	private int bodyBottom() { return frameBottom - 36; }

	private int verticalLimit() {
		int contentBottom = switch (tab) {
			case SIGNS -> 415;
			case BLOCKS -> BLOCK_KEY_Y + 27;
			case PLAYERS -> 365;
			case MOBS -> "mobFilter".equals(openDropdown) ? 307 : 230;
			case MESSAGES, HEALTH -> 345;
		};
		return Math.max(0, frameTop + contentBottom - bodyBottom());
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
		graphics.pose().pushMatrix();
		graphics.pose().scale(menuScale);
		try {
		if (previewEntry >= 0) {
			super.extractRenderState(graphics, (int) (mouseX / menuScale), (int) (mouseY / menuScale), delta);
			return;
		}
		for (int i = 0; i < children().size(); i++) {
			if (!(children().get(i) instanceof AbstractWidget widget)) continue;
			int top = i < headerCount ? frameTop : i < bodyEnd ? bodyTop() : bodyBottom();
			int bottom = i < headerCount ? bodyTop() : i < bodyEnd ? bodyBottom() : frameBottom;
			graphics.enableScissor(frameLeft() + 1, top, frameRight() - 1, bottom);
			widget.extractRenderState(graphics, (int) (mouseX / menuScale), (int) (mouseY / menuScale), delta);
			if (tab == Tab.SIGNS && widget instanceof Button && widget.getX() == panelLeft + SIGN_PREVIEW_X) {
				int x = widget.getX() + 2;
				int y = widget.getY() + 3;
				graphics.blit(OAK_SIGN_TEXTURE, x, y, x + 15, y + 15, 0, 1, 0, 1);
			}
			if (tab == Tab.HEALTH && widget.getX() == panelLeft + 104 && widget.getY() == panelTop + 225) {
				drawHealthStylePreview(graphics);
			}
			graphics.disableScissor();
		}
		} finally {
			graphics.pose().popMatrix();
		}
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		event = menuMouse(event);
		if (previewEntry >= 0) return super.mouseClicked(event, doubleClick);
		if (event.x() < frameLeft() || event.x() >= frameRight()) return false;
		int start = event.y() < bodyTop() ? 0 : event.y() < bodyBottom() ? headerCount : bodyEnd;
		int end = event.y() < bodyTop() ? headerCount : event.y() < bodyBottom() ? bodyEnd : children().size();
		for (int i = start; i < end; i++) {
			var child = children().get(i);
			if (child.mouseClicked(event, doubleClick)) {
				if (children().contains(child)) {
					setFocused(child);
					if (event.button() == 0) setDragging(true);
				}
				return true;
			}
		}
		return false;
	}

	private void drawHealthStylePreview(GuiGraphicsExtractor graphics) {
		boolean hearts = NearbyWorkshopClient.CONFIG.healthHudHearts;
		var style = OverheadHealthState.Display.create(12, 20, true, hearts);
		int iconWidth = hearts ? font.width(style.indicator()) : OverheadHealthState.Display.BAR_WIDTH;
		int textWidth = font.width("Вид: ");
		int left = panelLeft + (PANEL_WIDTH - textWidth - iconWidth) / 2;
		int y = panelTop + 231;
		graphics.text(font, "Вид: ", left, y + 1, 0xFFFFFFFF);
		int x = left + textWidth;
		if (hearts) {
			graphics.text(font, style.indicator(), x, y + 1, 0xFFFFFFFF);
		} else {
			graphics.fill(x, y + 3, x + iconWidth, y + 6, OverheadHealthState.Display.BAR_TRACK);
			graphics.fill(x, y + 3, x + Math.round(iconWidth * style.fraction()), y + 6, OverheadHealthState.Display.BAR_FILL);
		}
	}

	private MouseButtonEvent menuMouse(MouseButtonEvent event) {
		return new MouseButtonEvent(event.x() / menuScale, event.y() / menuScale, event.buttonInfo());
	}

	@Override
	public boolean mouseDragged(MouseButtonEvent event, double dx, double dy) {
		return super.mouseDragged(menuMouse(event), dx / menuScale, dy / menuScale);
	}

	@Override
	public boolean mouseReleased(MouseButtonEvent event) {
		return super.mouseReleased(menuMouse(event));
	}

	private void renderSignPreview(GuiGraphicsExtractor graphics) {
		graphics.fill(0, 0, menuWidth, menuHeight, 0xA8000000);
		graphics.fill(previewLeft, previewTop, previewLeft + 300, previewTop + 184, 0xF21A1020);
		graphics.fill(previewLeft, previewTop, previewLeft + 300, previewTop + 2, 0xFFFF69C6);
		graphics.fill(previewLeft + 1, previewTop + 1, previewLeft + 299, previewTop + 183, 0x501A1020);
		graphics.text(font, "ТАБЛИЧКА " + (previewEntry + 1), previewLeft + 15, previewTop + 15, 0xFFFF85D0);

		int boardLeft = previewLeft + 53;
		int boardTop = previewTop + 43;
		graphics.fill(boardLeft - 2, boardTop - 2, boardLeft + 196, boardTop + 81, 0xFF352018);
		graphics.fill(boardLeft, boardTop, boardLeft + 192, boardTop + 77, 0xFF9A653F);
		graphics.fill(boardLeft + 2, boardTop + 2, boardLeft + 190, boardTop + 75, 0xFF805132);
		graphics.fill(boardLeft + 2, boardTop + 2, boardLeft + 190, boardTop + 4, 0xFFB27A50);
		graphics.fill(previewLeft + 143, boardTop + 77, previewLeft + 157, previewTop + 153, 0xFF68412B);

		String[] lines = previewTemplate;
		for (int line = 0; line < 4; line++) {
			String raw = line < lines.length ? lines[line] : "";
			String clean = net.minecraft.ChatFormatting.stripFormatting(raw);
			if (clean == null) clean = "";
			int lineWidth = font.width(clean);
			int color = lineWidth > 90 ? 0xFFFF7777 : 0xFFF4E9D8;
			graphics.text(font, clean, boardLeft + (192 - lineWidth) / 2, boardTop + 12 + line * 14, color);
		}
		if (lines.length > 4 || Arrays.stream(lines).anyMatch(line -> {
			String clean = net.minecraft.ChatFormatting.stripFormatting(line);
			return clean != null && font.width(clean) > 90;
		})) {
			graphics.text(font, "Некоторые строки шире таблички", previewLeft + 15, previewTop + 164, 0xFFFF7777);
		} else {
			graphics.text(font, "Закрыть: кнопка ✕ или Escape", previewLeft + 15, previewTop + 164, 0xFFB7A4B8);
		}
	}
}


