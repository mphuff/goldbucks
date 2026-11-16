package com.goldbucks.test;

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;

import com.goldbucks.EconomyService;
import com.goldbucks.TownSquare;
import com.goldbucks.client.ClientSettings;
import com.goldbucks.client.ClientSettingsScreen;
import com.goldbucks.client.JobScreen;
import com.goldbucks.client.MenuScreen;
import com.goldbucks.client.SettingsScreen;
import com.goldbucks.economy.Settings;
import com.goldbucks.economy.Town;

/** The town update's screens in a real game client: the store, the board, the host settings and the player's own settings. */
@SuppressWarnings("UnstableApiUsage")
public class TownClientTest implements FabricClientGameTest {
	private static void command(ClientGameTestContext context, String command) {
		context.runOnClient(client -> client.player.connection.sendCommand(command));
	}

	private static void check(boolean ok, String what) {
		if (!ok) {
			throw new AssertionError(what);
		}
	}

	@Override
	public void runTest(ClientGameTestContext context) {
		try (TestSingleplayerContext singleplayer = context.worldBuilder().create()) {
			singleplayer.getConnection().waitForChunksRender();
			context.waitForScreen(JobScreen.class);
			context.clickScreenButton("Store Owner  -  $8 a day");
			context.waitFor(client -> client.gui.screen() == null);

			// The Town Store, with enough money to buy something.
			singleplayer.getServer().runOnServer(server -> {
				var player = server.getPlayerList().getPlayers().get(0);
				EconomyService.economy(server).setBalance(player.getUUID(), 100);
			});
			command(context, "store");
			context.waitForScreen(MenuScreen.class);
			check(context.computeOnClient(client -> ((MenuScreen) client.gui.screen()).menu().title()).equals("Town Store"), "the store opened");
			context.takeScreenshot("town-store");

			// The first Buy button is the Porch.
			context.clickScreenButton("Buy");
			singleplayer.getServer().waitFor(server -> EconomyService.economy(server)
					.account(server.getPlayerList().getPlayers().get(0).getUUID()).owns("porch"));
			context.runOnClient(client -> client.gui.setScreen(null));
			context.waitTicks(40);
			singleplayer.getConnection().waitForChunksRender();
			context.takeScreenshot("porch");

			// The Town Board.
			command(context, "board");
			context.waitForScreen(MenuScreen.class);
			check(context.computeOnClient(client -> ((MenuScreen) client.gui.screen()).menu().title()).equals("Town Board"), "the board opened");
			context.takeScreenshot("town-board");
			context.runOnClient(client -> client.gui.setScreen(null));

			// The host settings screen: a good change is saved...
			command(context, "host settings");
			context.waitForScreen(SettingsScreen.class);
			context.takeScreenshot("host-settings");
			context.runOnClient(client -> ((SettingsScreen) client.gui.screen()).setValue("priceCap", "35"));
			context.clickScreenButton("Save");
			singleplayer.getServer().waitFor(server -> Settings.current().priceCap == 35);

			// ...and a bad one is refused, with the reason on screen.
			command(context, "host settings");
			context.waitForScreen(SettingsScreen.class);
			context.runOnClient(client -> ((SettingsScreen) client.gui.screen()).setValue("priceCap", "0"));
			context.clickScreenButton("Save");
			context.waitTicks(10);
			context.waitForScreen(SettingsScreen.class);
			context.takeScreenshot("host-settings-refused");
			check(singleplayer.getServer().computeOnServer(server -> Settings.current().priceCap) == 35, "a bad value is not saved");
			context.clickScreenButton("Cancel");
			singleplayer.getServer().runCommand("host set priceCap 40");

			// The player's own settings.
			command(context, "goldbucks settings");
			context.waitForScreen(ClientSettingsScreen.class);
			context.clickScreenButton("Sale sound: On");
			context.clickScreenButton("Done");
			check(!ClientSettings.get().saleSound, "the sale sound was switched off");
			check(java.nio.file.Files.exists(ClientSettings.file()), "the player's settings are saved");
			ClientSettings.get().saleSound = true;
			ClientSettings.get().save();

			// The town square, and an event's boss bar.
			Town town = singleplayer.getServer().computeOnServer(server -> EconomyService.economy(server).town());
			var board = TownSquare.board(town);
			singleplayer.getServer().runCommand("tp @p " + (board.getX() + 0.5) + " " + board.getY() + " " + (board.getZ() + 6.5) + " 180 15");
			singleplayer.getServer().runCommand("host event start market_day");
			context.waitTicks(40);
			singleplayer.getConnection().waitForChunksRender();
			context.takeScreenshot("town-square-market-day");

			// Payday and the morning news.
			singleplayer.getServer().runCommand("host payday");
			context.waitTicks(15);
			context.takeScreenshot("morning-herald");
		}
	}
}
