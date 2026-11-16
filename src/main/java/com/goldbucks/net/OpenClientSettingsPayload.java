package com.goldbucks.net;

import io.netty.buffer.ByteBuf;

import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

import com.goldbucks.GoldbucksMod;

/** Server to client: open this player's own settings screen (sent by /goldbucks settings). */
public record OpenClientSettingsPayload() implements CustomPacketPayload {
	public static final CustomPacketPayload.Type<OpenClientSettingsPayload> TYPE =
			new CustomPacketPayload.Type<>(GoldbucksMod.id("open_client_settings"));

	public static final StreamCodec<ByteBuf, OpenClientSettingsPayload> CODEC = StreamCodec.unit(new OpenClientSettingsPayload());

	@Override
	public Type<OpenClientSettingsPayload> type() {
		return TYPE;
	}
}
