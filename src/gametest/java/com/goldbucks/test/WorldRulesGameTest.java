package com.goldbucks.test;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.LevelBasedPermissionSet;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;

import net.fabricmc.fabric.api.gametest.v1.GameTest;

import com.goldbucks.WorldRules;

public class WorldRulesGameTest {
	private static void leave(ServerPlayer player) {
		player.connection.disconnect(Component.literal("test over"));
	}

	@GameTest
	public void playersCannotFly(GameTestHelper helper) {
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		helper.assertTrue(player.gameMode.getGameModeForPlayer() == GameType.SURVIVAL, "players join in survival");
		helper.assertFalse(player.getAbilities().mayfly, "players cannot fly");
		leave(player);
		helper.succeed();
	}

	@GameTest(maxTicks = 60)
	public void playersAreNotCreative(GameTestHelper helper) {
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		player.setGameMode(GameType.CREATIVE);
		helper.assertTrue(player.getAbilities().instabuild, "the test player really was put in creative");

		helper.runAfterDelay(25, () -> {
			helper.assertTrue(player.gameMode.getGameModeForPlayer() == GameType.SURVIVAL, "creative mode is switched back to survival");
			helper.assertFalse(player.getAbilities().instabuild, "no all-items menu or free building");
			helper.assertFalse(player.getAbilities().mayfly, "no flying");
			leave(player);
			helper.succeed();
		});
	}

	@GameTest
	public void monstersDoNotSpawn(GameTestHelper helper) {
		BlockPos pos = new BlockPos(1, 2, 1);
		helper.spawn(EntityTypes.ZOMBIE, pos);
		helper.spawn(EntityTypes.COW, pos);

		helper.runAfterDelay(2, () -> {
			helper.assertEntityNotPresent(EntityTypes.ZOMBIE);
			helper.assertEntityPresent(EntityTypes.COW);
			helper.succeed();
		});
	}

	@GameTest
	public void playersCannotDie(GameTestHelper helper) {
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		float before = player.getHealth();

		player.hurtServer(helper.getLevel(), player.damageSources().generic(), 1000f);
		player.hurtServer(helper.getLevel(), player.damageSources().starve(), 1000f);
		player.hurtServer(helper.getLevel(), player.damageSources().fall(), 1000f);

		helper.assertTrue(player.isAlive(), "players cannot die");
		helper.assertValueEqual(player.getHealth(), before, "health after being hurt");
		leave(player);
		helper.succeed();
	}

	@GameTest
	public void cheatCommandsBlocked(GameTestHelper helper) {
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		// Give the player full operator rights, as LAN "allow cheats" would.
		CommandSourceStack source = player.createCommandSourceStack().withPermission(LevelBasedPermissionSet.OWNER);

		helper.assertTrue(WorldRules.isBlocked(source, "give @s minecraft:diamond"), "give is blocked");
		helper.assertTrue(WorldRules.isBlocked(source, "/time set day"), "time is blocked");
		helper.assertTrue(WorldRules.isBlocked(source, "minecraft:gamemode creative"), "namespaced commands are blocked");
		helper.assertFalse(WorldRules.isBlocked(source, "msg someone hello"), "chat commands still work");

		helper.getLevel().getServer().getCommands().performPrefixedCommand(source, "give @s minecraft:diamond");
		helper.assertFalse(player.getInventory().contains(Items.DIAMOND.getDefaultInstance()), "the blocked command did nothing");

		CommandSourceStack console = helper.getLevel().getServer().createCommandSourceStack();
		helper.assertFalse(WorldRules.isBlocked(console, "time set day"), "the server itself is not blocked");

		leave(player);
		helper.succeed();
	}

	/** Issue 2: look-up commands work for everyone, and a command that does not exist is not called host-only. */
	@GameTest
	public void lookUpCommandsWorkForEveryone(GameTestHelper helper) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		// An ordinary LAN guest: no operator rights at all.
		CommandSourceStack source = player.createCommandSourceStack();
		var dispatcher = helper.getLevel().getServer().getCommands().getDispatcher();

		for (String command : new String[] {"goldbucks", "shop", "balance", "job", "plot", "business", "guide", "home"}) {
			helper.assertFalse(WorldRules.isBlocked(source, command), "/" + command + " is open to everyone");
			helper.assertTrue(dispatcher.execute(command, source) > 0, "/" + command + " runs for an ordinary player");
		}

		helper.assertFalse(WorldRules.isBlocked(source, "money"), "a command that does not exist is not reported as host-only");
		helper.assertFalse(WorldRules.isBlocked(source, "/sell bread"), "nor is another made-up one");

		// Commands that change the game are still for the host only.
		CommandSourceStack op = source.withPermission(LevelBasedPermissionSet.OWNER);
		helper.assertTrue(WorldRules.isBlocked(op, "give @s minecraft:diamond"), "give is still blocked");
		helper.assertTrue(WorldRules.isBlocked(op, "host payday"), "host commands are still blocked");
		leave(player);
		helper.succeed();
	}
}
