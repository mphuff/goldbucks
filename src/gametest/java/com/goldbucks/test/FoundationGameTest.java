package com.goldbucks.test;

import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;

import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.fabricmc.loader.api.FabricLoader;

import com.goldbucks.EconomyService;
import com.goldbucks.economy.Account;
import com.goldbucks.economy.Economy;

public class FoundationGameTest {
	@GameTest
	public void modLoads(GameTestHelper helper) {
		helper.assertTrue(FabricLoader.getInstance().isModLoaded("goldbucks"), "goldbucks should be loaded");
		helper.succeed();
	}

	@GameTest
	public void joiningPlayerGetsAccount(GameTestHelper helper) {
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		Economy economy = EconomyService.economy(helper.getLevel().getServer());

		Account account = economy.account(player.getUUID());
		helper.assertTrue(account != null, "a joining player should get an account");
		helper.assertValueEqual(account.balance(), 20, "starting money");

		boolean listed = EconomyService.sidebar(helper.getLevel().getServer()).stream()
				.anyMatch(entry -> entry.name().equals(account.name()) && entry.cash() == 20 && entry.netWorth() == 20);
		helper.assertTrue(listed, "an online player should be on the side bar");

		player.connection.disconnect(net.minecraft.network.chat.Component.literal("test over"));
		helper.succeed();
	}
}
