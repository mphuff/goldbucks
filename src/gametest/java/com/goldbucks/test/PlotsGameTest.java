package com.goldbucks.test;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

import net.fabricmc.fabric.api.gametest.v1.GameTest;

import com.goldbucks.EconomyService;
import com.goldbucks.JobsService;
import com.goldbucks.LandService;
import com.goldbucks.economy.Job;
import com.goldbucks.economy.Result;

public class PlotsGameTest {
	private static void place(ServerPlayer player, ServerLevel level, BlockPos on) {
		ItemStack dirt = new ItemStack(Items.DIRT);
		player.setItemInHand(InteractionHand.MAIN_HAND, dirt);
		BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(on).add(0, 0.5, 0), Direction.UP, on, false);
		player.gameMode.useItemOn(player, level, dirt, InteractionHand.MAIN_HAND, hit);
	}

	@GameTest(padding = 12)
	public void strangersCannotBuild(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		ServerPlayer owner = helper.makeMockServerPlayerInLevel();
		ServerPlayer stranger = helper.makeMockServerPlayerInLevel();
		JobsService.choose(owner, Job.STORE_OWNER);
		JobsService.choose(stranger, Job.STORE_OWNER);

		BlockPos center = helper.absolutePos(new BlockPos(1, 2, 1));
		owner.snapTo(center.getX() + 0.5, center.getY(), center.getZ() + 0.5);
		helper.assertTrue(LandService.claim(owner).ok(), "the owner can claim a plot where they stand");
		helper.assertTrue(owner.getUUID().equals(LandService.ownerAt(level, center)), "the land is now the owner's");

		level.setBlockAndUpdate(center, Blocks.STONE.defaultBlockState());
		// Step aside so nobody is standing where blocks will be placed.
		owner.snapTo(center.getX() + 3.5, center.getY(), center.getZ() + 3.5);
		stranger.snapTo(center.getX() - 3.5, center.getY(), center.getZ() - 3.5);

		// A stranger can neither break nor place.
		helper.assertFalse(stranger.gameMode.destroyBlock(center), "a stranger cannot break blocks on the plot");
		helper.assertTrue(level.getBlockState(center).is(Blocks.STONE), "the block is still there");
		place(stranger, level, center);
		helper.assertTrue(level.getBlockState(center.above()).isAir(), "a stranger cannot place blocks on the plot");

		// A stranger cannot claim land that overlaps it either.
		stranger.snapTo(center.getX() + 5.5, center.getY(), center.getZ() + 0.5);
		helper.assertTrue(LandService.claim(stranger) == Result.OVERLAPS, "plots cannot overlap");
		helper.assertTrue(EconomyService.economy(level.getServer()).businessesOf(stranger.getUUID()).get(0).plot() == null,
				"the stranger got no plot");

		// The owner can do both.
		place(owner, level, center);
		helper.assertTrue(level.getBlockState(center.above()).is(Blocks.DIRT), "the owner can place blocks");
		helper.assertTrue(owner.gameMode.destroyBlock(center.above()), "the owner can break blocks");

		level.setBlockAndUpdate(center, Blocks.AIR.defaultBlockState());
		helper.succeed();
	}
}
