package com.goldbucks.test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.LevelBasedPermissionSet;

import net.fabricmc.fabric.api.gametest.v1.GameTest;

import com.goldbucks.SettingsService;
import com.goldbucks.economy.Settings;
import com.goldbucks.economy.TownData;

/** The world settings file, the host's settings commands, and the checks on data files against the real game. */
public class SettingsGameTest {
	@GameTest
	public void settingsFileIsWrittenAndHostCanChangeIt(GameTestHelper helper) throws Exception {
		MinecraftServer server = helper.getLevel().getServer();
		Path file = SettingsService.settingsFile(server);
		helper.assertTrue(Files.exists(file), "a new world gets a settings file with the defaults");

		CommandSourceStack host = server.createCommandSourceStack();
		server.getCommands().performPrefixedCommand(host, "host set priceCap 30");
		helper.assertValueEqual(Settings.current().priceCap, 30, "the host changed the price cap");
		helper.assertTrue(Files.readString(file).contains("\"priceCap\": 30"), "the change is saved in the world folder");

		// Bad values and bad combinations are refused, and nothing changes.
		server.getCommands().performPrefixedCommand(host, "host set priceCap 0");
		server.getCommands().performPrefixedCommand(host, "host set priceCap lots");
		server.getCommands().performPrefixedCommand(host, "host set masterDays 2");
		server.getCommands().performPrefixedCommand(host, "host set noSuchSetting 4");
		helper.assertValueEqual(Settings.current().priceCap, 30, "a refused change leaves the price cap alone");
		helper.assertValueEqual(Settings.current().masterDays, 30, "levels cannot be put out of order");

		// Changes that only work together are checked together.
		List<String> problems = SettingsService.change(server, Map.of("priceCap", "70", "masterPriceCap", "90"));
		helper.assertTrue(problems.isEmpty(), "two changes that only work together: " + problems);
		helper.assertValueEqual(Settings.current().priceCap, 70, "both saved");

		// The store's plot sizes must still make sense with a new plot size.
		List<String> plotProblems = SettingsService.change(server, Map.of("plotSize", "30"));
		helper.assertFalse(plotProblems.isEmpty(), "a plot as big as the Bigger plot upgrade is refused");
		helper.assertValueEqual(Settings.current().plotSize, 16, "plot size unchanged");

		SettingsService.change(server, Map.of("priceCap", "40", "masterPriceCap", "60"));
		helper.assertValueEqual(Settings.current().priceCap, 40, "back to the default");
		helper.succeed();
	}

	@GameTest
	public void onlyTheHostCanChangeSettings(GameTestHelper helper) {
		MinecraftServer server = helper.getLevel().getServer();
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		// Even with full operator rights, as LAN "allow cheats" would give.
		CommandSourceStack source = player.createCommandSourceStack().withPermission(LevelBasedPermissionSet.OWNER);

		server.getCommands().performPrefixedCommand(source, "host set priceCap 5");
		helper.assertValueEqual(Settings.current().priceCap, 40, "a player who is not the host cannot change settings");
		helper.succeed();
	}

	@GameTest
	public void aBrokenSettingsFileFallsBackToDefaults(GameTestHelper helper) throws Exception {
		MinecraftServer server = helper.getLevel().getServer();
		Path file = SettingsService.settingsFile(server);
		String good = Files.readString(file);

		Files.writeString(file, "{ \"priceCap\": 0, ");
		SettingsService.reloadForTests(server);
		helper.assertFalse(SettingsService.problems().isEmpty(), "the host is told the file is broken");
		helper.assertValueEqual(Settings.current().priceCap, 40, "the defaults are used");

		Files.writeString(file, "{ \"priceCap\": 0 }");
		SettingsService.reloadForTests(server);
		helper.assertTrue(SettingsService.problems().stream().anyMatch(p -> p.contains("priceCap")), "the bad value is named");
		helper.assertValueEqual(Settings.current().priceCap, 40, "the defaults are used");

		Files.writeString(file, good);
		SettingsService.reloadForTests(server);
		helper.assertTrue(SettingsService.problems().isEmpty(), "a fixed file loads cleanly: " + SettingsService.problems());
		helper.succeed();
	}

	@GameTest
	public void dataFilesNameRealItems(GameTestHelper helper) {
		List<String> problems = TownData.current().validate(Settings.current(), SettingsService::itemExists);
		helper.assertTrue(problems.isEmpty(), "every item in the shipped data files exists in this version of Minecraft: " + problems);
		helper.assertTrue(SettingsService.builtIn().validate(Settings.current(), SettingsService::itemExists).isEmpty(), "the built-in copy too");

		TownData typo = TownData.parse("{\"minecraft:breadd\": 2}", "[]", "[]", "{}");
		helper.assertFalse(typo.validate(Settings.current(), SettingsService::itemExists).isEmpty(), "a misspelt item is caught");
		helper.assertTrue(TownData.current().store.size() >= 18, "the store data loaded from the data pack");
		helper.succeed();
	}
}
