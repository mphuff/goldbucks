package com.goldbucks.client;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;

import net.fabricmc.loader.api.FabricLoader;

/** Settings that only change this player's own screen, saved in config/goldbucks-client.json. */
public final class ClientSettings {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static ClientSettings current;

	/** Play a sound when one of your shops sells something. */
	public boolean saleSound = true;
	/** Show a line above the hotbar when one of your shops sells something. */
	public boolean saleMessage = true;
	/** Show the net worth side bar on the left instead of the right. */
	public boolean sidebarOnLeft = false;
	/** Show the side bar at all. */
	public boolean showSidebar = true;

	public static Path file() {
		return FabricLoader.getInstance().getConfigDir().resolve("goldbucks-client.json");
	}

	public static ClientSettings get() {
		if (current == null) {
			current = load();
		}

		return current;
	}

	/** Reads the file. A missing or broken file gives the defaults, so the game always starts. */
	static ClientSettings load() {
		try {
			if (Files.exists(file())) {
				ClientSettings read = GSON.fromJson(Files.readString(file()), ClientSettings.class);

				if (read != null) {
					return read;
				}
			}
		} catch (IOException | JsonParseException broken) {
			com.goldbucks.GoldbucksMod.LOGGER.warn("Could not read {}, using the defaults: {}", file(), broken.getMessage());
		}

		return new ClientSettings();
	}

	public void save() {
		try {
			Files.createDirectories(file().getParent());
			Files.writeString(file(), GSON.toJson(this));
		} catch (IOException e) {
			com.goldbucks.GoldbucksMod.LOGGER.warn("Could not save {}", file(), e);
		}
	}
}
