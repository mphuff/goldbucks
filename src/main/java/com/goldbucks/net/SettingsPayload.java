package com.goldbucks.net;

import java.util.List;

import io.netty.buffer.ByteBuf;

import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

import com.goldbucks.GoldbucksMod;

/** Server to client: open the host settings screen with every setting's current value. */
public record SettingsPayload(List<Entry> entries, List<String> problems) implements CustomPacketPayload {
	/** One setting. {@code toggle} settings are true/false switches; the rest are whole numbers. */
	public record Entry(String name, String tab, String description, String value, boolean toggle) {
		public static final StreamCodec<ByteBuf, Entry> CODEC = StreamCodec.composite(
				ByteBufCodecs.STRING_UTF8, Entry::name,
				ByteBufCodecs.STRING_UTF8, Entry::tab,
				ByteBufCodecs.STRING_UTF8, Entry::description,
				ByteBufCodecs.STRING_UTF8, Entry::value,
				ByteBufCodecs.BOOL, Entry::toggle,
				Entry::new);
	}

	public static final CustomPacketPayload.Type<SettingsPayload> TYPE = new CustomPacketPayload.Type<>(GoldbucksMod.id("settings"));

	public static final StreamCodec<ByteBuf, SettingsPayload> CODEC = StreamCodec.composite(
			Entry.CODEC.apply(ByteBufCodecs.list()), SettingsPayload::entries,
			ByteBufCodecs.STRING_UTF8.apply(ByteBufCodecs.list()), SettingsPayload::problems,
			SettingsPayload::new);

	@Override
	public Type<SettingsPayload> type() {
		return TYPE;
	}
}
