package com.goldbucks;

import com.mojang.serialization.Codec;

import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

import com.goldbucks.economy.Economy;

/** Saves the economy with the world. */
public class EconomyState extends SavedData {
	private static final Codec<EconomyState> CODEC = Codec.STRING.xmap(
			json -> new EconomyState(Economy.fromJson(json)),
			state -> state.economy.toJson());

	private static final SavedDataType<EconomyState> TYPE = new SavedDataType<>(
			GoldbucksMod.id("economy"),
			EconomyState::new,
			CODEC,
			null);

	private final Economy economy;

	public EconomyState() {
		this(new Economy());
	}

	private EconomyState(Economy economy) {
		this.economy = economy;
	}

	public Economy economy() {
		return economy;
	}

	public static EconomyState get(MinecraftServer server) {
		return server.getDataStorage().computeIfAbsent(TYPE);
	}
}
