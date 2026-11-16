package com.goldbucks.test;

import java.nio.file.Path;

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.fabricmc.fabric.api.client.gametest.v1.screenshot.TestScreenshotOptions;

import net.minecraft.client.gui.screens.worldselection.WorldCreationGameRulesScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.Container;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import net.minecraft.world.level.block.entity.SignText;
import net.minecraft.world.level.block.entity.SignTextSlot;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.level.levelgen.Heightmap;

import com.goldbucks.EconomyService;
import com.goldbucks.HouseBuilder;
import com.goldbucks.LandService;
import com.goldbucks.ShopService;
import com.goldbucks.TownSquare;
import com.goldbucks.Townspeople;
import com.goldbucks.client.JobScreen;
import com.goldbucks.client.MenuScreen;
import com.goldbucks.client.SettingsScreen;
import com.goldbucks.economy.Business;
import com.goldbucks.economy.House;
import com.goldbucks.economy.Town;

/**
 * Takes the screenshots for the website, in a normal generated world. It only runs when GOLDBUCKS_SHOTS
 * names a folder to save them in: GOLDBUCKS_SHOTS=$PWD/site/images scripts/client-test.sh
 */
@SuppressWarnings("UnstableApiUsage")
public class ScreenshotsClientTest implements FabricClientGameTest {
	private static final int WIDTH = 1920;
	private static final int HEIGHT = 1080;

	private Path folder;

	private void shot(ClientGameTestContext context, String name) {
		context.takeScreenshot(TestScreenshotOptions.of(name).disableCounterPrefix().withDestinationDir(folder));
	}

	/** Turns the HUD (hotbar, side bar, chat) off or on, as F1 does. */
	private boolean hudShown = true;

	private void hud(ClientGameTestContext context, boolean show) {
		if (hudShown != show) {
			context.getInput().pressKey(options -> options.keyToggleGui);
			context.waitTicks(2);
			hudShown = show;
		}
	}

	/** Clears trees from a box, so they do not block the camera. */
	private static void clearTrees(TestSingleplayerContext world, int x1, int y1, int z1, int x2, int y2, int z2) {
		for (int x = Math.min(x1, x2); x <= Math.max(x1, x2); x += 16) {
			for (int z = Math.min(z1, z2); z <= Math.max(z1, z2); z += 16) {
				for (String kind : new String[] {"leaves", "logs"}) {
					world.getServer().runCommand(String.format(java.util.Locale.ROOT, "fill %d %d %d %d %d %d air replace #minecraft:%s",
							x, Math.min(y1, y2), z, Math.min(x + 15, Math.max(x1, x2)), Math.max(y1, y2), Math.min(z + 15, Math.max(z1, z2)), kind));
				}
			}
		}
	}

	private static void command(ClientGameTestContext context, String command) {
		context.runOnClient(client -> client.player.connection.sendCommand(command));
	}

	/** Puts the camera at a spot (at least 1 block above the ground there), looking at a target block. */
	private static void look(TestSingleplayerContext world, double x, double wantedY, double z, BlockPos target) {
		int ground = world.getServer().computeOnServer(server -> {
			server.overworld().getChunk((int) Math.floor(x) >> 4, (int) Math.floor(z) >> 4);
			return server.overworld().getHeight(Heightmap.Types.MOTION_BLOCKING, (int) Math.floor(x), (int) Math.floor(z));
		});
		double y = Math.max(wantedY, ground + 1);
		double dx = target.getX() + 0.5 - x;
		double dy = target.getY() + 0.5 - (y + 1.62);
		double dz = target.getZ() + 0.5 - z;
		float yaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
		float pitch = (float) -Math.toDegrees(Math.atan2(dy, Math.hypot(dx, dz)));
		world.getServer().runCommand(String.format(java.util.Locale.ROOT, "tp @p %.2f %.2f %.2f %.1f %.1f", x, y, z, yaw, pitch));
	}

	/** Waits for the world to draw and the chat to fade. */
	private static void settle(ClientGameTestContext context, TestSingleplayerContext world, int ticks) {
		context.waitTicks(5);
		world.getConnection().waitForChunksRender();
		context.waitTicks(ticks);
	}

	@Override
	public void runTest(ClientGameTestContext context) {
		String target = System.getenv("GOLDBUCKS_SHOTS");

		if (target == null) {
			return;
		}

		folder = Path.of(target);
		context.getInput().resizeWindow(WIDTH, HEIGHT);
		context.runOnClient(client -> client.options.guiScale().set(3));
		context.waitTicks(5);
		TownSquare.setOffset(-14, 12);
		LandService.setNeighborhoodOffset(8, 8);

		// The Create World screen's game rule switch.
		context.setScreen(() -> new WorldCreationGameRulesScreen(new GameRules(FeatureFlags.DEFAULT_FLAGS), rules -> { }));
		context.waitTicks(5);
		context.getInput().typeChars("gold");
		context.waitTicks(5);
		shot(context, "create-world-switch");
		context.setScreen(() -> null);

		try (TestSingleplayerContext world = context.worldBuilder()
				.adjustSettings(settings -> {
					settings.setWorldType(settings.getNormalPresetList().get(0));
					settings.setSeed("goldbucks");
				})
				.create()) {
			world.getConnection().waitForChunksRender();
			context.waitForScreen(JobScreen.class);
			shot(context, "job-menu");
			context.clickScreenButton("Store Owner  -  $8 a day");
			context.waitFor(client -> client.gui.screen() == null);

			world.getServer().runCommand("time set 2000");
			world.getServer().runCommand("weather clear");
			// No "game mode updated" and similar messages in the chat.
			world.getServer().runCommand("gamerule send_command_feedback false");
			world.getServer().runCommand("gamerule sendCommandFeedback false");
			world.getServer().runCommand("host set townsfolkEnabled true");
			world.getServer().runOnServer(server -> {
				var player = server.getPlayerList().getPlayers().get(0);
				EconomyService.economy(server).setBalance(player.getUUID(), 2000);
			});

			for (String upgrade : new String[] {"porch", "garden", "pool", "stable", "second_floor", "tower"}) {
				command(context, "store buy " + upgrade);
				context.waitTicks(2);
			}

			// The house with its upgrades, from the front.
			House house = world.getServer().computeOnServer(server -> EconomyService.economy(server)
					.house(server.getPlayerList().getPlayers().get(0).getUUID()));
			int floor = world.getServer().computeOnServer(server -> HouseBuilder.findDoor(server.overworld(), house.lot()).getY() - 1);
			BlockPos houseMiddle = new BlockPos(house.lot().minX() + 1, floor + 5, house.lot().minZ() + 1);
			clearTrees(world, house.lot().minX() - 12, floor + 1, house.lot().minZ() - 12, house.lot().minX() + 24, floor + 30, house.lot().minZ() + 26);
			world.getServer().runCommand("gamemode spectator @a");
			look(world, house.lot().minX() + 15.5, floor + 9, house.lot().minZ() + 20.5, houseMiddle);
			world.getServer().runCommand("time set 2000");
			hud(context, false);
			settle(context, world, 40);
			shot(context, "house-upgrades");
			hud(context, true);
			world.getServer().runCommand("gamemode survival @a");

			// A shop with a townsfolk customer on the way.
			Town town = world.getServer().computeOnServer(server -> EconomyService.economy(server).town());
			BlockPos square = TownSquare.center(town);
			int shopX = square.getX();
			int shopZ = square.getZ() + 30;
			int shopY = world.getServer().computeOnServer(server -> {
				server.overworld().getChunk(shopX >> 4, shopZ >> 4);
				return server.overworld().getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, shopX, shopZ);
			});
			world.getServer().runCommand("tp @p " + shopX + " " + shopY + " " + shopZ);
			settle(context, world, 10);
			// Level the ground around the shop, so the camera has a clear view.
			world.getServer().runCommand(String.format(java.util.Locale.ROOT, "fill %d %d %d %d %d %d air", shopX - 10, shopY, shopZ - 10, shopX + 10, shopY + 24, shopZ + 12));
			world.getServer().runCommand(String.format(java.util.Locale.ROOT, "fill %d %d %d %d %d %d grass_block", shopX - 10, shopY - 1, shopZ - 10, shopX + 10, shopY - 1, shopZ + 12));
			world.getServer().runCommand(String.format(java.util.Locale.ROOT, "fill %d %d %d %d %d %d dirt", shopX - 10, shopY - 4, shopZ - 10, shopX + 10, shopY - 2, shopZ + 12));
			context.waitTicks(10);
			command(context, "plot claim");
			context.waitTicks(10);
			world.getServer().runOnServer(server -> {
				var level = server.overworld();
				var player = server.getPlayerList().getPlayers().get(0);
				BlockPos chest = new BlockPos(shopX + 2, shopY, shopZ);
				level.setBlockAndUpdate(chest, Blocks.CHEST.defaultBlockState());
				level.setBlockAndUpdate(chest.above(), Blocks.OAK_SIGN.defaultBlockState());
				level.setBlockAndUpdate(chest.east(), Blocks.LANTERN.defaultBlockState());
				var lines = java.util.List.<Component>of(Component.literal("[Shop]"), Component.literal("$2"), Component.literal("Fresh bread"), Component.empty());
				((SignBlockEntity) level.getBlockEntity(chest.above())).setText(new SignText(lines, lines, DyeColor.BLACK, false), SignTextSlot.FRONT);
				((SignBlockEntity) level.getBlockEntity(chest.above())).setText(new SignText(lines, lines, DyeColor.BLACK, false), SignTextSlot.BACK);
				((Container) level.getBlockEntity(chest)).setItem(0, new ItemStack(Items.BREAD, 32));
				ShopService.onSignEdited(player, level, chest.above());
				Business business = EconomyService.economy(server).businessesOf(player.getUUID()).get(0);
				Townspeople.visit(server, business.owner(), false);
			});
			world.getServer().runCommand("gamemode spectator @a");
			world.getServer().runCommand(String.format(java.util.Locale.ROOT, "summon villager %.1f %d %.1f {NoAI:1b,Rotation:[142f,0f],VillagerData:{profession:\"minecraft:farmer\",level:1,type:\"minecraft:plains\"}}", shopX + 3.5, shopY, shopZ + 1.8));
			look(world, shopX + 0.5, shopY + 0.6, shopZ + 4.5, new BlockPos(shopX + 2, shopY, shopZ));
			hud(context, false);
			settle(context, world, 40);
			shot(context, "shop-customer");
			hud(context, true);
			world.getServer().runCommand("gamemode survival @a");

			// The town square on Market Day, from above.
			world.getServer().runCommand("host event start market_day");
			world.getServer().runCommand("time set 2000");
			clearTrees(world, square.getX() - 24, square.getY() + 1, square.getZ() - 24, square.getX() + 24, square.getY() + 30, square.getZ() + 30);
			world.getServer().runCommand("gamemode spectator @a");
			look(world, square.getX() + 0.5, square.getY() + 12, square.getZ() + 19.5, square.offset(0, 1, -1));
			settle(context, world, 220);
			shot(context, "town-square");
			world.getServer().runCommand("gamemode survival @a");
			world.getServer().runCommand("host event stop");

			// The Town Store, the Town Board and the host settings.
			command(context, "store");
			context.waitForScreen(MenuScreen.class);
			shot(context, "town-store");
			context.runOnClient(client -> client.gui.setScreen(null));

			command(context, "board");
			context.waitForScreen(MenuScreen.class);
			shot(context, "town-board");
			context.runOnClient(client -> client.gui.setScreen(null));

			command(context, "host settings");
			context.waitForScreen(SettingsScreen.class);
			shot(context, "host-settings");
			context.clickScreenButton("Cancel");

			// Payday and the morning Herald.
			look(world, square.getX() + 0.5, square.getY() + 3, square.getZ() + 9.5, square.offset(0, 2, -5));
			settle(context, world, 220);
			world.getServer().runCommand("host payday");
			context.waitTicks(30);
			shot(context, "morning-herald");
		}
	}
}
