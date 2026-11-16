package com.goldbucks.net;

import io.netty.buffer.ByteBuf;

import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

import com.goldbucks.GoldbucksMod;

/**
 * Server to client: a sale (or other good news) to show above the hotbar with a sound.
 * The client decides whether to show it, from the player's own settings.
 */
public record NoticePayload(String text, int sound) implements CustomPacketPayload {
	public static final int SALE = 0;
	public static final int TOWNSFOLK_SALE = 1;

	public static final CustomPacketPayload.Type<NoticePayload> TYPE = new CustomPacketPayload.Type<>(GoldbucksMod.id("notice"));

	public static final StreamCodec<ByteBuf, NoticePayload> CODEC = StreamCodec.composite(
			ByteBufCodecs.STRING_UTF8, NoticePayload::text,
			ByteBufCodecs.VAR_INT, NoticePayload::sound,
			NoticePayload::new);

	@Override
	public Type<NoticePayload> type() {
		return TYPE;
	}
}
