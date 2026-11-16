package com.goldbucks.net;

import java.util.List;

import io.netty.buffer.ByteBuf;

import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

import com.goldbucks.GoldbucksMod;

/**
 * Server to client: open a menu screen. Each row is a line of text with an optional button;
 * pressing the button sends its command back to the server, which runs it as the player.
 */
public record MenuPayload(String title, String subtitle, List<Row> rows) implements CustomPacketPayload {
	/** A line of the menu. An empty button label means the row is just text. */
	public record Row(String text, String tooltip, String button, String command, boolean enabled) {
		public static final StreamCodec<ByteBuf, Row> CODEC = StreamCodec.composite(
				ByteBufCodecs.STRING_UTF8, Row::text,
				ByteBufCodecs.STRING_UTF8, Row::tooltip,
				ByteBufCodecs.STRING_UTF8, Row::button,
				ByteBufCodecs.STRING_UTF8, Row::command,
				ByteBufCodecs.BOOL, Row::enabled,
				Row::new);
	}

	public static final CustomPacketPayload.Type<MenuPayload> TYPE = new CustomPacketPayload.Type<>(GoldbucksMod.id("menu"));

	public static final StreamCodec<ByteBuf, MenuPayload> CODEC = StreamCodec.composite(
			ByteBufCodecs.STRING_UTF8, MenuPayload::title,
			ByteBufCodecs.STRING_UTF8, MenuPayload::subtitle,
			Row.CODEC.apply(ByteBufCodecs.list()), MenuPayload::rows,
			MenuPayload::new);

	@Override
	public Type<MenuPayload> type() {
		return TYPE;
	}
}
