package com.goldbucks.net;

import io.netty.buffer.ByteBuf;

import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

import com.goldbucks.GoldbucksMod;

/** Client to server: the player picked a job from the menu. */
public record ChooseJobPayload(int job) implements CustomPacketPayload {
	public static final CustomPacketPayload.Type<ChooseJobPayload> TYPE =
			new CustomPacketPayload.Type<>(GoldbucksMod.id("choose_job"));

	public static final StreamCodec<ByteBuf, ChooseJobPayload> CODEC = StreamCodec.composite(
			ByteBufCodecs.VAR_INT, ChooseJobPayload::job,
			ChooseJobPayload::new);

	@Override
	public Type<ChooseJobPayload> type() {
		return TYPE;
	}
}
