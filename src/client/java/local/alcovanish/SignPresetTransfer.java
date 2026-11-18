package local.alcovanish;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;

final class SignPresetTransfer {
	static final String FILE_NAME = "alcovanish-signs.json";
	private static final long MAX_BYTES = 8 * 1024 * 1024;
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

	private record ExportFile(int version, Map<String, String[][]> presets) { }

	private SignPresetTransfer() { }

	static int exportPresets() throws IOException {
		var config = NearbyWorkshopClient.CONFIG;
		Map<String, String[][]> presets = new LinkedHashMap<>();
		presets.put(config.activeSignPreset, config.signTemplates);
		presets.putAll(config.savedSignPresets);
		Path path = path();
		Files.createDirectories(path.getParent());
		Path temp = Files.createTempFile(path.getParent(), "alcovanish-signs-", ".tmp");
		try {
			Files.writeString(temp, GSON.toJson(new ExportFile(1, presets)));
			if (Files.size(temp) > MAX_BYTES) throw new IOException("Export exceeds size limit");
			Files.move(temp, path, StandardCopyOption.REPLACE_EXISTING);
		} finally {
			Files.deleteIfExists(temp);
		}
		return presets.size();
	}

	static int importPresets() throws IOException {
		Path path = path();
		if (Files.size(path) > MAX_BYTES) throw new JsonParseException("File exceeds size limit");
		ExportFile file = GSON.fromJson(Files.readString(path), ExportFile.class);
		if (file == null || file.version() != 1 || file.presets() == null || file.presets().isEmpty()) {
			throw new JsonParseException("Invalid preset file");
		}
		var config = NearbyWorkshopClient.CONFIG;
		Map<String, String[][]> occupied = new LinkedHashMap<>(config.savedSignPresets);
		occupied.put(config.activeSignPreset, config.signTemplates);
		Map<String, String[][]> additions = new LinkedHashMap<>();
		for (var entry : file.presets().entrySet()) {
			String base = entry.getKey().strip();
			String[][] templates = entry.getValue();
			validate(base, templates);
			String name = base;
			for (int suffix = 2; ; suffix++) {
				String match = matchingName(occupied, name);
				if (match == null) {
					occupied.put(name, templates);
					additions.put(name, templates);
					break;
				}
				if (Arrays.deepEquals(occupied.get(match), templates)) break;
				String ending = " (" + suffix + ")";
				name = base.substring(0, Math.min(base.length(), 120 - ending.length())) + ending;
			}
		}
		config.savedSignPresets.putAll(additions);
		return additions.size();
	}

	private static void validate(String name, String[][] templates) {
		if (name.isBlank() || name.length() > 120 || templates == null || templates.length == 0) {
			throw new JsonParseException("Invalid preset");
		}
		for (String[] row : templates) {
			if (row == null || row.length > 4) throw new JsonParseException("Invalid sign");
			for (String line : row) {
				if (line == null || line.length() > 384 || line.contains("\n") || line.contains("\r")) {
					throw new JsonParseException("Invalid sign line");
				}
			}
		}
	}

	private static String matchingName(Map<String, String[][]> presets, String name) {
		for (String existing : presets.keySet()) {
			if (existing.equalsIgnoreCase(name)) return existing;
		}
		return null;
	}

	private static Path path() {
		return FabricLoader.getInstance().getConfigDir().resolve(FILE_NAME);
	}
}
