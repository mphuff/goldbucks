package com.goldbucks.test;

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;

import com.goldbucks.EconomyService;
import com.goldbucks.client.JobScreen;
import com.goldbucks.economy.Job;

@SuppressWarnings("UnstableApiUsage")
public class JobMenuClientTest implements FabricClientGameTest {
	@Override
	public void runTest(ClientGameTestContext context) {
		try (TestSingleplayerContext singleplayer = context.worldBuilder().create()) {
			singleplayer.getConnection().waitForChunksRender();

			// The menu opens by itself on first login.
			context.waitForScreen(JobScreen.class);
			context.takeScreenshot("job-menu");

			context.clickScreenButton("Cook  -  $7 a day");
			context.waitFor(client -> client.gui.screen() == null);
			singleplayer.getConnection().waitForServerboundPackets();

			Job job = singleplayer.getServer().computeOnServer(server -> {
				var player = server.getPlayerList().getPlayers().get(0);
				var owned = EconomyService.economy(server).businessesOf(player.getUUID());
				return owned.isEmpty() ? null : owned.get(0).job();
			});

			if (job != Job.COOK) {
				throw new AssertionError("Clicking Cook on the job menu should make the player a cook, got " + job);
			}

			context.takeScreenshot("job-chosen");

			// The player was taken to their new house; look at it.
			context.waitTicks(20);
			singleplayer.getConnection().waitForChunksRender();
			context.waitTicks(5);
			// The house must stand on the ground in front of the player, not buried or missing.
			boolean atDoor = singleplayer.getServer().computeOnServer(server -> {
				var player = server.getPlayerList().getPlayers().get(0);
				var lot = EconomyService.economy(server).house(player.getUUID()).lot();
				var door = new net.minecraft.core.BlockPos(lot.minX() + 3, player.blockPosition().getY(), lot.minZ() + 6);
				return server.overworld().getBlockState(door).is(net.minecraft.tags.BlockTags.DOORS)
						&& Math.abs(player.getX() - (door.getX() + 0.5)) < 1
						&& player.getZ() > door.getZ() && player.getZ() < door.getZ() + 6;
			});

			if (!atDoor) {
				throw new AssertionError("The player should be standing in front of their house's door at ground level");
			}

			context.takeScreenshot("starting-house");

			// Once the chat messages have faded, the name sign by the door is in plain view.
			context.waitTicks(230);
			context.takeScreenshot("house-name-sign");
		}
	}
}
