package com.goldbucks.net;

import io.netty.buffer.ByteBuf;

import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

import com.goldbucks.GoldbucksMod;

/** Client to server: the player pressed a menu button. */
public record MenuActionPayload(String command) implements CustomPacketPayload {
	public static final CustomPacketPayload.Type<MenuActionPayload> TYPE = new CustomPacketPayload.Type<>(GoldbucksMod.id("menu_action"));

	public static final StreamCodec<ByteBuf, MenuActionPayload> CODEC = StreamCodec.composite(
			ByteBufCodecs.STRING_UTF8, MenuActionPayload::command,
			MenuActionPayload::new);

	@Override
	public Type<MenuActionPayload> type() {
		return TYPE;
	}
}
