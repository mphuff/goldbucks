package com.goldbucks.test;

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;

import net.minecraft.client.gui.screens.worldselection.WorldCreationGameRulesScreen;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.level.gamerules.GameRules;

import com.goldbucks.Activation;
import com.goldbucks.client.ClientBalances;
import com.goldbucks.client.JobScreen;

/** A world made without the Goldbucks game rule is plain Minecraft, until the host switches Goldbucks on. */
@SuppressWarnings("UnstableApiUsage")
public class PlainWorldClientTest implements FabricClientGameTest {
	private static void check(boolean ok, String what) {
		if (!ok) {
			throw new AssertionError(what);
		}
	}

	@Override
	public void runTest(ClientGameTestContext context) {
		// The Create World screen's game rules list has a Goldbucks switch with a proper name.
		check(I18n.get("gamerule.goldbucks.goldbucks").equals("Goldbucks"), "the game rule is called Goldbucks");
		context.setScreen(() -> new WorldCreationGameRulesScreen(new GameRules(FeatureFlags.DEFAULT_FLAGS), rules -> { }));
		context.waitTicks(5);
		context.getInput().typeChars("goldbucks");
		context.waitTicks(5);
		context.takeScreenshot("create-world-game-rules");
		context.setScreen(() -> null);

		Activation.setForceOnForTests(false);

		try (TestSingleplayerContext singleplayer = context.worldBuilder().create()) {
			singleplayer.getConnection().waitForChunksRender();
			context.waitTicks(60);
			check(context.computeOnClient(client -> client.gui.screen() == null), "no job menu in a plain world");
			check(ClientBalances.entries().isEmpty(), "no side bar in a plain world");
			check(!singleplayer.getServer().computeOnServer(server -> Activation.active()), "Goldbucks is off");
			context.takeScreenshot("plain-world");

			// The host switches it on: now it is a Goldbucks world.
			context.runOnClient(client -> client.player.connection.sendCommand("goldbucks on"));
			context.waitForScreen(JobScreen.class);
			check(singleplayer.getServer().computeOnServer(server -> server.getGameRules().get(Activation.RULE)), "the game rule is on");
			context.takeScreenshot("switched-on");
		} finally {
			Activation.setForceOnForTests(true);
		}
	}
}
