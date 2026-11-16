package com.goldbucks.test;

import java.util.List;

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;

import com.goldbucks.client.ClientBalances;
import com.goldbucks.client.JobScreen;
import com.goldbucks.net.BalancesPayload;

@SuppressWarnings("UnstableApiUsage")
public class SidebarClientTest implements FabricClientGameTest {
	@Override
	public void runTest(ClientGameTestContext context) {
		try (TestSingleplayerContext singleplayer = context.worldBuilder().create()) {
			singleplayer.getConnection().waitForChunksRender();

			// Get the first-login job menu out of the way.
			context.waitForScreen(JobScreen.class);
			context.clickScreenButton("Librarian  -  $4 a day");
			context.waitFor(client -> client.gui.screen() == null);

			// The side bar updates within a second of the job being picked.
			context.waitFor(client -> !ClientBalances.entries().isEmpty() && ClientBalances.entries().get(0).netWorth() == 70);
			List<BalancesPayload.Entry> entries = ClientBalances.entries();

			if (entries.size() != 1 || entries.get(0).cash() != 20 || entries.get(0).netWorth() != 70) {
				throw new AssertionError("Expected one player with $20 cash, worth $70 with their business, on the side bar, got " + entries);
			}

			context.takeScreenshot("sidebar");
		}
	}
}
