package com.goldbucks.test;

import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import net.minecraft.world.level.block.entity.SignTextSlot;

import net.fabricmc.fabric.api.gametest.v1.GameTest;

import com.goldbucks.EconomyService;
import com.goldbucks.GuideService;
import com.goldbucks.HouseBuilder;
import com.goldbucks.LandService;
import com.goldbucks.economy.Economy;
import com.goldbucks.economy.House;
import com.goldbucks.economy.Plot;

public class HousesGameTest {
	/** Looks up and down a column for a block with this tag. */
	private static BlockPos find(ServerLevel level, int x, int z, TagKey<Block> tag) {
		for (int y = level.getMinY(); y <= level.getMaxY(); y++) {
			BlockPos pos = new BlockPos(x, y, z);

			if (level.getBlockState(pos).is(tag)) {
				return pos;
			}
		}

		return null;
	}

	private static String signText(ServerLevel level, BlockPos pos) {
		if (!(level.getBlockEntity(pos) instanceof SignBlockEntity sign)) {
			return "";
		}

		StringBuilder text = new StringBuilder();
		sign.getText(SignTextSlot.FRONT).getMessages(false).forEach(line -> text.append(line.getString()).append(' '));
		return text.toString();
	}

	@GameTest(maxTicks = 40)
	public void houseIsBuilt(GameTestHelper helper) {
		ServerPlayer first = helper.makeMockServerPlayerInLevel();
		ServerPlayer second = helper.makeMockServerPlayerInLevel();
		ServerLevel overworld = helper.getLevel().getServer().overworld();
		Economy economy = EconomyService.economy(helper.getLevel().getServer());

		House house = economy.house(first.getUUID());
		House next = economy.house(second.getUUID());
		helper.assertTrue(house != null && next != null, "every player gets a house on first login");

		Plot lot = house.lot();
		helper.assertValueEqual(lot.size(), 7, "house width");
		helper.assertValueEqual(next.lot().minX() - (lot.minX() + lot.size()), 10, "gap between neighbouring houses");
		helper.assertValueEqual(next.lot().minZ(), lot.minZ(), "houses stand in a row");

		BlockPos door = find(overworld, lot.minX() + 3, lot.minZ() + 6, BlockTags.DOORS);
		helper.assertTrue(door != null, "the house has a door in its south wall");
		helper.assertTrue(overworld.getBlockState(door.above()).is(BlockTags.DOORS), "the door is two blocks tall");
		helper.assertTrue(find(overworld, lot.minX() + 1, lot.minZ() + 1, BlockTags.BEDS) != null, "the house has a bed");

		BlockPos westWindow = new BlockPos(lot.minX(), door.getY() + 1, lot.minZ() + 3);
		BlockPos eastWindow = new BlockPos(lot.minX() + 6, door.getY() + 1, lot.minZ() + 3);
		helper.assertTrue(overworld.getBlockState(westWindow).is(BlockTags.IMPERMEABLE), "window in the west wall");
		helper.assertTrue(overworld.getBlockState(eastWindow).is(BlockTags.IMPERMEABLE), "window in the east wall");

		helper.assertTrue(first.getUUID().equals(economy.landOwnerAt(lot.minX() + 3, lot.minZ() + 3)), "the house belongs to its player");
		helper.assertFalse(second.gameMode.destroyBlock(door), "a neighbour cannot break the door");

		// Issue 3: the house is recognisable and is the player's spawn point.
		BlockPos signPos = HouseBuilder.signPos(door);
		helper.assertTrue(signText(overworld, signPos).contains(first.getGameProfile().name() + "'s"), "a sign by the door names the owner");
		helper.assertFalse(second.gameMode.destroyBlock(signPos), "a neighbour cannot break the name sign");
		BlockPos respawn = first.getRespawnConfig().respawnData().pos();
		helper.assertTrue(first.getRespawnConfig().forced(), "the spawn point does not depend on a bed");
		helper.assertTrue(economy.landOwnerAt(respawn.getX(), respawn.getZ() - 2) != null
				&& Math.abs(respawn.getX() - door.getX()) <= 1 && respawn.getZ() - door.getZ() <= 3 && respawn.getZ() > door.getZ(),
				"the player's spawn point is at their front door");

		// Every new player gets the guide book.
		helper.assertTrue(first.getInventory().contains(stack -> stack.is(Items.WRITTEN_BOOK)
				&& GuideService.GUIDE_TITLE.equals(stack.get(DataComponents.WRITTEN_BOOK_CONTENT).title().raw())), "the guide book is given");

		// A house built by the first version has no sign; it gets one the next time its owner logs in.
		overworld.setBlockAndUpdate(signPos, Blocks.AIR.defaultBlockState());
		LandService.onJoin(first);
		helper.assertTrue(signText(overworld, signPos).contains("House"), "an old house gets its name sign on login");

		// /home brings a player back from anywhere.
		helper.runAfterDelay(8, () -> second.snapTo(second.getX() + 300, second.getY(), second.getZ() + 300));
		helper.runAfterDelay(9, () -> helper.assertTrue(LandService.goHome(second), "/home finds the house"));

		helper.runAfterDelay(10, () -> {
			BlockPos secondDoor = HouseBuilder.findDoor(overworld, next.lot());
			helper.assertTrue(Math.abs(second.getX() - (secondDoor.getX() + 0.5)) < 1 && second.getZ() > secondDoor.getZ()
					&& second.getZ() < secondDoor.getZ() + 6, "/home puts the player at their own front door");
			helper.assertTrue(Math.abs(first.getX() - (door.getX() + 0.5)) < 1 && first.getZ() > door.getZ()
					&& first.getZ() < door.getZ() + 6, "the player is taken to their front door");
			helper.succeed();
		});
	}

	@GameTest(maxTicks = 40)
	public void housesAreNeverBuiltInWater(GameTestHelper helper) {
		ServerLevel overworld = helper.getLevel().getServer().overworld();
		Economy economy = EconomyService.economy(helper.getLevel().getServer());
		ServerPlayer first = helper.makeMockServerPlayerInLevel();
		House firstHouse = economy.house(first.getUUID());

		// Flood the next two lots along the row.
		int step = com.goldbucks.economy.Rules.HOUSE_SIZE + com.goldbucks.economy.Rules.HOUSE_GAP;
		java.util.Set<Integer> taken = new java.util.HashSet<>();
		economy.houses().forEach(house -> taken.add(house.index()));
		int rowStartX = firstHouse.lot().minX() - firstHouse.index() * step;
		java.util.List<Plot> flooded = new java.util.ArrayList<>();

		for (int index = 0; flooded.size() < 2; index++) {
			if (!taken.contains(index)) {
				flooded.add(new Plot(rowStartX + index * step, firstHouse.lot().minZ(), 7));
			}
		}

		for (Plot lot : flooded) {
			for (int x = lot.minX() - 1; x <= lot.minX() + 7; x++) {
				for (int z = lot.minZ() - 1; z <= lot.minZ() + 9; z++) {
					overworld.getChunk(x >> 4, z >> 4);
					int top = overworld.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z) - 1;
					overworld.setBlock(new BlockPos(x, top, z), Blocks.WATER.defaultBlockState(), 2);
				}
			}
		}

		ServerPlayer second = helper.makeMockServerPlayerInLevel();
		House house = economy.house(second.getUUID());

		for (Plot lot : flooded) {
			helper.assertFalse(house.lot().equals(lot), "the house skipped the flooded lot at " + lot.minX());
		}

		BlockPos door = HouseBuilder.findDoor(overworld, house.lot());
		helper.assertTrue(door != null && overworld.getBlockState(door.below()).getFluidState().isEmpty(), "the floor is not water");
		helper.succeed();
	}

	@GameTest
	public void newPlayersGetAFarmingKitOnce(GameTestHelper helper) {
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		int seeds = 0;

		for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
			if (player.getInventory().getItem(slot).is(Items.WHEAT_SEEDS)) {
				seeds += player.getInventory().getItem(slot).getCount();
			}
		}

		helper.assertValueEqual(seeds, 16, "a new player has wheat seeds to start a farm");
		GuideService.giveFarmKitOnce(player);
		int after = 0;

		for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
			if (player.getInventory().getItem(slot).is(Items.WHEAT_SEEDS)) {
				after += player.getInventory().getItem(slot).getCount();
			}
		}

		helper.assertValueEqual(after, 16, "the kit is only given once");
		helper.assertTrue(com.goldbucks.BoardService.hint("minecraft:wheat") != null, "the board says where wheat comes from");
		helper.succeed();
	}
}
