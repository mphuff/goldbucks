package com.goldbucks.test;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.monster.zombie.Zombie;

import net.fabricmc.fabric.api.gametest.v1.GameTest;

import com.goldbucks.Activation;
import com.goldbucks.EconomyService;
import com.goldbucks.WorldRules;
import com.goldbucks.economy.Economy;

/** Goldbucks only runs in worlds where its game rule is on. */
public class ActivationGameTest {
	@GameTest
	public void goldbucksCanBeSwitchedOffAndOnPerWorld(GameTestHelper helper) {
		MinecraftServer server = helper.getLevel().getServer();
		Economy economy = EconomyService.economy(server);
		CommandSourceStack console = server.createCommandSourceStack();
		helper.assertTrue(Activation.active() && server.getGameRules().get(Activation.RULE), "the test world is a Goldbucks world");

		server.getCommands().performPrefixedCommand(console, "goldbucks off");
		helper.assertFalse(Activation.active(), "switched off");
		helper.assertFalse(server.getGameRules().get(Activation.RULE), "the game rule follows");

		try {
			// A world without Goldbucks is plain Minecraft.
			ServerPlayer visitor = helper.makeMockServerPlayerInLevel();
			helper.assertFalse(economy.hasAccount(visitor.getUUID()), "no account, house or job menu for a new player");
			Zombie zombie = helper.spawn(EntityTypes.ZOMBIE, new BlockPos(1, 2, 1));
			helper.assertTrue(zombie.isAlive(), "monsters are allowed");
			zombie.discard();
			helper.assertTrue(WorldRules.isOffInThisWorld("balance"), "the mod's commands are refused");
			helper.assertTrue(WorldRules.isOffInThisWorld("/store buy porch"), "every one of them");
			helper.assertFalse(WorldRules.isOffInThisWorld("goldbucks on"), "but it can be switched on");
			helper.assertFalse(WorldRules.isOffInThisWorld("give @s minecraft:diamond"), "vanilla commands are untouched");
			helper.assertFalse(WorldRules.isBlocked(visitor.createCommandSourceStack(), "time set day"), "cheats follow the normal LAN rules");

			// Switching it on sets up everyone who is here.
			server.getCommands().performPrefixedCommand(console, "goldbucks on");
			helper.assertTrue(Activation.active(), "switched back on");
			helper.assertTrue(economy.hasAccount(visitor.getUUID()), "the visitor now has an account");
			helper.assertTrue(economy.house(visitor.getUUID()) != null, "and a house");
		} finally {
			if (!Activation.active()) {
				server.getGameRules().set(Activation.RULE, true, server);
			}
		}

		helper.succeed();
	}

	@GameTest
	public void onlyTheHostCanSwitchItOff(GameTestHelper helper) {
		MinecraftServer server = helper.getLevel().getServer();
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		server.getCommands().performPrefixedCommand(player.createCommandSourceStack()
				.withPermission(net.minecraft.server.permissions.LevelBasedPermissionSet.OWNER), "goldbucks off");
		helper.assertTrue(Activation.active(), "a player who is not the host cannot switch Goldbucks off");
		helper.succeed();
	}
}
