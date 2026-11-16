package com.goldbucks;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.google.gson.JsonParseException;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.world.level.storage.LevelResource;

import com.goldbucks.economy.Settings;
import com.goldbucks.economy.TownData;

/**
 * Loads the world settings from {@code <world>/goldbucks/settings.json} and the town data files
 * from data packs, checks both, and refuses anything broken so a bad edit cannot break the world.
 */
public final class SettingsService {
	private static final String[] DATA_FILES = {"item_values.json", "store.json", "orders.json", "events.json"};
	/** Problems found the last time settings or data were loaded, shown to the host when they join. */
	private static final List<String> problems = new ArrayList<>();

	/** Changes made to the settings whenever they load. Only the automated tests use this. */
	private static java.util.function.Consumer<Settings> testOverride;

	private SettingsService() {
	}

	public static void setTestOverride(java.util.function.Consumer<Settings> override) {
		testOverride = override;
	}

	public static void register() {
		// Settings are loaded by Activation when Goldbucks is on in the world.
		ServerLifecycleEvents.END_DATA_PACK_RELOAD.register((server, resources, success) -> {
			if (success && Activation.active()) {
				loadData(server);
				tellHost(server);
			}
		});
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
			Settings.reset();
			problems.clear();
		});
	}

	/** Reads the settings file and data again. The automated tests use this after editing the file. */
	public static void reloadForTests(MinecraftServer server) {
		load(server);
	}

	public static List<String> problems() {
		return List.copyOf(problems);
	}

	public static Path settingsFile(MinecraftServer server) {
		return server.getWorldPath(LevelResource.ROOT).resolve("goldbucks").resolve("settings.json");
	}

	public static void load(MinecraftServer server) {
		problems.clear();
		Path file = settingsFile(server);
		String json = null;

		try {
			if (Files.exists(file)) {
				json = Files.readString(file);
			}
		} catch (IOException e) {
			problems.add("Could not read " + file + ": " + e.getMessage());
		}

		Settings.Loaded loaded = Settings.load(json);

		if (!loaded.ok()) {
			for (String problem : loaded.problems()) {
				problems.add("settings.json: " + problem);
			}

			problems.add("settings.json was not used; the game is using the default settings until it is fixed.");
		}

		if (testOverride != null) {
			testOverride.accept(loaded.settings());
		}

		Settings.activate(loaded.settings());

		// A missing file is written out with the defaults, so the host can see what can be changed.
		if (json == null) {
			save(server, loaded.settings());
		}

		loadData(server);
		problems.forEach(problem -> GoldbucksMod.LOGGER.warn(problem));
	}

	/** Saves new settings and makes them active. They must already be valid. */
	public static boolean save(MinecraftServer server, Settings settings) {
		Settings.activate(settings);

		try {
			Path file = settingsFile(server);
			Files.createDirectories(file.getParent());
			Files.writeString(file, settings.toJson());
			return true;
		} catch (IOException e) {
			GoldbucksMod.LOGGER.error("Could not save settings", e);
			return false;
		}
	}

	/** Reads the town data from data packs (the mod's own files, or a world data pack's replacements). */
	static void loadData(MinecraftServer server) {
		problems.removeIf(problem -> problem.startsWith("data:"));
		TownData data;

		try {
			String[] texts = new String[DATA_FILES.length];

			for (int i = 0; i < DATA_FILES.length; i++) {
				texts[i] = read(server, DATA_FILES[i]);
			}

			data = TownData.parse(texts[0], texts[1], texts[2], texts[3]);
		} catch (JsonParseException | IOException broken) {
			problems.add("data: a town data file could not be read: " + broken.getMessage());
			data = null;
		}

		List<String> dataProblems = data == null ? List.of() : data.validate(Settings.current(), SettingsService::itemExists);

		if (data != null && dataProblems.isEmpty()) {
			TownData.activate(data);
			return;
		}

		dataProblems.forEach(problem -> problems.add("data: " + problem));
		problems.add("data: the mod's own town data is being used until the data pack is fixed.");
		TownData.activate(builtIn());
	}

	private static String read(MinecraftServer server, String file) throws IOException {
		Identifier id = GoldbucksMod.id("town/" + file);
		List<Resource> stack = server.getResourceManager().getResourceStack(id);

		if (stack.isEmpty()) {
			return readBuiltIn(file);
		}

		// The last pack in the list is the one on top, which wins.
		try (InputStream in = stack.get(stack.size() - 1).open()) {
			return new String(in.readAllBytes(), StandardCharsets.UTF_8);
		}
	}

	private static String readBuiltIn(String file) throws IOException {
		try (InputStream in = SettingsService.class.getResourceAsStream("/data/goldbucks/town/" + file)) {
			if (in == null) {
				throw new IOException("missing " + file);
			}

			return new String(in.readAllBytes(), StandardCharsets.UTF_8);
		}
	}

	/** The town data inside the mod jar, which is always valid (a unit test checks it). */
	public static TownData builtIn() {
		try {
			return TownData.parse(readBuiltIn(DATA_FILES[0]), readBuiltIn(DATA_FILES[1]), readBuiltIn(DATA_FILES[2]), readBuiltIn(DATA_FILES[3]));
		} catch (IOException e) {
			throw new IllegalStateException("The mod's own data files are missing", e);
		}
	}

	public static boolean itemExists(String id) {
		Identifier parsed = Identifier.tryParse(id);
		return parsed != null && BuiltInRegistries.ITEM.containsKey(parsed);
	}

	/** Tells the host about any problems with the settings or data. */
	public static void tellHost(MinecraftServer server) {
		if (problems.isEmpty()) {
			return;
		}

		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			if (WorldRules.isHost(server, player)) {
				Text.warn(player, "Some settings could not be used:");
				problems.stream().limit(8).forEach(problem -> Text.warn(player, problem));
			}
		}
	}

	/**
	 * Applies changes from the host: all are checked together, and nothing changes if any is wrong.
	 * Returns the problems, or an empty list when the changes were saved.
	 */
	public static List<String> change(MinecraftServer server, Map<String, String> changes) {
		Settings.Changed changed = Settings.current().with(changes);

		if (!changed.ok()) {
			return changed.problems();
		}

		// The data files must still make sense with the new settings (for example, plot sizes).
		List<String> dataProblems = TownData.current().validate(changed.settings(), SettingsService::itemExists);

		if (!dataProblems.isEmpty()) {
			return dataProblems;
		}

		save(server, changed.settings());
		WorldRules.applySettings(server);
		return List.of();
	}
}
