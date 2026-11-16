package com.goldbucks.test;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.LevelBasedPermissionSet;

import net.fabricmc.fabric.api.gametest.v1.GameTest;

import com.goldbucks.NameTags;
import com.goldbucks.EconomyService;
import com.goldbucks.JobsService;
import com.goldbucks.economy.Economy;
import com.goldbucks.economy.Job;

public class HostCommandsGameTest {
	@GameTest
	public void hostCanFireSetBalancesAndForgive(GameTestHelper helper) {
		MinecraftServer server = helper.getLevel().getServer();
		Economy economy = EconomyService.economy(server);
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		JobsService.choose(player, Job.COOK);
		// Every test player has the same name, so pick this one out with a tag.
		player.addTag("host_test_target");
		String who = "@a[tag=host_test_target,limit=1]";
		// The server console stands in for the host: both are allowed to run host commands.
		CommandSourceStack host = server.createCommandSourceStack();

		server.getCommands().performPrefixedCommand(host, "host setbalance " + who + " -45");
		helper.assertValueEqual(economy.account(player.getUUID()).balance(), -45, "balance set by the host");
		helper.assertValueEqual(NameTags.labelOf(player).strip(), "DEBT -$45", "debt label after the host sets a debt");

		server.getCommands().performPrefixedCommand(host, "host forgive " + who);
		helper.assertValueEqual(economy.account(player.getUUID()).balance(), 0, "balance after the debt is forgiven");
		helper.assertValueEqual(NameTags.labelOf(player), "", "label after the debt is forgiven");

		server.getCommands().performPrefixedCommand(host, "host fire " + who);
		helper.assertTrue(economy.needsJob(player.getUUID()), "a fired player has no job");
		helper.assertTrue(JobsService.choose(player, Job.STORE_OWNER).ok(), "and can pick a new one");
		helper.succeed();
	}

	@GameTest
	public void otherPlayersCannotUseHostCommands(GameTestHelper helper) {
		MinecraftServer server = helper.getLevel().getServer();
		Economy economy = EconomyService.economy(server);
		ServerPlayer cheater = helper.makeMockServerPlayerInLevel();
		// Even with full operator rights, as LAN "allow cheats" would give.
		CommandSourceStack source = cheater.createCommandSourceStack().withPermission(LevelBasedPermissionSet.OWNER);

		cheater.addTag("host_test_cheater");
		String who = "@a[tag=host_test_cheater,limit=1]";

		server.getCommands().performPrefixedCommand(source, "host setbalance " + who + " 5000");
		helper.assertValueEqual(economy.account(cheater.getUUID()).balance(), 20, "a player cannot give themselves money");

		// The same command from the console works, so it was the host rule that stopped it.
		server.getCommands().performPrefixedCommand(server.createCommandSourceStack(), "host setbalance " + who + " 33");
		helper.assertValueEqual(economy.account(cheater.getUUID()).balance(), 33, "the console can set the balance");

		server.getCommands().performPrefixedCommand(source, "host forgive " + who);
		server.getCommands().performPrefixedCommand(source, "host payday");

		helper.assertValueEqual(economy.account(cheater.getUUID()).balance(), 33, "other host commands are refused too");
		helper.succeed();
	}
}
