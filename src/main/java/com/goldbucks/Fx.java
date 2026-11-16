package com.goldbucks;

import java.util.List;
import java.util.Random;

import it.unimi.dsi.fastutil.ints.IntList;

import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.projectile.FireworkRocketEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.FireworkExplosion;
import net.minecraft.world.item.component.Fireworks;

/** Particles, sounds and fireworks, all vanilla. */
public final class Fx {
	private static final Random RANDOM = new Random();
	private static final int[] COLOURS = {0xFFD700, 0xFF5555, 0x55FF55, 0x5555FF, 0xFF55FF, 0x55FFFF, 0xFFFFFF};

	private Fx() {
	}

	public static void sparkle(ServerLevel level, BlockPos pos) {
		level.sendParticles(ParticleTypes.HAPPY_VILLAGER, pos.getX() + 0.5, pos.getY() + 1, pos.getZ() + 0.5, 12, 0.5, 0.5, 0.5, 0);
	}

	/** Gold sparkles around a player who was just paid. */
	public static void payday(ServerPlayer player) {
		ServerLevel level = player.level();
		level.sendParticles(ParticleTypes.WAX_ON, player.getX(), player.getY() + 1, player.getZ(), 30, 0.6, 0.8, 0.6, 0.5);
		level.sendParticles(ParticleTypes.HAPPY_VILLAGER, player.getX(), player.getY() + 1, player.getZ(), 15, 0.6, 0.8, 0.6, 0);
		player.level().playSound(null, player.blockPosition(), SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 0.8f, 1.2f);
	}

	/** A burst of firework sparks for a level-up or achievement. */
	public static void celebrate(ServerPlayer player) {
		player.level().sendParticles(ParticleTypes.TOTEM_OF_UNDYING, player.getX(), player.getY() + 1, player.getZ(), 40, 0.5, 1, 0.5, 0.3);
		player.level().playSound(null, player.blockPosition(), SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 1f, 1f);
	}

	public static void bell(ServerLevel level, BlockPos pos) {
		level.playSound(null, pos, SoundEvents.BELL_BLOCK, SoundSource.BLOCKS, 2f, 1f);
	}

	/** Launches a few fireworks from just above this spot. */
	public static void fireworksShow(ServerLevel level, BlockPos pos) {
		for (int i = 0; i < 6; i++) {
			int colour = COLOURS[RANDOM.nextInt(COLOURS.length)];
			FireworkExplosion.Shape shape = FireworkExplosion.Shape.values()[RANDOM.nextInt(FireworkExplosion.Shape.values().length)];
			ItemStack rocket = new ItemStack(Items.FIREWORK_ROCKET);
			rocket.set(DataComponents.FIREWORKS, new Fireworks(1 + RANDOM.nextInt(2),
					List.of(new FireworkExplosion(shape, IntList.of(colour), IntList.of(0xFFFFFF), RANDOM.nextBoolean(), true))));
			FireworkRocketEntity entity = new FireworkRocketEntity(level, pos.getX() + 0.5 + RANDOM.nextGaussian() * 2,
					pos.getY() + 1, pos.getZ() + 0.5 + RANDOM.nextGaussian() * 2, rocket);
			level.addFreshEntity(entity);
		}
	}
}
