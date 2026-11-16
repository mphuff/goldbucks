package com.goldbucks;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.levelgen.Heightmap;

import com.goldbucks.economy.Plot;

/**
 * Builds the starting house: a 7x7 oak house with a door on the south side,
 * a window in the east and west walls, a bed, a chest and a torch.
 */
public final class HouseBuilder {
	/** Do not tell neighbouring blocks, so doors and beds are not knocked off while half built. */
	private static final int QUIET = 2;

	private HouseBuilder() {
	}

	/** The height of the house floor on this lot. */
	public static int floorY(ServerLevel level, Plot lot) {
		// Make sure the land exists before measuring it; a chunk nobody has visited has no height yet.
		for (int x = lot.minX() - 1; x <= lot.minX() + lot.size(); x += lot.size() + 1) {
			for (int z = lot.minZ() - 1; z <= lot.minZ() + lot.size() + 1; z += lot.size() + 2) {
				level.getChunk(x >> 4, z >> 4);
			}
		}

		int centerX = lot.minX() + lot.size() / 2;
		int centerZ = lot.minZ() + lot.size() / 2;
		return level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, centerX, centerZ) - 1;
	}

	/** Where the front door (lower half) goes. */
	public static BlockPos doorPos(Plot lot, int floorY) {
		return new BlockPos(lot.minX() + lot.size() / 2, floorY + 1, lot.minZ() + lot.size() - 1);
	}

	/** Where the head of the bed goes. */
	public static BlockPos bedPos(Plot lot, int floorY) {
		return new BlockPos(lot.minX() + 1, floorY + 1, lot.minZ() + 1);
	}

	/** Finds the lower half of an existing house's front door, or null if it is gone. */
	public static BlockPos findDoor(ServerLevel level, Plot lot) {
		int x = lot.minX() + lot.size() / 2;
		int z = lot.minZ() + lot.size() - 1;
		level.getChunk(x >> 4, z >> 4);

		for (int y = level.getMinY(); y <= level.getMaxY(); y++) {
			BlockPos pos = new BlockPos(x, y, z);

			if (level.getBlockState(pos).is(net.minecraft.tags.BlockTags.DOORS)) {
				return pos;
			}
		}

		return null;
	}

	/** Where the name sign hangs: on the front wall, beside the door. */
	public static BlockPos signPos(BlockPos door) {
		return door.above().east().south();
	}

	/** Hangs (or rewrites) the owner's name sign by the front door. Leaves anything else there alone. */
	public static boolean nameSign(ServerLevel level, BlockPos door, String ownerName) {
		BlockPos pos = signPos(door);
		BlockState existing = level.getBlockState(pos);

		if (!existing.isAir() && !existing.is(net.minecraft.tags.BlockTags.WALL_SIGNS)) {
			return false;
		}

		// Pale birch, so the sign stands out against the oak wall.
		level.setBlock(pos, Blocks.BIRCH_WALL_SIGN.defaultBlockState()
				.setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.SOUTH), 3);

		if (level.getBlockEntity(pos) instanceof net.minecraft.world.level.block.entity.SignBlockEntity sign) {
			java.util.List<net.minecraft.network.chat.Component> lines = java.util.List.of(
					net.minecraft.network.chat.Component.empty(),
					net.minecraft.network.chat.Component.literal(ownerName + "'s"),
					net.minecraft.network.chat.Component.literal("House"),
					net.minecraft.network.chat.Component.empty());
			sign.setText(new net.minecraft.world.level.block.entity.SignText(lines, lines, DyeColor.BLACK, false),
					net.minecraft.world.level.block.entity.SignTextSlot.FRONT);
			sign.setChanged();
			return true;
		}

		return false;
	}

	/** Builds the house and returns the floor height it was built at. */
	public static int build(ServerLevel level, Plot lot) {
		int floorY = floorY(level, lot);
		int last = lot.size() - 1;
		BlockState planks = Blocks.OAK_PLANKS.defaultBlockState();
		BlockState log = Blocks.OAK_LOG.defaultBlockState();
		BlockState air = Blocks.AIR.defaultBlockState();

		for (int dx = 0; dx <= last; dx++) {
			for (int dz = 0; dz <= last; dz++) {
				int x = lot.minX() + dx;
				int z = lot.minZ() + dz;
				boolean edge = dx == 0 || dz == 0 || dx == last || dz == last;
				boolean corner = (dx == 0 || dx == last) && (dz == 0 || dz == last);

				level.setBlock(new BlockPos(x, floorY, z), planks, QUIET);

				// If the lot is over water or a dip, fill underneath so the house stands on solid ground.
				for (int dy = 1; dy <= 40; dy++) {
					BlockPos below = new BlockPos(x, floorY - dy, z);
					BlockState state = level.getBlockState(below);

					if (!state.canBeReplaced() && state.getFluidState().isEmpty()) {
						break;
					}

					level.setBlock(below, Blocks.DIRT.defaultBlockState(), QUIET);
				}

				for (int dy = 1; dy <= 3; dy++) {
					level.setBlock(new BlockPos(x, floorY + dy, z), corner ? log : edge ? planks : air, QUIET);
				}

				level.setBlock(new BlockPos(x, floorY + 4, z), planks, QUIET);

				// Clear anything (trees, hills) that would poke through the roof.
				for (int dy = 5; dy <= 8; dy++) {
					level.setBlock(new BlockPos(x, floorY + dy, z), air, QUIET);
				}
			}
		}

		// Windows in the east and west walls.
		int middle = lot.size() / 2;
		level.setBlock(new BlockPos(lot.minX(), floorY + 2, lot.minZ() + middle), Blocks.GLASS.defaultBlockState(), QUIET);
		level.setBlock(new BlockPos(lot.minX() + last, floorY + 2, lot.minZ() + middle), Blocks.GLASS.defaultBlockState(), QUIET);

		// Front door on the south side, with a clear doorstep.
		BlockPos door = doorPos(lot, floorY);
		BlockState doorState = Blocks.OAK_DOOR.defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.NORTH);
		level.setBlock(door, doorState.setValue(BlockStateProperties.DOUBLE_BLOCK_HALF, DoubleBlockHalf.LOWER), QUIET);
		level.setBlock(door.above(), doorState.setValue(BlockStateProperties.DOUBLE_BLOCK_HALF, DoubleBlockHalf.UPPER), QUIET);
		BlockPos step = door.south();
		level.setBlock(step.below(), planks, QUIET);
		level.setBlock(step, air, QUIET);
		level.setBlock(step.above(), air, QUIET);

		// Bed along the west wall, head to the north.
		BlockPos bedHead = bedPos(lot, floorY);
		BlockState bed = Blocks.BED.pick(DyeColor.RED).defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.NORTH);
		level.setBlock(bedHead, bed.setValue(BlockStateProperties.BED_PART, BedPart.HEAD), QUIET);
		level.setBlock(bedHead.south(), bed.setValue(BlockStateProperties.BED_PART, BedPart.FOOT), QUIET);

		// A chest and a light.
		level.setBlock(new BlockPos(lot.minX() + last - 1, floorY + 1, lot.minZ() + 1),
				Blocks.CHEST.defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.SOUTH), QUIET);
		level.setBlock(new BlockPos(lot.minX() + last - 1, floorY + 1, lot.minZ() + last - 1), Blocks.TORCH.defaultBlockState(), QUIET);

		return floorY;
	}
}
