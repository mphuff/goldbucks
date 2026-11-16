package com.goldbucks;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.SlabType;

import com.goldbucks.economy.House;
import com.goldbucks.economy.Plot;
import com.goldbucks.economy.StoreKind;

/**
 * Builds the Town Store's house upgrades out of ordinary blocks, a few at a time so players can watch.
 * Before building, it checks the space holds only natural ground, so nothing a player built is ever removed.
 */
public final class UpgradeBuilder {
	/** Blocks placed per tick while an upgrade goes up. */
	private static final int PER_TICK = 6;

	private record Placement(BlockPos pos, BlockState state) {
	}

	private static final Deque<Placement> queue = new ArrayDeque<>();
	/** When true, upgrades appear at once. The automated tests use this. */
	private static boolean instant;

	private UpgradeBuilder() {
	}

	public static void setInstant(boolean value) {
		instant = value;
	}

	public static void register() {
		ServerLifecycleEvents.SERVER_STOPPING.register(server -> finishNow(server.overworld()));
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			if (queue.isEmpty()) {
				return;
			}

			ServerLevel level = server.overworld();

			for (int i = 0; i < PER_TICK && !queue.isEmpty(); i++) {
				Placement next = queue.poll();
				level.setBlock(next.pos(), next.state(), 3);

				if (i == 0 && server.getTickCount() % 4 == 0) {
					level.playSound(null, next.pos(), SoundEvents.WOOD_PLACE, SoundSource.BLOCKS, 0.6f, 1f);
					level.sendParticles(ParticleTypes.POOF, next.pos().getX() + 0.5, next.pos().getY() + 0.5, next.pos().getZ() + 0.5, 2, 0.2, 0.2, 0.2, 0.01);
				}
			}
		});
	}

	private static void finishNow(ServerLevel level) {
		while (!queue.isEmpty()) {
			Placement next = queue.poll();
			level.setBlock(next.pos(), next.state(), 3);
		}
	}

	/** The floor height of a house, found from its front door. Returns Integer.MIN_VALUE if the door is gone. */
	public static int floorY(ServerLevel level, House house) {
		BlockPos door = HouseBuilder.findDoor(level, house.lot());
		return door == null ? Integer.MIN_VALUE : door.getY() - 1;
	}

	/** Whether a block is natural ground (or air, water, plants), which an upgrade may build over. */
	public static boolean natural(BlockState state) {
		return state.isAir() || state.canBeReplaced() || state.is(BlockTags.LEAVES) || state.is(BlockTags.LOGS)
				|| state.is(BlockTags.DIRT) || state.is(BlockTags.BASE_STONE_OVERWORLD) || state.is(BlockTags.SAND)
				|| state.is(Blocks.GRAVEL) || state.is(Blocks.CLAY) || state.is(BlockTags.SNOW) || state.is(BlockTags.FLOWERS)
				|| state.is(BlockTags.ORES)
				|| state.is(Blocks.WATER) || state.is(Blocks.LAVA) || state.is(Blocks.GRASS_BLOCK)
				|| state.is(Blocks.SANDSTONE) || state.is(Blocks.RED_SANDSTONE) || state.is(BlockTags.TERRACOTTA)
				|| state.is(Blocks.ICE) || state.is(Blocks.PACKED_ICE) || state.is(Blocks.SNOW_BLOCK) || state.is(Blocks.POWDER_SNOW)
				|| state.is(Blocks.CALCITE) || state.is(Blocks.DRIPSTONE_BLOCK) || state.is(Blocks.MOSS_BLOCK) || state.is(Blocks.MUD)
				|| state.is(Blocks.OBSIDIAN);
	}

	/**
	 * Checks an upgrade can be built. Returns null if it can, or a description of what is in the way.
	 */
	public static String blocked(ServerLevel level, House house, StoreKind upgrade) {
		int floorY = floorY(level, house);

		if (floorY == Integer.MIN_VALUE) {
			return "Your house's front door is missing, so the builders cannot find it.";
		}

		for (Placement placement : plan(house, upgrade, floorY)) {
			BlockState there = level.getBlockState(placement.pos());

			if (there.equals(placement.state()) || natural(there) || isHouseBlock(house, floorY, placement.pos(), upgrade)) {
				continue;
			}

			BlockPos pos = placement.pos();
			return "Something you built is in the way at " + pos.getX() + ", " + pos.getY() + ", " + pos.getZ()
					+ " (" + there.getBlock().getName().getString() + "). Move it and try again.";
		}

		return null;
	}

	/** Parts of the starting house that an upgrade is meant to change, such as the roof that becomes a floor. */
	private static boolean isHouseBlock(House house, int floorY, BlockPos pos, StoreKind upgrade) {
		Plot lot = house.lot();
		boolean inHouse = lot.contains(pos.getX(), pos.getZ());

		return switch (upgrade) {
			case SECOND_FLOOR -> inHouse && pos.getY() <= floorY + 4;
			case BASEMENT -> inHouse && pos.getY() >= floorY;
			case TOWER -> inHouse && pos.getY() <= floorY + 8;
			case POOL -> pos.getZ() < lot.minZ() && pos.getY() <= floorY + 1;
			default -> false;
		};
	}

	/** Starts building an upgrade. It must have been checked with {@link #blocked} first. */
	public static void build(ServerLevel level, House house, StoreKind upgrade) {
		int floorY = floorY(level, house);
		List<Placement> plan = plan(house, upgrade, floorY);

		if (instant) {
			plan.forEach(p -> level.setBlock(p.pos(), p.state(), 3));
		} else {
			queue.addAll(plan);
		}
	}

	private static BlockState air() {
		return Blocks.AIR.defaultBlockState();
	}

	private static List<Placement> plan(House house, StoreKind upgrade, int y) {
		Plot lot = house.lot();
		int x0 = lot.minX();
		int z0 = lot.minZ();
		int last = lot.size() - 1;
		List<Placement> plan = new ArrayList<>();
		BlockState planks = Blocks.OAK_PLANKS.defaultBlockState();
		BlockState log = Blocks.OAK_LOG.defaultBlockState();
		BlockState fence = Blocks.OAK_FENCE.defaultBlockState();
		BlockState slab = Blocks.OAK_SLAB.defaultBlockState();

		switch (upgrade) {
			case PORCH -> {
				int front = z0 + lot.size();

				for (int x = x0; x <= x0 + last; x++) {
					for (int z = front + 1; z <= front + 2; z++) {
						plan.add(new Placement(new BlockPos(x, y, z), planks));

						for (int dy = 1; dy <= 3; dy++) {
							plan.add(new Placement(new BlockPos(x, y + dy, z), air()));
						}
					}

					for (int z = front; z <= front + 2; z++) {
						plan.add(new Placement(new BlockPos(x, y + 4, z), slab));
					}
				}

				for (int x : new int[] {x0, x0 + last}) {
					for (int dy = 1; dy <= 3; dy++) {
						plan.add(new Placement(new BlockPos(x, y + dy, front + 2), fence));
					}
				}

				BlockState bench = Blocks.OAK_STAIRS.defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.WEST);
				plan.add(new Placement(new BlockPos(x0 + 1, y + 1, front + 1), bench));
				plan.add(new Placement(new BlockPos(x0 + 5, y + 1, front + 1), bench.setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.EAST)));
				plan.add(new Placement(new BlockPos(x0 + 1, y + 1, front + 2), Blocks.POTTED_POPPY.defaultBlockState()));
				plan.add(new Placement(new BlockPos(x0 + 5, y + 1, front + 2), Blocks.POTTED_DANDELION.defaultBlockState()));
				plan.add(new Placement(new BlockPos(x0 + last, y + 3, front + 1), Blocks.LANTERN.defaultBlockState().setValue(BlockStateProperties.HANGING, true)));
			}
			case GARDEN -> {
				int zMin = z0 - 7;
				int zMax = z0 - 1;

				for (int x = x0; x <= x0 + last; x++) {
					for (int z = zMin; z <= zMax; z++) {
						boolean edge = x == x0 || x == x0 + last || z == zMin || z == zMax;
						boolean water = x == x0 + 3 && z == z0 - 4;
						boolean crops = !edge && (z == z0 - 5 || z == z0 - 3) && !water;
						BlockState ground = water ? Blocks.WATER.defaultBlockState()
								: crops ? Blocks.FARMLAND.defaultBlockState().setValue(BlockStateProperties.MOISTURE, 7)
								: Blocks.GRASS_BLOCK.defaultBlockState();
						plan.add(new Placement(new BlockPos(x, y, z), ground));
						BlockState above = air();

						if (edge) {
							above = x == x0 + last && z == z0 - 4
									? Blocks.OAK_FENCE_GATE.defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.EAST)
									: fence;
						} else if (crops) {
							above = (x % 2 == 0 ? Blocks.CARROTS : Blocks.WHEAT).defaultBlockState();
						} else if (z == z0 - 6 || z == z0 - 2) {
							Object[] flowers = {Blocks.POPPY, Blocks.DANDELION, Blocks.CORNFLOWER, Blocks.ALLIUM, Blocks.OXEYE_DAISY};
							above = ((net.minecraft.world.level.block.Block) flowers[Math.floorMod(x + z, flowers.length)]).defaultBlockState();
						}

						plan.add(new Placement(new BlockPos(x, y + 1, z), above));

						for (int dy = 2; dy <= 4; dy++) {
							plan.add(new Placement(new BlockPos(x, y + dy, z), air()));
						}
					}
				}
			}
			case POOL -> {
				for (int x = x0 + 2; x <= x0 + 4; x++) {
					for (int z = z0 - 6; z <= z0 - 5; z++) {
						plan.add(new Placement(new BlockPos(x, y - 1, z), Blocks.SMOOTH_STONE.defaultBlockState()));
						plan.add(new Placement(new BlockPos(x, y, z), Blocks.WATER.defaultBlockState()));
						plan.add(new Placement(new BlockPos(x, y + 1, z), air()));
					}
				}
			}
			case BASEMENT -> {
				BlockState wall = Blocks.STONE_BRICKS.defaultBlockState();

				for (int x = x0; x <= x0 + last; x++) {
					for (int z = z0; z <= z0 + last; z++) {
						boolean edge = x == x0 || z == z0 || x == x0 + last || z == z0 + last;
						plan.add(new Placement(new BlockPos(x, y - 4, z), wall));

						for (int dy = -3; dy <= -1; dy++) {
							plan.add(new Placement(new BlockPos(x, y + dy, z), edge ? wall : air()));
						}
					}
				}

				BlockState ladder = Blocks.LADDER.defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.WEST);

				for (int dy = -3; dy <= 0; dy++) {
					plan.add(new Placement(new BlockPos(x0 + last - 1, y + dy, z0 + 3), ladder));
				}

				plan.add(new Placement(new BlockPos(x0 + 3, y - 3, z0 + 3), Blocks.CRAFTING_TABLE.defaultBlockState()));
				plan.add(new Placement(new BlockPos(x0 + 1, y - 3, z0 + 1), Blocks.BARREL.defaultBlockState()));
				plan.add(new Placement(new BlockPos(x0 + 3, y - 1, z0 + 1), Blocks.LANTERN.defaultBlockState().setValue(BlockStateProperties.HANGING, true)));
			}
			case SECOND_FLOOR -> {
				for (int x = x0; x <= x0 + last; x++) {
					for (int z = z0; z <= z0 + last; z++) {
						boolean edge = x == x0 || z == z0 || x == x0 + last || z == z0 + last;
						boolean corner = (x == x0 || x == x0 + last) && (z == z0 || z == z0 + last);

						for (int dy = 5; dy <= 7; dy++) {
							boolean window = dy == 6 && edge && !corner && (x == x0 + 3 || z == z0 + 3);
							plan.add(new Placement(new BlockPos(x, y + dy, z), window ? Blocks.GLASS_PANE.defaultBlockState()
									: corner ? log : edge ? planks : air()));
						}

						plan.add(new Placement(new BlockPos(x, y + 8, z), planks));
					}
				}

				BlockState ladder = Blocks.LADDER.defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.WEST);

				for (int dy = 1; dy <= 4; dy++) {
					plan.add(new Placement(new BlockPos(x0 + last - 1, y + dy, z0 + 3), ladder));
				}

				plan.add(new Placement(new BlockPos(x0 + 1, y + 5, z0 + 1), Blocks.BOOKSHELF.defaultBlockState()));
				plan.add(new Placement(new BlockPos(x0 + 2, y + 5, z0 + 1), Blocks.BOOKSHELF.defaultBlockState()));
				plan.add(new Placement(new BlockPos(x0 + 3, y + 7, z0 + 3), Blocks.LANTERN.defaultBlockState().setValue(BlockStateProperties.HANGING, true)));
			}
			case TOWER -> {
				int tx = x0 + last - 2;

				for (int x = tx; x <= tx + 2; x++) {
					for (int z = z0; z <= z0 + 2; z++) {
						boolean middle = x == tx + 1 && z == z0 + 1;

						for (int dy = 9; dy <= 14; dy++) {
							boolean window = dy == 12 && !middle && (x == tx + 1 || z == z0 + 1);
							plan.add(new Placement(new BlockPos(x, y + dy, z), middle ? air()
									: window ? Blocks.GLASS_PANE.defaultBlockState() : Blocks.STONE_BRICKS.defaultBlockState()));
						}

						plan.add(new Placement(new BlockPos(x, y + 15, z), middle ? air() : Blocks.STONE_BRICK_SLAB.defaultBlockState()
								.setValue(BlockStateProperties.SLAB_TYPE, SlabType.BOTTOM)));

						if (!middle) {
							plan.add(new Placement(new BlockPos(x, y + 16, z), Blocks.STONE_BRICK_WALL.defaultBlockState()));
						}
					}
				}

				BlockState ladder = Blocks.LADDER.defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.SOUTH);
				plan.add(new Placement(new BlockPos(tx + 1, y + 8, z0 + 1), air()));

				for (int dy = 5; dy <= 14; dy++) {
					plan.add(new Placement(new BlockPos(tx + 1, y + dy, z0 + 1), ladder));
				}
			}
			case STABLE -> {
				int xMin = x0 - 5;
				int xMax = x0 - 1;

				for (int x = xMin; x <= xMax; x++) {
					for (int z = z0; z <= z0 + last; z++) {
						boolean edge = x == xMin || z == z0 || z == z0 + last;
						plan.add(new Placement(new BlockPos(x, y, z), Blocks.SPRUCE_PLANKS.defaultBlockState()));
						BlockState wall = edge ? fence : air();

						if (x == xMin && z == z0 + 3) {
							wall = Blocks.OAK_FENCE_GATE.defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.WEST);
						}

						plan.add(new Placement(new BlockPos(x, y + 1, z), wall));
						boolean post = (x == xMin) && (z == z0 || z == z0 + last);
						plan.add(new Placement(new BlockPos(x, y + 2, z), post ? fence : air()));
						plan.add(new Placement(new BlockPos(x, y + 3, z), post ? fence : air()));
						plan.add(new Placement(new BlockPos(x, y + 4, z), Blocks.SPRUCE_SLAB.defaultBlockState()));
					}
				}

				plan.add(new Placement(new BlockPos(x0 - 2, y + 1, z0 + 1), Blocks.HAY_BLOCK.defaultBlockState()));
				plan.add(new Placement(new BlockPos(x0 - 2, y + 1, z0 + 5), Blocks.WATER_CAULDRON.defaultBlockState()
						.setValue(BlockStateProperties.LEVEL_CAULDRON, 3)));
				plan.add(new Placement(new BlockPos(x0 - 3, y + 3, z0 + 3), Blocks.LANTERN.defaultBlockState().setValue(BlockStateProperties.HANGING, true)));
			}
			default -> {
			}
		}

		return plan;
	}
}
